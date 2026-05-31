
from __future__ import annotations

import datetime
import logging
import uuid
from decimal import Decimal
from typing import Optional, Tuple

from sqlalchemy.exc import IntegrityError

from app.extensions import db
from app.models import DriverWallet, Payment, WalletTransaction
from app.utils.alipay_config import AlipayConfigError, get_alipay_client

_log = logging.getLogger("wallet")


# ---------- 工具函数 ----------

def _to_decimal(value) -> Decimal:
    if isinstance(value, Decimal):
        return value
    if value is None:
        return Decimal("0")
    return Decimal(str(value))


# 获取司机钱包，若不存在就建一条 0 余额的空钱包记录。
def _get_or_create_wallet(driver_username: str, lock: bool = False) -> DriverWallet:
    query = DriverWallet.query.filter_by(driver_username=driver_username)
    if lock:
        try:
            query = query.with_for_update()
        except Exception:  # pragma: no cover - 部分后端不支持时退回普通查询
            pass

    wallet = query.first()
    if wallet is None:
        wallet = DriverWallet(
            driver_username=driver_username,
            balance=Decimal("0"),
            total_income=Decimal("0"),
            total_withdraw=Decimal("0"),
        )
        db.session.add(wallet)
        db.session.flush()
    return wallet


# 读取（必要时创建）司机钱包，不加锁，用于查询接口调用
def get_wallet_for_driver(driver_username: str) -> DriverWallet:
    return _get_or_create_wallet(driver_username, lock=False)


# ---------- 入账 ----------

# 把一条 Payment 置为 PAID，并把金额加到对应司机的钱包余额上。
# 保证同一个 payment_id 只能给司机入账一次，避免 /query、模拟支付、异步通知三个入口重复触发导致重复加余额。
# 返回值：bool，表示本次调用是否真的新增了入账流水（True=刚刚入账，False=之前已经入过账）。
def mark_payment_paid_and_credit_driver(
    payment: Payment, alipay_trade_no: Optional[str] = None
) -> bool:
    if payment is None or payment.payment_id is None:
        return False

    # 关键：把当前 session 中的 payment 实例重新查询并加锁，避免读到陈旧 status
    locked_payment = (
        db.session.query(Payment)
        .filter_by(payment_id=payment.payment_id)
    )
    try:
        locked_payment = locked_payment.with_for_update()
    except Exception:  # pragma: no cover
        pass
    locked = locked_payment.first()
    if locked is None:
        return False

    now = datetime.datetime.now()

    # 1. 同步 Payment 终态信息
    if locked.status != "PAID":
        locked.status = "PAID"
        locked.paid_at = now
    if alipay_trade_no and not locked.alipay_trade_no:
        locked.alipay_trade_no = alipay_trade_no

    # 2. 入账（带数据库唯一约束兜底）
    existing = WalletTransaction.query.filter_by(
        payment_id=locked.payment_id, type="INCOME"
    ).first()
    if existing is not None:
        db.session.commit()
        return False

    amount = _to_decimal(locked.amount)
    if amount <= 0:
        # 金额异常仍然要把 Payment 标为 PAID，但不入账
        db.session.commit()
        _log.warning("payment_id=%s amount<=0，跳过入账", locked.payment_id)
        return False

    wallet = _get_or_create_wallet(locked.driver_username, lock=True)
    wallet.balance = _to_decimal(wallet.balance) + amount
    wallet.total_income = _to_decimal(wallet.total_income) + amount

    txn = WalletTransaction(
        driver_username=locked.driver_username,
        payment_id=locked.payment_id,
        order_id=locked.order_id,
        amount=amount,
        type="INCOME",
        status="SUCCESS",
        remark=f"订单 #{locked.order_id} 拼车收入",
        alipay_trade_no=alipay_trade_no or locked.alipay_trade_no,
    )
    db.session.add(txn)

    try:
        db.session.commit()
    except IntegrityError:
        # 并发场景下被另一个进程抢先写入，回滚后视为已入账
        db.session.rollback()
        _log.info("payment_id=%s 并发入账，已被其他请求处理", locked.payment_id)
        return False
    return True


# ---------- 提现 ----------

def _make_withdraw_out_biz_no(driver_username: str) -> str:
    ts = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    short = uuid.uuid4().hex[:8]
    safe_name = "".join(c for c in driver_username if c.isalnum())[:8] or "drv"
    return f"WD{safe_name}-{ts}-{short}"


# 校验并规范化司机填写的支付宝收款账号
def _validate_payee_account(payee_account: str) -> str:
    account = (payee_account or "").strip()
    if not account:
        raise ValueError("请填写支付宝收款账号")
    if "@" not in account or len(account) < 5:
        raise ValueError("收款账号格式不正确，沙箱示例：myaccount@sandbox.com")
    return account


# 尝试调用支付宝沙箱转账
# 返回值：(ok, alipay_order_id, message)
def _try_alipay_transfer(
    out_biz_no: str, amount: Decimal, payee_account: str, driver_username: str
) -> Tuple[bool, Optional[str], Optional[str]]:
    try:
        client = get_alipay_client()
    except AlipayConfigError as exc:
        return False, None, f"支付宝沙箱未配置：{exc}"
    except Exception as exc:  # pragma: no cover
        _log.exception("加载支付宝客户端失败")
        return False, None, f"加载支付宝客户端失败：{exc}"

    try:
        result = client.api_alipay_fund_trans_toaccount_transfer(
            out_biz_no=out_biz_no,
            payee_type="ALIPAY_LOGONID",
            payee_account=payee_account,
            amount=format(amount, ".2f"),
            payer_show_name="星途拼车",
            payee_real_name=None,
            remark=f"司机 {driver_username} 提现至 {payee_account}",
        )
    except Exception as exc:  # pragma: no cover
        _log.exception("调用支付宝转账失败")
        return False, None, f"调用支付宝转账失败：{exc}"

    code = str(result.get("code", "")) if isinstance(result, dict) else ""
    if code == "10000":
        return True, result.get("order_id") or result.get("alipay_order_no"), "支付宝沙箱转账成功"
    sub_msg = (
        result.get("sub_msg") or result.get("msg") or "支付宝转账失败"
        if isinstance(result, dict)
        else "支付宝转账失败"
    )
    return False, None, sub_msg


# 司机提现。
# 优先调用支付宝沙箱转账接口；
# 如果支付宝配置缺失或调用失败，则回退到本地模拟提现。
def perform_withdraw(
    driver_username: str, amount, payee_account: str
) -> Tuple[bool, str, WalletTransaction]:
    payee_account = _validate_payee_account(payee_account)
    amount_dec = _to_decimal(amount).quantize(Decimal("0.01"))
    if amount_dec <= 0:
        raise ValueError("提现金额必须大于 0")

    wallet = _get_or_create_wallet(driver_username, lock=True)
    balance = _to_decimal(wallet.balance)
    if amount_dec > balance:
        raise ValueError(f"当前可提现余额仅 {balance} 元，无法提现 {amount_dec} 元")

    out_biz_no = _make_withdraw_out_biz_no(driver_username)
    txn = WalletTransaction(
        driver_username=driver_username,
        payment_id=None,
        order_id=None,
        amount=amount_dec,
        type="WITHDRAW",
        status="PENDING",
        remark=f"提现至 {payee_account}（申请 {out_biz_no}）",
        alipay_trade_no=None,
    )
    db.session.add(txn)

    # 先扣余额、记累计提现，PENDING 流水也算占用余额
    wallet.balance = balance - amount_dec
    wallet.total_withdraw = _to_decimal(wallet.total_withdraw) + amount_dec
    db.session.commit()

    # 转账阶段：尝试调用支付宝沙箱，失败则回退本地模拟
    ok, alipay_order_id, message = _try_alipay_transfer(
        out_biz_no, amount_dec, payee_account, driver_username
    )

    if ok:
        txn.status = "SUCCESS"
        txn.alipay_trade_no = alipay_order_id
        txn.remark = f"支付宝沙箱转账成功：{payee_account}（{out_biz_no}）"
        final_message = "支付宝沙箱转账成功"
    else:
        # 演示环境：沙箱不可用 / 转账接口失败时回退到本地模拟提现
        txn.status = "SUCCESS"
        txn.remark = f"本地模拟提现至 {payee_account}（{message}）"
        final_message = "本地模拟提现成功（支付宝沙箱不可用，已记录提现流水）"

    db.session.commit()
    return True, final_message, txn
