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


vehicles_bp = Blueprint("vehicles", __name__)


@vehicles_bp.route("/api/vehicle/add", methods=["POST"])
def add_vehicle():
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

    # 验证用户是司机
    user = User.query.filter_by(username=current_user).first()
    if not user or user.usertype != 2:
        return jsonify({"code": 403, "message": "只有司机可以添加车辆信息"}), 403

    data = request.json
    license_plate = data.get("license_plate")
    brand = data.get("brand")
    model = data.get("model")
    color = data.get("color")
    seat_count = data.get("seat_count", 4)

    if not all([license_plate, brand, model, color]):
        return jsonify({"code": 400, "message": "车辆信息不完整"}), 400

    # 检查车牌号是否已存在
    existing_vehicle = Vehicle.query.filter_by(license_plate=license_plate).first()
    if existing_vehicle:
        return jsonify({"code": 409, "message": "车牌号已存在"}), 409

    # 创建车辆记录
    vehicle = Vehicle(driver_username=current_user, license_plate=license_plate, brand=brand, model=model, color=color, seat_count=seat_count)

    db.session.add(vehicle)
    db.session.commit()

    return jsonify({"code": 201, "message": "车辆信息添加成功", "data": {"vehicle_id": vehicle.vehicle_id, "license_plate": vehicle.license_plate, "brand": vehicle.brand, "model": vehicle.model, "color": vehicle.color, "seat_count": vehicle.seat_count, "is_verified": vehicle.is_verified}})


# 获取司机的车辆信息
@vehicles_bp.route("/api/vehicle/my-vehicles", methods=["GET"])
def get_my_vehicles():
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

    # 验证用户是司机
    user = User.query.filter_by(username=current_user).first()
    if not user or user.usertype != 2:
        return jsonify({"code": 403, "message": "只有司机可以查看车辆信息"}), 403

    vehicles = Vehicle.query.filter_by(driver_username=current_user).all()

    vehicle_list = []
    for vehicle in vehicles:
        vehicle_list.append({"vehicle_id": vehicle.vehicle_id, "license_plate": vehicle.license_plate, "brand": vehicle.brand, "model": vehicle.model, "color": vehicle.color, "seat_count": vehicle.seat_count, "is_verified": vehicle.is_verified, "created_at": vehicle.created_at.isoformat()})

    return jsonify({"code": 200, "message": "查询成功", "data": {"list": vehicle_list}})


# 更新车辆信息
@vehicles_bp.route("/api/vehicle/update/<int:vehicle_id>", methods=["PUT"])
def update_vehicle(vehicle_id):
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

    # 查找车辆
    vehicle = Vehicle.query.get_or_404(vehicle_id)

    # 验证车辆所有权
    if vehicle.driver_username != current_user:
        return jsonify({"code": 403, "message": "无权修改此车辆信息"}), 403

    data = request.json

    # 更新车辆信息
    if "brand" in data:
        vehicle.brand = data["brand"]
    if "model" in data:
        vehicle.model = data["model"]
    if "color" in data:
        vehicle.color = data["color"]
    if "seat_count" in data:
        vehicle.seat_count = data["seat_count"]
    if "license_plate" in data:
        # 检查新车牌号是否已存在
        existing_vehicle = Vehicle.query.filter_by(license_plate=data["license_plate"]).first()
        if existing_vehicle and existing_vehicle.vehicle_id != vehicle_id:
            return jsonify({"code": 409, "message": "车牌号已存在"}), 409
        vehicle.license_plate = data["license_plate"]

    db.session.commit()

    return jsonify({"code": 200, "message": "车辆信息更新成功", "data": {"vehicle_id": vehicle.vehicle_id, "license_plate": vehicle.license_plate, "brand": vehicle.brand, "model": vehicle.model, "color": vehicle.color, "seat_count": vehicle.seat_count, "is_verified": vehicle.is_verified}})


# 删除车辆信息
@vehicles_bp.route("/api/vehicle/delete/<int:vehicle_id>", methods=["DELETE"])
def delete_vehicle(vehicle_id):
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

    # 查找车辆
    vehicle = Vehicle.query.get_or_404(vehicle_id)

    # 验证车辆所有权
    if vehicle.driver_username != current_user:
        return jsonify({"code": 403, "message": "无权删除此车辆信息"}), 403

    db.session.delete(vehicle)
    db.session.commit()

    return jsonify({"code": 200, "message": "车辆信息删除成功"})


# 优惠券相关接口
