import datetime

import jwt
from flask import Blueprint, jsonify, request, make_response
import logging
import uuid

from app.extensions import db
from app.models import (
    Coupon,
    DriverAverageRating,
    DriverRating,
    Order,
    OrderStatus,
    Payment,
    User,
    UserCoupon,
    Vehicle,
)
from app.utils.auth import check_token, generate_token

# 支付宝沙箱集成：配置/客户端工厂在独立模块
from app.utils.alipay_config import (
    AlipayConfigError,
    get_alipay_client,
    get_gateway,
    get_notify_url,
    get_return_url,
)

payments_bp = Blueprint("payments", __name__)

# 用于在 /api/payment/notify 中验签
_log = logging.getLogger("alipay")

# ------ 内部工具函数 ------

# 生成本系统唯一支付单号（格式：WW{order_id}-{时间戳}-{8位UUID}）
def _make_out_trade_no(order_id: int) -> str:
    ts = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    short = uuid.uuid4().hex[:8]
    return f"WW{order_id}-{ts}-{short}"


# 支付宝 trade_status → 本系统 Payment.status
def _alipay_status_to_local(trade_status: str) -> str:
    mapping = {
        "TRADE_SUCCESS": "PAID",
        "TRADE_FINISHED": "PAID",
        "WAIT_BUYER_PAY": "PENDING",
        "TRADE_CLOSED": "CLOSED",
    }
    return mapping.get(trade_status or "", "CREATED")


# 统一序列化 Payment，给前端返回
def _payment_to_dict(payment: "Payment") -> dict:
    return {
        "payment_id": payment.payment_id,
        "order_id": payment.order_id,
        "passenger_username": payment.passenger_username,
        "driver_username": payment.driver_username,
        "amount": float(payment.amount) if payment.amount is not None else None,
        "status": payment.status,
        "has_paid": payment.status == "PAID",
        "out_trade_no": payment.out_trade_no,
        "alipay_trade_no": payment.alipay_trade_no,
        "pay_url": payment.pay_url,
        "paid_at": payment.paid_at.isoformat() if payment.paid_at else None,
    }


# ------ 共用：JWT 校验，复用既有 check_token ------

# 通用登录校验：成功返回 (None, current_user)；失败返回 (response, None)
# 所有 /api/payment/* 接口除 notify/return 之外都走这个
def _require_login():
    token = request.headers.get("Authorization")
    if not token:
        return (jsonify({"code": 401, "message": "Token缺失"}), 401), None
    check_result = check_token(token)
    if check_result:
        return check_result, None
    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    return None, payload["username"]


# 创建支付宝沙箱支付订单，成功返回 pay_url 等字段，由前端打开网页付款
@payments_bp.route("/api/payment/pay", methods=["POST"])
def pay_order():
    err, current_user = _require_login()
    if err:
        return err

    data = request.json or {}
    order_id = data.get("order_id")
    amount = data.get("amount")

    if order_id is None or amount is None:
        return jsonify({"code": 400, "message": "order_id 或 amount 缺失"}), 400
    
    # 金额校验
    try:
        amount_value = float(amount)
    except (TypeError, ValueError):
        return jsonify({"code": 400, "message": "支付金额必须是数字"}), 400
    if amount_value <= 0:
        return jsonify({"code": 400, "message": "支付金额必须大于0"}), 400

    # 订单必须存在
    order = Order.query.get_or_404(order_id)

    # 订单必须已完成
    order_status = OrderStatus.query.filter_by(order_id=order_id).first()
    if not order_status:
        return jsonify({"code": 404, "message": "订单状态不存在"}), 404
    if order_status.status != 2:
        return jsonify({"code": 400, "message": "订单尚未完成，无法支付"}), 400

    if not order.driver:
        return jsonify({"code": 400, "message": "订单暂无司机，无需支付"}), 400
    if current_user not in [order.user1, order.user2, order.user3, order.user4]:
        return jsonify({"code": 403, "message": "您不是该订单的乘客"}), 403
    if current_user == order.driver:
        return jsonify({"code": 403, "message": "司机不能为自己支付"}), 403

    payment = Payment.query.filter_by(
        order_id=order_id, passenger_username=current_user
    ).first()
    if payment and payment.status == "PAID":
        return jsonify({"code": 409, "message": "您已完成支付，请勿重复支付"}), 409

    # 加载支付宝客户端
    try:
        client = get_alipay_client()
        gateway = get_gateway()
    except AlipayConfigError as exc:
        return jsonify({"code": 500, "message": f"支付宝配置错误：{exc}"}), 500
    except Exception as exc:  # pragma: no cover
        _log.exception("加载支付宝客户端失败")
        return jsonify({"code": 500, "message": "支付通道初始化失败"}), 500

    out_trade_no = _make_out_trade_no(order_id)
    subject = f"星途拼车订单 #{order_id}"

    host_root = request.host_url.rstrip("/")
    return_url = get_return_url(default=f"{host_root}/api/payment/return")
    notify_url = get_notify_url(default=f"{host_root}/api/payment/notify")

    # 调用支付宝沙箱网页支付（page_pay）
    try:
        order_string = client.api_alipay_trade_page_pay(
            out_trade_no=out_trade_no,
            total_amount=format(amount_value, ".2f"),   # 金额必须是字符串、两位小数
            subject=subject,
            return_url=return_url,
            notify_url=notify_url,
        )
    except Exception as exc:
        _log.exception("调用支付宝 page_pay 失败")
        return jsonify({"code": 500, "message": f"创建支付订单失败：{exc}"}), 500

    pay_url = f"{gateway}?{order_string}"

    # 复用旧行或新建行
    if payment is None:
        payment = Payment(
            order_id=order_id,
            passenger_username=current_user,
            driver_username=order.driver,
            amount=amount_value,
            status="CREATED",
            out_trade_no=out_trade_no,
            pay_url=pay_url,
        )
        db.session.add(payment)
    else:
        payment.amount = amount_value
        payment.driver_username = order.driver
        payment.status = "CREATED"
        payment.out_trade_no = out_trade_no
        payment.pay_url = pay_url
        payment.alipay_trade_no = None
        payment.paid_at = None

    db.session.commit()

    return jsonify({
        "code": 200,
        "message": "支付订单创建成功",
        "data": _payment_to_dict(payment),
    })


# 查当前用户对订单的本地支付状态
@payments_bp.route("/api/payment/status", methods=["POST"])
def payment_status():
    err, current_user = _require_login()
    if err:
        return err

    data = request.json or {}
    order_id = data.get("order_id")
    if order_id is None:
        return jsonify({"code": 400, "message": "order_id 缺失"}), 400

    payment = Payment.query.filter_by(
        order_id=order_id, passenger_username=current_user
    ).first()
    if not payment:
        return jsonify({
            "code": 200,
            "message": "查询成功",
            "data": {
                "order_id": order_id,
                "has_paid": False,
                "status": "UNPAID",
                "amount": None,
                "out_trade_no": None,
                "alipay_trade_no": None,
                "pay_url": None,
                "paid_at": None,
            },
        })
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": _payment_to_dict(payment),
    })


# 主动调用支付宝交易查询接口，同步刷新本地 Payment
# 参数：{ order_id?: int, out_trade_no?: str }，至少要给一个
# 当 order_id 给定时按当前用户定位 Payment 行
@payments_bp.route("/api/payment/query", methods=["POST", "GET"])
def payment_query():
    err, current_user = _require_login()
    if err:
        return err

    if request.method == "GET":
        data = request.args
    else:
        data = request.json or {}
    order_id = data.get("order_id")
    out_trade_no = data.get("out_trade_no")

    payment = None
    if out_trade_no:
        payment = Payment.query.filter_by(out_trade_no=out_trade_no).first()
        if payment and payment.passenger_username != current_user:
            return jsonify({"code": 403, "message": "无权查询该支付单"}), 403
    elif order_id is not None:
        payment = Payment.query.filter_by(
            order_id=order_id, passenger_username=current_user
        ).first()

    if not payment:
        return jsonify({"code": 404, "message": "支付单不存在，请先发起支付"}), 404

    # 已是终态就不再查支付宝
    if payment.status == "PAID":
        return jsonify({
            "code": 200, "message": "已支付", "data": _payment_to_dict(payment)
        })

    if not payment.out_trade_no:
        return jsonify({"code": 400, "message": "本地支付单缺失 out_trade_no"}), 400

    try:
        client = get_alipay_client()
        result = client.api_alipay_trade_query(out_trade_no=payment.out_trade_no)
    except AlipayConfigError as exc:
        return jsonify({"code": 500, "message": f"支付宝配置错误：{exc}"}), 500
    except Exception as exc:  # pragma: no cover
        _log.exception("调用支付宝交易查询失败")
        return jsonify({"code": 502, "message": f"查询支付宝失败：{exc}"}), 502

    code_str = str(result.get("code", ""))
    if code_str != "10000":
        # 沙箱查询失败：通常是单号不存在（用户还没付款生成支付宝侧交易）
        # 这种情况保持本地 status 不变，正常返回，供前端继续轮询
        return jsonify({
            "code": 200,
            "message": result.get("sub_msg") or result.get("msg") or "支付宝未返回交易",
            "data": _payment_to_dict(payment),
        })

    trade_status = result.get("trade_status", "")
    new_status = _alipay_status_to_local(trade_status)
    alipay_trade_no = result.get("trade_no")

    changed = False
    if new_status == "PAID" and payment.status != "PAID":
        payment.status = "PAID"
        payment.alipay_trade_no = alipay_trade_no
        payment.paid_at = datetime.datetime.now()
        changed = True
    elif new_status != payment.status and new_status in ("PENDING", "CLOSED"):
        payment.status = new_status
        if alipay_trade_no:
            payment.alipay_trade_no = alipay_trade_no
        changed = True

    if changed:
        db.session.commit()

    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": _payment_to_dict(payment),
    })


# 支付宝异步通知，验签通过且 trade_status ∈ {TRADE_SUCCESS, TRADE_FINISHED} 时更新本地 Payment
# 必须返回纯文本 success，否则支付宝会重试
@payments_bp.route("/api/payment/notify", methods=["POST"])
def payment_notify():
    raw = request.form.to_dict()
    if not raw:
        return "failure"

    signature = raw.pop("sign", None)
    raw.pop("sign_type", None)
    if not signature:
        return "failure"

    try:
        client = get_alipay_client()
        verified = client.verify(raw, signature)
    except Exception:  # pragma: no cover
        _log.exception("支付宝通知验签出错")
        return "failure"

    if not verified:
        return "failure"

    out_trade_no = raw.get("out_trade_no")
    trade_status = raw.get("trade_status", "")
    alipay_trade_no = raw.get("trade_no")
    if not out_trade_no:
        return "failure"

    payment = Payment.query.filter_by(out_trade_no=out_trade_no).first()
    if not payment:
        # 不存在的单号也返回 success，避免支付宝重复推送（log 中已记录）
        return "success"

    if trade_status in ("TRADE_SUCCESS", "TRADE_FINISHED") and payment.status != "PAID":
        payment.status = "PAID"
        payment.alipay_trade_no = alipay_trade_no
        payment.paid_at = datetime.datetime.now()
        db.session.commit()
    elif trade_status == "TRADE_CLOSED" and payment.status not in ("PAID", "CLOSED"):
        payment.status = "CLOSED"
        db.session.commit()

    return "success"


# 支付宝同步跳转返回页，仅做提示，不在此处更新支付状态
@payments_bp.route("/api/payment/return", methods=["GET"])
def payment_return():
    html = (
        "<!DOCTYPE html><html lang='zh-CN'><head><meta charset='utf-8'>"
        "<meta name='viewport' content='width=device-width, initial-scale=1'>"
        "<title>支付返回</title>"
        "<style>body{font-family:sans-serif;text-align:center;padding:48px;color:#333}"
        "h1{color:#0089FF}p{color:#666}</style></head><body>"
        "<h1>支付提交完成</h1>"
        "<p>请回到拼车 App，下拉刷新或点击“点此刷新”按钮以更新订单状态。</p>"
        "<p>本页面可以关闭。</p>"
        "</body></html>"
    )
    response = make_response(html, 200)
    response.headers["Content-Type"] = "text/html; charset=utf-8"
    return response