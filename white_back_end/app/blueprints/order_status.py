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


order_status_bp = Blueprint("order_status", __name__)


@order_status_bp.route("/api/order/confirm-arrival", methods=["POST"])
def confirm_arrival():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")

    if not order_id:
        return jsonify({"code": 400, "message": "order_id缺失"}), 400

    # 获取订单和订单状态
    order = Order.query.get_or_404(order_id)
    order_status = OrderStatus.query.filter_by(order_id=order_id).first()

    if not order_status:
        return jsonify({"code": 404, "message": "订单状态不存在"}), 404

    # 标记用户到达状态
    is_driver = False
    if current_user == order.driver:
        order_status.driver_arrived = True
        is_driver = True
    elif order.user1 == current_user:
        order_status.user1_arrived = True
    elif order.user2 == current_user:
        order_status.user2_arrived = True
    elif order.user3 == current_user:
        order_status.user3_arrived = True
    elif order.user4 == current_user:
        order_status.user4_arrived = True
    else:
        return jsonify({"code": 403, "message": "您不是该订单的参与者"}), 403

    # 检查是否所有乘客都已到达（不包括司机）
    all_passengers_arrived = True
    if order.user1 and order.user1 != order.driver and not order_status.user1_arrived:
        all_passengers_arrived = False
    if order.user2 and order.user2 != order.driver and not order_status.user2_arrived:
        all_passengers_arrived = False
    if order.user3 and order.user3 != order.driver and not order_status.user3_arrived:
        all_passengers_arrived = False
    if order.user4 and order.user4 != order.driver and not order_status.user4_arrived:
        all_passengers_arrived = False

    # 订单状态保持为0（未开始），直到司机主动点击"开始行程"

    db.session.commit()

    return jsonify(
        {
            "code": 200,
            "message": "确认到达成功",
            "data": {
                "status": order_status.status,
                "user1_arrived": order_status.user1_arrived,
                "user2_arrived": order_status.user2_arrived,
                "user3_arrived": order_status.user3_arrived,
                "user4_arrived": order_status.user4_arrived,
                "driver_arrived": order_status.driver_arrived,
                "all_passengers_arrived": all_passengers_arrived,
            },
        }
    )


# 司机开始行程接口
@order_status_bp.route("/api/order/start-trip", methods=["POST"])
def start_trip():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")

    if not order_id:
        return jsonify({"code": 400, "message": "order_id缺失"}), 400

    # 获取订单和订单状态
    order = Order.query.get_or_404(order_id)
    order_status = OrderStatus.query.filter_by(order_id=order_id).first()

    if not order_status:
        return jsonify({"code": 404, "message": "订单状态不存在"}), 404

    # 检查是否是司机
    if current_user != order.driver:
        return jsonify({"code": 403, "message": "只有司机可以开始行程"}), 403

    # 检查司机是否已到达
    if not order_status.driver_arrived:
        return jsonify({"code": 400, "message": "司机尚未确认到达"}), 400

    # 检查所有乘客是否已到达
    all_passengers_arrived = True
    if order.user1 and order.user1 != order.driver and not order_status.user1_arrived:
        all_passengers_arrived = False
    if order.user2 and order.user2 != order.driver and not order_status.user2_arrived:
        all_passengers_arrived = False
    if order.user3 and order.user3 != order.driver and not order_status.user3_arrived:
        all_passengers_arrived = False
    if order.user4 and order.user4 != order.driver and not order_status.user4_arrived:
        all_passengers_arrived = False

    if not all_passengers_arrived:
        return jsonify({"code": 400, "message": "等待所有乘客确认到达"}), 400

    # 更新订单状态为进行中
    order_status.status = 1  # 进行中

    db.session.commit()

    return jsonify({"code": 200, "message": "行程开始成功", "data": {"status": order_status.status}})


# 确认到达目的地
@order_status_bp.route("/api/order/confirm-destination", methods=["POST"])
def confirm_destination():
    # 获取请求头中的Token
    token = request.headers.get("Authorization")
    if not token:
        return jsonify({"code": 401, "message": "Token缺失"}), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload["username"]  # 当前用户

    data = request.json
    order_id = data.get("order_id")

    if not order_id:
        return jsonify({"code": 400, "message": "order_id缺失"}), 400

    # 获取订单和订单状态
    order = Order.query.get_or_404(order_id)

    # 检查是否是司机
    user = User.query.filter_by(username=current_user).first()
    is_driver = user and current_user == order.driver

    if is_driver:
        # 司机确认处理
        order_status = OrderStatus.query.filter_by(order_id=order_id).first()
        if not order_status:
            return jsonify({"code": 404, "message": "订单状态不存在"}), 404

        # 只有在进行中状态才能确认到达目的地
        if order_status.status == 1:
            # 设置为"已完成"
            order_status.status = 2  # 已完成
            # 设置完成时间为当前时间
            order_status.completed_at = datetime.datetime.now()
            message = "确认到达目的地成功"
        else:
            message = "状态更新失败，订单状态异常"

        db.session.commit()

        return jsonify({"code": 200, "message": message, "data": {"status": order_status.status, "completed_at": order_status.completed_at.isoformat() if order_status.completed_at else None}})

    return jsonify({"code": 200, "message": "用户确认到达目的地"})


