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
from app.utils.chat import (
    ROLE_DRIVER,
    ROLE_PASSENGER,
    add_member_to_order_conversation,
    append_system_message_for_order,
    create_conversation_for_new_order,
    has_any_participant,
    remove_member_from_order_conversation,
)
from app.utils.auth import check_token, generate_token


orders_bp = Blueprint("orders", __name__)


@orders_bp.route('/api/orders', methods=['GET'])
def get_orders():
    orders = Order.query.all()
    data = [
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
            "remark": order.remark
        } for order in orders
    ]
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {
            "list": data
        }
    })


# 添加新订单
@orders_bp.route('/api/orders/add', methods=['POST'])
def add_order():
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
    current_user = payload['username']  # 解析订单发起人
    user_info = User.query.get_or_404(current_user)

    if user_info.usertype == 2:
        return jsonify({
            "code": 403,
            "message": "您当前身份为司机，不可新建订单"
        }), 403

    data = request.json
    new_order = Order(
        user1=current_user,
        departure=data['departure'],
        destination=data['destination'],
        date=datetime.datetime.strptime(data['date'], '%Y-%m-%d').date(),
        earliest_departure_time=datetime.datetime.strptime(data['earliest_departure_time'], '%H:%M').time(),
        latest_departure_time=datetime.datetime.strptime(data['latest_departure_time'], '%H:%M').time(),
        remark=data['remark']
    )
    db.session.add(new_order)
    db.session.flush()

    db.session.add(OrderStatus(order_id=new_order.order_id))
    create_conversation_for_new_order(new_order, current_user)
    db.session.commit()

    data = {
            "order_id": new_order.order_id,
            "departure": new_order.departure,
            "destination": new_order.destination,
            "date": new_order.date.isoformat(),
            "earliest_departure_time": new_order.earliest_departure_time.isoformat(),
            "latest_departure_time": new_order.latest_departure_time.isoformat(),
            "remark": new_order.remark
        }
    return jsonify({
        "code": 201,
        "message": "Order added successfully",
        "username": current_user,
        "data": data
    }), 201


# 获取单个订单
@orders_bp.route('/api/orders/<int:order_id>', methods=['GET'])
def get_order(order_id):
    order = Order.query.get_or_404(order_id)
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data":
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
                "remark": order.remark
            }
    })


# 删除订单
@orders_bp.route('/api/orders/leave', methods=['POST'])
def delete_order():
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

    # 获取请求体中的order_id
    data = request.json
    order_id = data.get('order_id')
    if not order_id:
        return jsonify({
            "code": 400,
            "message": "order_id缺失"
        }), 400

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload['username']

    order = Order.query.get_or_404(order_id)
    user_info = User.query.get_or_404(current_user)
    if user_info.usertype == 1:
        users = [order.user1, order.user2, order.user3, order.user4]

        non_empty_users = [user for user in users if user is not None]
        non_empty_count = len(non_empty_users)

        # 移除当前用户
        if current_user in non_empty_users:
            if order.user1 == current_user:
                order.user1 = None
            elif order.user2 == current_user:
                order.user2 = None
            elif order.user3 == current_user:
                order.user3 = None
            elif order.user4 == current_user:
                order.user4 = None

            # 重新排列用户
            users = [order.user1, order.user2, order.user3, order.user4]
            users = [user for user in users if user is not None]
            order.user1, order.user2, order.user3, order.user4 = (
                users[0] if len(users) > 0 else None,
                users[1] if len(users) > 1 else None,
                users[2] if len(users) > 2 else None,
                users[3] if len(users) > 3 else None
            )

            append_system_message_for_order(order.order_id, f"{current_user}已退出拼车")
            remove_member_from_order_conversation(order.order_id, current_user)

            # 检查是否所有用户都离开了
            if len(users) == 0:
                # 先删除与该订单关联的所有评分记录
                DriverRating.query.filter_by(order_id=order.order_id).delete()
                
                # 删除订单状态记录
                OrderStatus.query.filter_by(order_id=order.order_id).delete()
                
                # 然后删除订单本身
                db.session.delete(order)
                db.session.commit()
            else:
                db.session.commit()

            return jsonify({
                "code": 200,
                "message": "离开订单成功"
            })
        else:
            return jsonify({
                "code": 404,
                "message": "订单中没有该用户"
            }), 404

    elif user_info.usertype == 2:
        if order.driver == current_user:
            order.driver = None
            append_system_message_for_order(order.order_id, f"司机{current_user}已退出拼车")
            remove_member_from_order_conversation(order.order_id, current_user)
            if not has_any_participant(order):
                DriverRating.query.filter_by(order_id=order.order_id).delete()
                OrderStatus.query.filter_by(order_id=order.order_id).delete()
                db.session.delete(order)
            db.session.commit()
            return jsonify({
                "code": 200,
                "message": "离开订单成功"
            })
        else:
            return jsonify({
                "code": 404,
                "message": "订单中没有该用户"
            }), 404
    else:
        return jsonify({
            "code": 404,
            "message": "用户类型错误"
        }), 404


@orders_bp.route('/api/orders/join', methods=['POST'])
def join_order():
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
    current_user = payload['username']  # 解析加入订单的用户

    data = request.json
    order_id = data.get('order_id')

    if not order_id:
        return jsonify({
            "code": 400,
            "message": "order_id缺失"
        }), 400

    order = Order.query.get_or_404(order_id)
    # 检查订单是否已满员
    user_info = User.query.get_or_404(current_user)
    if user_info.usertype == 1:  # 一般用户
        users = [order.user1, order.user2, order.user3, order.user4]
        non_empty_users = [user for user in users if user is not None]
        if current_user in non_empty_users:
            return jsonify({
                "code": 409,
                "message": "用户已在订单中"
            }), 409
        if len(non_empty_users) >= 4:
            return jsonify({
                "code": 422,
                "message": "加入失败，订单已满员"
            }), 422
        # 订单没有满员
        if order.user1 is None:
            order.user1 = current_user
        elif order.user2 is None:
            order.user2 = current_user
        elif order.user3 is None:
            order.user3 = current_user
        elif order.user4 is None:
            order.user4 = current_user

        add_member_to_order_conversation(order, current_user, ROLE_PASSENGER)
        append_system_message_for_order(order.order_id, f"{current_user}已加入拼车")
        db.session.commit()

        return jsonify({
            "code": 200,
            "message": "加入订单成功"
        }), 200

    elif user_info.usertype == 2:  # 司机
        if order.driver is None:
            order.driver = current_user
            add_member_to_order_conversation(order, current_user, ROLE_DRIVER)
            append_system_message_for_order(order.order_id, f"司机{current_user}已接单")
            db.session.commit()
            return jsonify({
                "code": 200,
                "message": "加入订单成功"
            }), 200

        else:
            return jsonify({
                "code": 422,
                "message": "加入失败，订单内已有司机"
            }), 422
    else:
        return jsonify({
            "code": 404,
            "message": "用户类型错误"
        }), 404


@orders_bp.route('/api/orders/search/<string:keyword>', methods=['GET'])
def search_orders(keyword):
    search_condition = f"%{keyword}%"
    # 去和字段departure以及destination做匹配
    orders = Order.query.filter(
        (Order.departure.ilike(search_condition)) | (Order.destination.ilike(search_condition))
    ).order_by(Order.date.asc()).all()

    result = [
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
            "remark": order.remark
        } for order in orders
    ]
        
    return jsonify({
        "code": 200,
        "message": "搜索成功",
        "data": {
            "list": result
        }
    })



@orders_bp.route("/api/orders/not-started", methods=["GET"])
def get_not_started_orders():
    # 先获取所有订单
    orders = Order.query.all()
    result = []

    for order in orders:
        # 获取订单状态
        order_status = OrderStatus.query.filter_by(order_id=order.order_id).first()

        # 如果没有状态记录，创建一个（默认为未开始）
        if not order_status:
            order_status = OrderStatus(order_id=order.order_id, status=0, user1_arrived=False, user2_arrived=False, user3_arrived=False, user4_arrived=False, driver_arrived=False)  # 0表示未开始
            db.session.add(order_status)
            db.session.commit()

        # 只添加未开始的订单
        if order_status.status == 0:
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
                }
            )

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": result}})


# 车辆相关接口
