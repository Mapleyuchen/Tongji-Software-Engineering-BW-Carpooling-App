import datetime

import jwt
from flask import Blueprint, jsonify, request

from app.extensions import db
from app.models import (
    Coupon,
    DriverAverageRating,
    DriverRating,
    Order,
    OrderStatus,
    User,
    UserCoupon,
    Vehicle,
)
from app.utils.auth import check_token, generate_token


coupons_bp = Blueprint("coupons", __name__)


@coupons_bp.route("/api/coupon/create", methods=["POST"])
def create_coupon():
    data = request.json

    required_fields = ["coupon_name", "discount_type", "discount_value", "start_date", "end_date"]
    if not all(field in data for field in required_fields):
        return jsonify({"code": 400, "message": "优惠券信息不完整"}), 400

    # 验证折扣类型
    if data["discount_type"] not in ["percentage", "fixed"]:
        return jsonify({"code": 400, "message": "折扣类型必须是percentage或fixed"}), 400

    try:
        start_date = datetime.datetime.strptime(data["start_date"], "%Y-%m-%d").date()
        end_date = datetime.datetime.strptime(data["end_date"], "%Y-%m-%d").date()

        if start_date >= end_date:
            return jsonify({"code": 400, "message": "结束日期必须晚于开始日期"}), 400

        coupon = Coupon(coupon_name=data["coupon_name"], discount_type=data["discount_type"], discount_value=data["discount_value"], min_amount=data.get("min_amount", 0), start_date=start_date, end_date=end_date, usage_limit=data.get("usage_limit", 1), is_active=data.get("is_active", True))

        db.session.add(coupon)
        db.session.commit()

        return jsonify(
            {
                "code": 201,
                "message": "优惠券创建成功",
                "data": {
                    "coupon_id": coupon.coupon_id,
                    "coupon_name": coupon.coupon_name,
                    "discount_type": coupon.discount_type,
                    "discount_value": float(coupon.discount_value),
                    "min_amount": float(coupon.min_amount),
                    "start_date": coupon.start_date.isoformat(),
                    "end_date": coupon.end_date.isoformat(),
                    "usage_limit": coupon.usage_limit,
                    "is_active": coupon.is_active,
                },
            }
        )
    except ValueError:
        return jsonify({"code": 400, "message": "日期格式错误"}), 400


# 获取所有可用优惠券
@coupons_bp.route("/api/coupon/available", methods=["GET"])
def get_available_coupons():
    current_date = datetime.datetime.now().date()

    # 查询当前有效的优惠券
    coupons = Coupon.query.filter(Coupon.is_active == True, Coupon.start_date <= current_date, Coupon.end_date >= current_date).all()

    coupon_list = []
    for coupon in coupons:
        coupon_list.append(
            {
                "coupon_id": coupon.coupon_id,
                "coupon_name": coupon.coupon_name,
                "discount_type": coupon.discount_type,
                "discount_value": float(coupon.discount_value),
                "min_amount": float(coupon.min_amount),
                "start_date": coupon.start_date.isoformat(),
                "end_date": coupon.end_date.isoformat(),
                "usage_limit": coupon.usage_limit,
            }
        )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": coupon_list}})


# 用户领取优惠券
@coupons_bp.route("/api/coupon/claim/<int:coupon_id>", methods=["POST"])
def claim_coupon(coupon_id):
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 检查优惠券是否存在且有效
    coupon = Coupon.query.get_or_404(coupon_id)
    current_date = datetime.datetime.now().date()

    if not coupon.is_active:
        return jsonify({"code": 400, "message": "优惠券已失效"}), 400

    if coupon.start_date > current_date or coupon.end_date < current_date:
        return jsonify({"code": 400, "message": "优惠券不在有效期内"}), 400

    # 检查用户是否已经领取过该优惠券
    existing_user_coupon = UserCoupon.query.filter_by(username=current_user, coupon_id=coupon_id).first()

    if existing_user_coupon:
        return jsonify({"code": 409, "message": "您已经领取过该优惠券"}), 409

    # 创建用户优惠券记录
    user_coupon = UserCoupon(username=current_user, coupon_id=coupon_id)

    db.session.add(user_coupon)
    db.session.commit()

    return jsonify({"code": 200, "message": "优惠券领取成功", "data": {"coupon_name": coupon.coupon_name, "discount_type": coupon.discount_type, "discount_value": float(coupon.discount_value)}})


# 获取用户的优惠券
@coupons_bp.route("/api/coupon/my-coupons", methods=["GET"])
def get_my_coupons():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    # 查询用户的优惠券
    user_coupons = db.session.query(UserCoupon, Coupon).join(Coupon, UserCoupon.coupon_id == Coupon.coupon_id).filter(UserCoupon.username == current_user).all()

    coupon_list = []
    current_date = datetime.datetime.now().date()

    for user_coupon, coupon in user_coupons:
        # 判断优惠券状态
        is_expired = coupon.end_date < current_date
        is_used_up = user_coupon.used_count >= coupon.usage_limit
        can_use = not is_expired and not is_used_up and coupon.is_active

        coupon_list.append(
            {
                "coupon_id": coupon.coupon_id,
                "coupon_name": coupon.coupon_name,
                "discount_type": coupon.discount_type,
                "discount_value": float(coupon.discount_value),
                "min_amount": float(coupon.min_amount),
                "start_date": coupon.start_date.isoformat(),
                "end_date": coupon.end_date.isoformat(),
                "usage_limit": coupon.usage_limit,
                "used_count": user_coupon.used_count,
                "obtained_at": user_coupon.obtained_at.isoformat(),
                "can_use": can_use,
                "is_expired": is_expired,
            }
        )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": coupon_list}})


# 使用优惠券（在订单支付时调用）
@coupons_bp.route("/api/coupon/use/<int:coupon_id>", methods=["POST"])
def use_coupon(coupon_id):
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]

    data = request.json
    order_amount = data.get("amount", 0)

    # 查找用户的优惠券
    user_coupon = UserCoupon.query.filter_by(username=current_user, coupon_id=coupon_id).first()

    if not user_coupon:
        return jsonify({"code": 404, "message": "您没有该优惠券"}), 404

    # 获取优惠券信息
    coupon = Coupon.query.get_or_404(coupon_id)
    current_date = datetime.datetime.now().date()

    # 验证优惠券状态
    if not coupon.is_active:
        return jsonify({"code": 400, "message": "优惠券已失效"}), 400

    if coupon.end_date < current_date:
        return jsonify({"code": 400, "message": "优惠券已过期"}), 400

    if user_coupon.used_count >= coupon.usage_limit:
        return jsonify({"code": 400, "message": "优惠券使用次数已达上限"}), 400

    if order_amount < coupon.min_amount:
        return jsonify({"code": 400, "message": f"订单金额不满足最低消费要求({coupon.min_amount}元)"}), 400

    # 计算折扣金额
    if coupon.discount_type == "percentage":
        discount_amount = order_amount * (float(coupon.discount_value) / 100)
    else:  # fixed
        discount_amount = float(coupon.discount_value)

    # 确保折扣金额不超过订单金额
    discount_amount = min(discount_amount, order_amount)
    final_amount = order_amount - discount_amount

    # 增加使用次数
    user_coupon.used_count += 1
    db.session.commit()

    return jsonify({"code": 200, "message": "优惠券使用成功", "data": {"original_amount": order_amount, "discount_amount": discount_amount, "final_amount": final_amount, "coupon_name": coupon.coupon_name}})
