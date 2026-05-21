import datetime

from app.extensions import db


class Coupon(db.Model):
    __tablename__ = "coupon"
    coupon_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    coupon_name = db.Column(db.String(100), nullable=False)
    discount_type = db.Column(db.String(20), nullable=False)  # percentage, fixed, ……
    discount_value = db.Column(db.Numeric(10, 2), nullable=False)
    min_amount = db.Column(db.Numeric(10, 2), default=0)  # 最低消费金额
    start_date = db.Column(db.Date, nullable=False)
    end_date = db.Column(db.Date, nullable=False)
    usage_limit = db.Column(db.Integer, default=1)  # 使用次数限制
    is_active = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)


class UserCoupon(db.Model):
    __tablename__ = "user_coupon"
    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    username = db.Column(db.String(20), db.ForeignKey("user.username"))
    coupon_id = db.Column(db.Integer, db.ForeignKey("coupon.coupon_id"))
    used_count = db.Column(db.Integer, default=0)
    obtained_at = db.Column(db.DateTime, default=datetime.datetime.now)

