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


users_bp = Blueprint("users", __name__)


@users_bp.route('/api/look', methods=['GET'])
def look():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({"code": 401, "message": "缺少Token"}), 401

    message = check_token(token)
    if message:
        return message

    # 查询所有用户数据
    users = User.query.all()
    table_data = [
        {
            "username": user.username,
            "password": user.password
        } for user in users
    ]
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {
            "table": table_data
        }
    })



@users_bp.route('/api/user/<string:username>', methods=['GET'])
def user_info(username):
    user = User.query.get_or_404(username)
    if user.usertype == 1:
        type = '一般用户'
    elif user.usertype == 2:
        type = '司机'
    else:
        type = '类型错误'
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data":
            {
                "phonenumber": user.phonenumber,
                "usertype": f"{type}"
            }
    })


@users_bp.route('/api/user/orders', methods=['GET'])
def user_orders():
    # 获取请求头中的Token
    token = request.headers.get('Authorization')
    if not token:
        return jsonify({
            "code": 401,
            "message": "Token缺失"
        }), 401

    # 检查Token的有效性
    check_result = check_token(token)
    if check_result:
        return check_result  # 如果Token无效，直接返回错误信息

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload['username']  # 当前用户

    # 查询当前用户参与的所有订单
    orders = Order.query.filter(
        (Order.user1 == current_user) |
        (Order.user2 == current_user) |
        (Order.user3 == current_user) |
        (Order.user4 == current_user)
    ).all()

    result = []
    for order in orders:
        result.append({
            "order_id": order.order_id,
            "user1": order.user1,
            "user2": order.user2,
            "user3": order.user3,
            "user4": order.user4,
            "driver": order.driver,
            "departure": order.departure,
            "destination": order.destination,
            "date": order.date.isoformat(),
            "earliest_departure_time": order.earliest_departure_time.isoformat(),
            "latest_departure_time": order.latest_departure_time.isoformat(),
            "remark": order.remark
        })

    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {
            "list": result
        }
    })


@users_bp.route("/api/user/current-order", methods=["GET"])
def get_current_order():
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

    # 获取当前日期和时间
    current_date = datetime.datetime.now().date()
    current_time = datetime.datetime.now()

    # 查询当天用户参与的所有订单
    today_orders = Order.query.filter(((Order.user1 == current_user) | (Order.user2 == current_user) | (Order.user3 == current_user) | (Order.user4 == current_user) | (Order.driver == current_user)) & (Order.date == current_date)).all()

    # 没有当天订单
    if not today_orders:
        return jsonify({"code": 404, "message": "未找到相关订单"})

    # 将订单分为三类：已完成、进行中、未开始
    completed_orders = []  # 已完成订单 (status = 2)
    in_progress_orders = []  # 进行中订单 (status = 1)
    not_started_orders = []  # 未开始订单 (status = 0)

    for order in today_orders:
        order_status = OrderStatus.query.filter_by(order_id=order.order_id).first()

        # 如果没有状态记录，创建一个
        if not order_status:
            order_status = OrderStatus(order_id=order.order_id, status=0, user1_arrived=False, user2_arrived=False, user3_arrived=False, user4_arrived=False, driver_arrived=False)
            db.session.add(order_status)
            db.session.commit()

        if order_status.status == 2:  # 已完成
            completed_orders.append((order, order_status))
        elif order_status.status == 1:  # 进行中
            in_progress_orders.append((order, order_status))
        elif order_status.status == 0:  # 未开始
            not_started_orders.append((order, order_status))

    # 对各类订单按时间排序
    completed_orders.sort(key=lambda x: x[1].completed_at if x[1].completed_at else datetime.datetime.min, reverse=True)
    in_progress_orders.sort(key=lambda x: datetime.datetime.combine(x[0].date, x[0].earliest_departure_time))
    not_started_orders.sort(key=lambda x: datetime.datetime.combine(x[0].date, x[0].earliest_departure_time))

    target_order = None
    target_order_status = None

    # 优先级1：显示进行中订单（取最早的）
    if in_progress_orders:
        target_order, target_order_status = in_progress_orders[0]

    # 优先级2：显示已超过开始时间或开始时间在30分钟内的未开始订单
    elif not_started_orders:
        # 筛选出已超过开始时间的未开始订单
        overdue_orders = []
        future_orders = []

        for order, status in not_started_orders:
            order_start_time = datetime.datetime.combine(order.date, order.earliest_departure_time)
            time_diff = (order_start_time - current_time).total_seconds() / 60  # 转换为分钟

            if order_start_time <= current_time:  # 已超过开始时间
                overdue_orders.append((order, status))
            else:
                future_orders.append((order, status, time_diff))

        # 按时间差排序
        future_orders.sort(key=lambda x: x[2])  # 按时间差升序排序

        if overdue_orders:  # 有已超过开始时间的未开始订单
            target_order, target_order_status = overdue_orders[0]  # 取最早的
        elif future_orders and completed_orders:
            # 判断最近的未开始订单是否在30分钟内
            nearest_future_order, nearest_future_status, time_diff = future_orders[0]

            # 检查用户是否已对最近完成的订单评分
            last_completed_order, last_completed_status = completed_orders[0]
            has_rated = True  # 默认假设已评分

            if last_completed_order.driver:
                # 查询用户是否已经评分
                existing_rating = DriverRating.query.filter_by(order_id=last_completed_order.order_id, user_username=current_user, driver_username=last_completed_order.driver).first()
                has_rated = existing_rating is not None

                # 如果是司机，视为已评分（司机不需要评价自己）
                if current_user == last_completed_order.driver:
                    has_rated = True

            # 如果最近的未开始订单开始时间在30分钟内，显示未开始订单
            # 否则，如果用户未评分，显示已完成订单
            if time_diff <= 30:
                target_order, target_order_status = nearest_future_order, nearest_future_status
            elif not has_rated and current_user != last_completed_order.driver:
                target_order, target_order_status = last_completed_order, last_completed_status
            else:
                # 如果超过30分钟，且已完成订单已评分或用户是司机，显示未开始订单
                target_order, target_order_status = nearest_future_order, nearest_future_status
        elif future_orders:  # 没有已完成订单，显示最近的未开始订单
            target_order, target_order_status = future_orders[0][0], future_orders[0][1]

    # 优先级3：显示已完成订单（未评分的情况）
    elif completed_orders:
        last_completed_order, last_completed_status = completed_orders[0]

        # 检查用户是否已经评分
        has_rated = False
        if last_completed_order.driver:
            # 查询用户是否已经评分
            existing_rating = DriverRating.query.filter_by(order_id=last_completed_order.order_id, user_username=current_user, driver_username=last_completed_order.driver).first()
            has_rated = existing_rating is not None

            # 如果是司机，视为已评分（司机不需要评价自己）
            if current_user == last_completed_order.driver:
                has_rated = True

        # 如果未评分且当前用户不是司机，显示最后完成的订单
        if not has_rated and current_user != last_completed_order.driver:
            target_order = last_completed_order
            target_order_status = last_completed_status
        else:
            # 已评分或用户是司机，返回无订单
            return jsonify({"code": 404, "message": "未找到相关订单"})

    # 所有情况都没有匹配到
    if not target_order:
        return jsonify({"code": 404, "message": "未找到相关订单"})

    # 返回选中的订单
    return jsonify(
        {
            "code": 200,
            "message": "查询成功",
            "data": {
                "order": {
                    "order_id": target_order.order_id,
                    "user1": target_order.user1,
                    "user2": target_order.user2,
                    "user3": target_order.user3,
                    "user4": target_order.user4,
                    "driver": target_order.driver,
                    "departure": target_order.departure,
                    "destination": target_order.destination,
                    "date": target_order.date.isoformat(),
                    "earliest_departure_time": target_order.earliest_departure_time.isoformat(),
                    "latest_departure_time": target_order.latest_departure_time.isoformat(),
                    "remark": target_order.remark,
                },
                "status": {
                    "status": target_order_status.status,
                    "user1_arrived": target_order_status.user1_arrived,
                    "user2_arrived": target_order_status.user2_arrived,
                    "user3_arrived": target_order_status.user3_arrived,
                    "user4_arrived": target_order_status.user4_arrived,
                    "driver_arrived": target_order_status.driver_arrived,
                },
                "start": {
                    "name": target_order.departure,
                    "lon": posData[target_order.departure][0],
                    "lat": posData[target_order.departure][1]
                },
                "end": {
                    "name": target_order.destination,
                    "lon": posData[target_order.destination][0],
                    "lat": posData[target_order.destination][1]
                }
            },
        }
    )




@users_bp.route("/api/user/completed-orders", methods=["GET"])
def get_completed_orders():
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

    # 查询用户参与且已完成的订单
    completed_orders = (
        Order.query.join(OrderStatus, Order.order_id == OrderStatus.order_id)
        .filter(((Order.user1 == current_user) | (Order.user2 == current_user) | (Order.user3 == current_user) | (Order.user4 == current_user) | (Order.driver == current_user)) & (OrderStatus.status == 2))  # 已完成状态
        .order_by(OrderStatus.completed_at.desc())
        .all()
    )

    result = []
    for order in completed_orders:
        order_status = OrderStatus.query.filter_by(order_id=order.order_id).first()
        result.append(
            {
                "order_id": order.order_id,
                "user1": order.user1,
                "user2": order.user2,
                "user3": order.user3,
                "user4": order.user4,
                "driver": order.driver,
                "departure": order.departure,
                "destination": order.destination,
                "date": order.date.isoformat(),
                "earliest_departure_time": order.earliest_departure_time.isoformat(),
                "latest_departure_time": order.latest_departure_time.isoformat(),
                "remark": order.remark,
                "completed_at": order_status.completed_at.isoformat() if order_status.completed_at else None,
            }
        )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": result}})


