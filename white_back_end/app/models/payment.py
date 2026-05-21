import datetime

from app.extensions import db

class Payment(db.Model):
    __tablename__ = "payment"
    payment_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    order_id = db.Column(db.Integer, db.ForeignKey("orders.order_id"), nullable=False)
    passenger_username = db.Column(db.String(20), db.ForeignKey("user.username"), nullable=False)
    driver_username = db.Column(db.String(20), db.ForeignKey("user.username"), nullable=False)
    amount = db.Column(db.Numeric(10, 2), nullable=False)
    # 支付状态：CREATED / PENDING / PAID / FAILED / CLOSED（兼容历史值 UNPAID）
    status = db.Column(db.String(20), nullable=False, default="CREATED")
    # 本系统支付单号，唯一；用于和支付宝交互
    out_trade_no = db.Column(db.String(64), unique=True, nullable=True)
    # 支付宝交易号，支付成功后回写
    alipay_trade_no = db.Column(db.String(64), nullable=True)
    # 支付宝返回的网页支付跳转链接（含签名的 querystring 完整 URL）
    pay_url = db.Column(db.Text, nullable=True)
    paid_at = db.Column(db.DateTime, nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)
    updated_at = db.Column(
        db.DateTime,
        default=datetime.datetime.now,
        onupdate=datetime.datetime.now,
    )

    __table_args__ = (
        db.UniqueConstraint("order_id", "passenger_username", name="uq_payment_order_passenger"),
    )