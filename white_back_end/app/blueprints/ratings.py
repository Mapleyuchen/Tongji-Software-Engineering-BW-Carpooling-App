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


ratings_bp = Blueprint("ratings", __name__)


@ratings_bp.route("/api/order/rate-driver", methods=["POST"])
def rate_driver():
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
    rating = data.get("rating")

    if not order_id or not rating:
        return jsonify({"code": 400, "message": "订单ID或评分缺失"}), 400

    # 验证评分范围
    try:
        rating_value = float(rating)
        if rating_value < 1 or rating_value > 5:
            return jsonify({"code": 400, "message": "评分必须在1-5之间"}), 400
    except ValueError:
        return jsonify({"code": 400, "message": "评分必须是数字"}), 400

    # 获取订单信息
    order = Order.query.get_or_404(order_id)

    # 确保用户是该订单的参与者
    if current_user != order.user1 and current_user != order.user2 and current_user != order.user3 and current_user != order.user4:
        return jsonify({"code": 403, "message": "您不是该订单的参与者"}), 403

    # 找到司机
    driver_user = User.query.filter_by(username=order.driver).first()
    if not driver_user:
        return jsonify({"code": 404, "message": "找不到该订单的司机"}), 404

    # 检查用户是否已经评价过
    existing_rating = DriverRating.query.filter_by(order_id=order_id, user_username=current_user, driver_username=driver_user.username).first()

    # 获取或创建司机平均评分记录
    driver_avg_rating = DriverAverageRating.query.filter_by(driver_username=driver_user.username).first()

    if not driver_avg_rating:
        driver_avg_rating = DriverAverageRating(driver_username=driver_user.username, average_rating=5.0, rating_count=0)
        db.session.add(driver_avg_rating)

    if existing_rating:
        # 如果已有评分，需要更新平均分
        old_rating = existing_rating.rating
        # 更新现有评分记录
        existing_rating.rating = rating_value
        existing_rating.created_at = datetime.datetime.now()

        # 更新平均评分: 从总和中减去旧评分再加上新评分
        if driver_avg_rating.rating_count > 0:
            total_rating = driver_avg_rating.average_rating * driver_avg_rating.rating_count
            total_rating = total_rating - old_rating + rating_value
            driver_avg_rating.average_rating = total_rating / driver_avg_rating.rating_count
    else:
        # 创建新评分
        new_rating = DriverRating(order_id=order_id, driver_username=driver_user.username, user_username=current_user, rating=rating_value)
        db.session.add(new_rating)

        # 更新平均评分: 计算新的平均值
        if driver_avg_rating.rating_count == 0:
            driver_avg_rating.average_rating = rating_value
        else:
            total_rating = driver_avg_rating.average_rating * driver_avg_rating.rating_count
            driver_avg_rating.average_rating = (total_rating + rating_value) / (driver_avg_rating.rating_count + 1)

        # 增加评分次数
        driver_avg_rating.rating_count += 1

    db.session.commit()

    return jsonify({"code": 200, "message": "评分提交成功", "data": {"driver_rating": driver_avg_rating.average_rating}})


# 获取司机评分
@ratings_bp.route("/api/user/driver-rating/<string:username>", methods=["GET"])
def get_driver_rating(username):
    # 验证该用户是司机
    driver = User.query.filter_by(username=username, usertype=2).first()
    if not driver:
        return jsonify({"code": 404, "message": "找不到该司机"}), 404

    # 获取司机平均评分
    driver_avg_rating = DriverAverageRating.query.filter_by(driver_username=username).first()

    if not driver_avg_rating:
        # 如果没有评分记录，返回默认值
        return jsonify({"code": 200, "message": "查询成功", "data": {"username": username, "rating": 5.0, "rating_count": 0}})

    return jsonify({"code": 200, "message": "查询成功", "data": {"username": username, "rating": driver_avg_rating.average_rating, "rating_count": driver_avg_rating.rating_count}})


# 检查用户是否已对订单司机评分
@ratings_bp.route("/api/order/check-user-rating", methods=["POST"])
def check_user_rating():
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
    driver_username = data.get("driver_username")

    if not order_id or not driver_username:
        return jsonify({"code": 400, "message": "订单ID或司机用户名缺失"}), 400

    # 查询是否已经评分
    existing_rating = DriverRating.query.filter_by(order_id=order_id, user_username=current_user, driver_username=driver_username).first()

    return jsonify({"code": 200, "message": "查询成功", "data": {"has_rated": existing_rating is not None}})
