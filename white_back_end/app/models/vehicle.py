import datetime

from app.extensions import db


class Vehicle(db.Model):
    __tablename__ = "vehicle"
    vehicle_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"), nullable=False)
    license_plate = db.Column(db.String(20), nullable=False, unique=True)  # 车牌号
    brand = db.Column(db.String(50), nullable=False)  # 品牌
    model = db.Column(db.String(50), nullable=False)  # 型号
    color = db.Column(db.String(20), nullable=False)  # 颜色
    seat_count = db.Column(db.Integer, nullable=False, default=4)  # 座位数
    is_verified = db.Column(db.Boolean, default=False)  # 是否认证
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)

