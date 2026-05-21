import datetime

from app.extensions import db


class Order(db.Model):
    __tablename__ = 'orders'
    order_id = db.Column(db.Integer, primary_key=True, unique=True, nullable=False)  # 订单编号
    user1 = db.Column(db.String(20), nullable=True)  # 拼车的四位用户
    user2 = db.Column(db.String(20), nullable=True)
    user3 = db.Column(db.String(20), nullable=True)
    user4 = db.Column(db.String(20), nullable=True)
    driver = db.Column(db.String(20), nullable=True)
    departure = db.Column(db.String(100), nullable=False)  # 出发地
    destination = db.Column(db.String(100), nullable=False)  # 目的地
    date = db.Column(db.Date, nullable=False)  # 日期
    earliest_departure_time = db.Column(db.Time, nullable=False)  # 最早发车时间
    latest_departure_time = db.Column(db.Time, nullable=False)  # 最晚发车时间
    remark = db.Column(db.String(100), nullable=False)  # remark

    def __repr__(self):
        return f'<Order {self.order_id}>'

