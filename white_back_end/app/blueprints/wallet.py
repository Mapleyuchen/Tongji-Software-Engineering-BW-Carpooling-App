
import jwt
from flask import Blueprint, jsonify, request

from app.extensions import db
from app.models import User, WalletTransaction
from app.utils.auth import check_token
from app.utils.wallet_service import get_wallet_for_driver, perform_withdraw

wallet_bp = Blueprint("wallet", __name__)


# ------------ 内部工具 ------------

# 登录校验 + 司机身份验证：成功返回 (None, username)；失败返回 flask response, None)
def _require_driver():
    token = request.headers.get("Authorization")
    if not token:
        return (jsonify({"code": 401, "message": "Token缺失"}), 401), None

    check_result = check_token(token)
    if check_result:
        return check_result, None

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    current_user = payload.get("username")
    if not current_user:
        return (jsonify({"code": 401, "message": "Token异常"}), 401), None

    user = User.query.filter_by(username=current_user).first()
    if not user:
        return (jsonify({"code": 401, "message": "用户不存在"}), 401), None
    if user.usertype != 2:
        return (
            jsonify({"code": 403, "message": "钱包功能仅向司机用户开放"}),
            403,
        ), None

    return None, current_user


def _wallet_to_dict(wallet) -> dict:
    return {
        "driver_username": wallet.driver_username,
        "balance": float(wallet.balance or 0),
        "total_income": float(wallet.total_income or 0),
        "total_withdraw": float(wallet.total_withdraw or 0),
        "updated_at": wallet.updated_at.isoformat() if wallet.updated_at else None,
    }


def _txn_to_dict(txn: WalletTransaction) -> dict:
    return {
        "transaction_id": txn.transaction_id,
        "driver_username": txn.driver_username,
        "payment_id": txn.payment_id,
        "order_id": txn.order_id,
        "amount": float(txn.amount or 0),
        "type": txn.type,
        "status": txn.status,
        "remark": txn.remark,
        "alipay_trade_no": txn.alipay_trade_no,
        "created_at": txn.created_at.isoformat() if txn.created_at else None,
        "updated_at": txn.updated_at.isoformat() if txn.updated_at else None,
    }


# ------------ 路由 ------------

# 余额 + 累计收入 + 累计提现
@wallet_bp.route("/api/wallet/summary", methods=["GET"])
def wallet_summary():
    err, current_user = _require_driver()
    if err:
        return err

    wallet = get_wallet_for_driver(current_user)
    db.session.commit()
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": _wallet_to_dict(wallet),
    })


# 收入流水
@wallet_bp.route("/api/wallet/income-records", methods=["GET"])
def wallet_income_records():
    err, current_user = _require_driver()
    if err:
        return err

    records = (
        WalletTransaction.query
        .filter_by(driver_username=current_user, type="INCOME")
        .order_by(WalletTransaction.created_at.desc())
        .all()
    )
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {"list": [_txn_to_dict(r) for r in records]},
    })


# 提现流水
@wallet_bp.route("/api/wallet/withdraw-records", methods=["GET"])
def wallet_withdraw_records():
    err, current_user = _require_driver()
    if err:
        return err

    records = (
        WalletTransaction.query
        .filter_by(driver_username=current_user, type="WITHDRAW")
        .order_by(WalletTransaction.created_at.desc())
        .all()
    )
    return jsonify({
        "code": 200,
        "message": "查询成功",
        "data": {"list": [_txn_to_dict(r) for r in records]},
    })


# 发起提现
@wallet_bp.route("/api/wallet/withdraw", methods=["POST"])
def wallet_withdraw():
    err, current_user = _require_driver()
    if err:
        return err

    data = request.json or {}
    amount = data.get("amount")
    payee_account = data.get("payee_account") or data.get("alipay_account")
    # amount 缺省时表示提现全部余额
    if amount is None:
        wallet_now = get_wallet_for_driver(current_user)
        amount = wallet_now.balance
        db.session.commit()

    if not payee_account or not str(payee_account).strip():
        return jsonify({"code": 400, "message": "请填写支付宝收款账号"}), 400

    try:
        ok, message, txn = perform_withdraw(current_user, amount, payee_account)
    except ValueError as exc:
        return jsonify({"code": 400, "message": str(exc)}), 400
    except Exception as exc:  # pragma: no cover - 兜底，避免把 5xx 抛给客户端
        db.session.rollback()
        return jsonify({"code": 500, "message": f"提现失败：{exc}"}), 500

    wallet = get_wallet_for_driver(current_user)
    db.session.commit()
    return jsonify({
        "code": 200 if ok else 400,
        "message": message,
        "data": {
            "transaction": _txn_to_dict(txn),
            "wallet": _wallet_to_dict(wallet),
        },
    })
