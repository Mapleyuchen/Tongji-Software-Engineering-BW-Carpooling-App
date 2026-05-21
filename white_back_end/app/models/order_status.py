import datetime

from app.extensions import db


class OrderStatus(db.Model):
    __tablename__ = "order_status"
    order_id = db.Column(db.Integer, db.ForeignKey("orders.order_id"), primary_key=True)
    status = db.Column(db.Integer, default=0)  # 0: 未开始, 1: 进行中, 2: 已完成
    user1_arrived = db.Column(db.Boolean, default=False)
    user2_arrived = db.Column(db.Boolean, default=False)
    user3_arrived = db.Column(db.Boolean, default=False)
    user4_arrived = db.Column(db.Boolean, default=False)
    driver_arrived = db.Column(db.Boolean, default=False)
    completed_at = db.Column(db.DateTime, nullable=True)  # 订单完成时间

