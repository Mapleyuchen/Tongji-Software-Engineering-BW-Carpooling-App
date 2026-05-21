import datetime

from app.extensions import db


class DriverRating(db.Model):
    __tablename__ = "driver_rating"
    rating_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    order_id = db.Column(db.Integer, db.ForeignKey("orders.order_id"))
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"))
    user_username = db.Column(db.String(20), db.ForeignKey("user.username"))
    rating = db.Column(db.Float, nullable=False)  # 1-5分
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)


# 司机平均评分表 - 不修改User表，而是创建新表存储评分信息
class DriverAverageRating(db.Model):
    __tablename__ = "driver_average_rating"
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"), primary_key=True)
    average_rating = db.Column(db.Float, default=5.0)  # 默认5分
    rating_count = db.Column(db.Integer, default=0)  # 评分次数

