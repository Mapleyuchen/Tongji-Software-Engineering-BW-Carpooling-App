import datetime

from app.extensions import db


# 司机钱包：每个司机一行，余额 / 累计收入 / 累计提现
class DriverWallet(db.Model):
    __tablename__ = "driver_wallet"

    driver_username = db.Column(
        db.String(20), db.ForeignKey("user.username"), primary_key=True
    )
    balance = db.Column(db.Numeric(12, 2), nullable=False, default=0)
    total_income = db.Column(db.Numeric(12, 2), nullable=False, default=0)
    total_withdraw = db.Column(db.Numeric(12, 2), nullable=False, default=0)
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)
    updated_at = db.Column(
        db.DateTime,
        default=datetime.datetime.now,
        onupdate=datetime.datetime.now,
    )


# 钱包流水：包含收入（INCOME）和提现（WITHDRAW）两种
# - INCOME 由乘客付款触发，payment_id 必填，用于幂等
# - WITHDRAW 由司机主动提现触发，payment_id 为 NULL
class WalletTransaction(db.Model):
    __tablename__ = "wallet_transaction"

    transaction_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    driver_username = db.Column(
        db.String(20), db.ForeignKey("user.username"), nullable=False, index=True
    )
    # INCOME 类型必须绑定一条 payment，并且对同一 payment_id 只允许一次
    payment_id = db.Column(
        db.Integer, db.ForeignKey("payment.payment_id"), nullable=True
    )
    # 收入类型会带订单号；提现类型为 NULL
    order_id = db.Column(
        db.Integer, db.ForeignKey("orders.order_id"), nullable=True
    )
    amount = db.Column(db.Numeric(12, 2), nullable=False)
    # INCOME / WITHDRAW
    type = db.Column(db.String(16), nullable=False)
    # SUCCESS / PENDING / FAILED
    status = db.Column(db.String(16), nullable=False, default="SUCCESS")
    # 备注（用于区分真实支付宝转账 vs 本地模拟提现）
    remark = db.Column(db.String(200), nullable=True)
    # 支付宝交易号 / 提现转账号
    alipay_trade_no = db.Column(db.String(64), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.datetime.now)
    updated_at = db.Column(
        db.DateTime,
        default=datetime.datetime.now,
        onupdate=datetime.datetime.now,
    )

    __table_args__ = (
        # 同一 payment 只能产生一笔 INCOME 流水（数据库层兜底，防止并发重复入账）
        db.UniqueConstraint("payment_id", "type", name="uq_wallet_txn_payment_type"),
    )
