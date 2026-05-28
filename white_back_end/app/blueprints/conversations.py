import datetime

import jwt
from flask import Blueprint, jsonify, request

from app.extensions import db
from app.models import Conversation, ConversationMember, Message, Order, OrderStatus
from app.utils.auth import check_token
from app.utils.chat import (
    CONVERSATION_STATUS_CLOSED,
    CONVERSATION_STATUS_OPEN,
    ChatError,
    close_conversation_now,
    is_conversation_due,
    require_member,
    send_user_text_message,
    serialize_conversation,
    serialize_message,
)
from app.utils.chat_events import (
    emit_conversation_closed,
    emit_conversation_updated,
    emit_message_new,
)


conversations_bp = Blueprint("conversations", __name__)


def _current_username_or_response():
    token = request.headers.get("Authorization")
    if not token:
        return None, (jsonify({"code": 401, "message": "Token缺失"}), 401)

    check_result = check_token(token)
    if check_result:
        return None, check_result

    payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    return payload["username"], None


def _participant_filter(username):
    return (
        (Order.user1 == username)
        | (Order.user2 == username)
        | (Order.user3 == username)
        | (Order.user4 == username)
        | (Order.driver == username)
    )


def _close_due_conversations():
    due_conversation_ids = [
        row[0]
        for row in db.session.query(Conversation.conversation_id)
        .filter(
            Conversation.status == CONVERSATION_STATUS_OPEN,
            Conversation.close_at.isnot(None),
            Conversation.close_at <= datetime.datetime.now(),
        )
        .all()
    ]
    for conversation_id in due_conversation_ids:
        conversation, message, closed_now = close_conversation_now(conversation_id)
        db.session.commit()
        if closed_now and conversation:
            _emit_closed_events(conversation, message)


def _emit_closed_events(conversation, message):
    if message:
        emit_message_new(message, conversation)
    emit_conversation_closed(conversation)
    emit_conversation_updated(
        conversation,
        reason="conversation_closed",
        message=message,
    )


def _close_due_conversation_with_notice(conversation):
    if not is_conversation_due(conversation):
        return conversation, None, False

    conversation, message, closed_now = close_conversation_now(conversation.conversation_id)
    db.session.commit()
    if closed_now and conversation:
        _emit_closed_events(conversation, message)
    return conversation, message, closed_now


def _passenger_count(order):
    return len([user for user in [order.user1, order.user2, order.user3, order.user4] if user])


def _serialize_order_chat_cache(order, order_status, conversation):
    return {
        "order_id": order.order_id,
        "conversation_id": conversation.conversation_id,
        "user1": order.user1,
        "user2": order.user2,
        "user3": order.user3,
        "user4": order.user4,
        "driver": order.driver,
        "passenger_count": _passenger_count(order),
        "has_driver": bool(order.driver),
        "departure": order.departure,
        "destination": order.destination,
        "date": order.date.isoformat() if order.date else None,
        "earliest_departure_time": (
            order.earliest_departure_time.isoformat()
            if order.earliest_departure_time
            else None
        ),
        "latest_departure_time": (
            order.latest_departure_time.isoformat()
            if order.latest_departure_time
            else None
        ),
        "remark": order.remark,
        "order_status": order_status.status if order_status else 0,
        "user1_arrived": order_status.user1_arrived if order_status else False,
        "user2_arrived": order_status.user2_arrived if order_status else False,
        "user3_arrived": order_status.user3_arrived if order_status else False,
        "user4_arrived": order_status.user4_arrived if order_status else False,
        "driver_arrived": order_status.driver_arrived if order_status else False,
        "completed_at": (
            order_status.completed_at.isoformat()
            if order_status and order_status.completed_at
            else None
        ),
    }


def _latest_visible_message(conversation_id, clear_before_seq):
    return (
        Message.query.filter(
            Message.conversation_id == conversation_id,
            Message.seq > clear_before_seq,
        )
        .order_by(Message.seq.desc())
        .first()
    )


def _unread_count(conversation_id, member):
    return Message.query.filter(
        Message.conversation_id == conversation_id,
        Message.seq > member.last_read_seq,
        Message.seq > member.clear_before_seq,
    ).count()


def _serialize_conversation_item(order, order_status, conversation, member):
    last_message = _latest_visible_message(
        conversation.conversation_id,
        member.clear_before_seq,
    )
    return {
        "conversation": serialize_conversation(conversation),
        "member": {
            "role": member.role,
            "joined_at": member.joined_at.isoformat() if member.joined_at else None,
            "last_read_seq": member.last_read_seq,
            "clear_before_seq": member.clear_before_seq,
            "hidden_at": member.hidden_at.isoformat() if member.hidden_at else None,
        },
        "order": _serialize_order_chat_cache(order, order_status, conversation),
        "last_message": serialize_message(last_message) if last_message else None,
        "last_message_content": last_message.content if last_message else None,
        "last_message_time": (
            last_message.created_at.isoformat()
            if last_message and last_message.created_at
            else None
        ),
        "unread_count": _unread_count(conversation.conversation_id, member),
    }


def _base_conversation_query(username):
    return (
        db.session.query(Order, OrderStatus, Conversation, ConversationMember)
        .join(OrderStatus, Order.order_id == OrderStatus.order_id)
        .join(Conversation, Conversation.order_id == Order.order_id)
        .join(
            ConversationMember,
            ConversationMember.conversation_id == Conversation.conversation_id,
        )
        .filter(ConversationMember.username == username)
        .filter(ConversationMember.hidden_at.is_(None))
        .filter(_participant_filter(username))
    )


def _json_success(data, message="查询成功"):
    return jsonify({"code": 200, "message": message, "data": data})


@conversations_bp.route("/api/chat/conversations/current", methods=["GET"])
def get_current_chat_conversation():
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    _close_due_conversations()
    now = datetime.datetime.now()

    candidates = (
        _base_conversation_query(username)
        .filter(
            OrderStatus.status == 2,
            Conversation.status == CONVERSATION_STATUS_OPEN,
        )
        .order_by(OrderStatus.completed_at.desc())
        .all()
    )
    if candidates:
        order, order_status, conversation, member = candidates[0]
        return _json_success(
            {"conversation": _serialize_conversation_item(order, order_status, conversation, member)}
        )

    candidates = (
        _base_conversation_query(username)
        .filter(OrderStatus.status == 1)
        .order_by(Order.date.asc(), Order.earliest_departure_time.asc())
        .all()
    )
    if candidates:
        order, order_status, conversation, member = candidates[0]
        return _json_success(
            {"conversation": _serialize_conversation_item(order, order_status, conversation, member)}
        )

    candidates = (
        _base_conversation_query(username)
        .filter(OrderStatus.status == 0)
        .order_by(Order.date.asc(), Order.earliest_departure_time.asc())
        .all()
    )
    if candidates:
        order, order_status, conversation, member = min(
            candidates,
            key=lambda row: abs(
                (
                    datetime.datetime.combine(row[0].date, row[0].earliest_departure_time)
                    - now
                ).total_seconds()
            ),
        )
        return _json_success(
            {"conversation": _serialize_conversation_item(order, order_status, conversation, member)}
        )

    return _json_success({"conversation": None})


@conversations_bp.route("/api/chat/conversations/not-started", methods=["GET"])
def get_not_started_chat_conversations():
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    _close_due_conversations()
    rows = (
        _base_conversation_query(username)
        .filter(OrderStatus.status == 0)
        .order_by(Order.date.asc(), Order.earliest_departure_time.asc())
        .all()
    )
    return _json_success(
        {
            "list": [
                _serialize_conversation_item(order, order_status, conversation, member)
                for order, order_status, conversation, member in rows
            ]
        }
    )


@conversations_bp.route("/api/chat/conversations/history", methods=["GET"])
def get_history_chat_conversations():
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    _close_due_conversations()
    rows = (
        _base_conversation_query(username)
        .filter(
            OrderStatus.status == 2,
            Conversation.status == CONVERSATION_STATUS_CLOSED,
        )
        .order_by(OrderStatus.completed_at.desc())
        .all()
    )
    return _json_success(
        {
            "list": [
                _serialize_conversation_item(order, order_status, conversation, member)
                for order, order_status, conversation, member in rows
            ]
        }
    )


@conversations_bp.route("/api/chat/conversations/<int:conversation_id>", methods=["GET"])
def get_chat_conversation_detail(conversation_id):
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    row = (
        _base_conversation_query(username)
        .filter(Conversation.conversation_id == conversation_id)
        .first()
    )
    if not row:
        return jsonify({"code": 404, "message": "群聊不存在或无权访问"}), 404

    order, order_status, conversation, member = row
    conversation, _, _ = _close_due_conversation_with_notice(conversation)
    data = _serialize_conversation_item(order, order_status, conversation, member)
    return _json_success({"conversation": data})


@conversations_bp.route(
    "/api/chat/conversations/<int:conversation_id>/messages",
    methods=["GET"],
)
def get_chat_messages(conversation_id):
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    try:
        after_seq = int(request.args.get("after_seq", 0))
    except ValueError:
        return jsonify({"code": 400, "message": "after_seq格式错误"}), 400

    try:
        member = require_member(conversation_id, username)
        messages = (
            Message.query.filter(
                Message.conversation_id == conversation_id,
                Message.seq > after_seq,
                Message.seq > member.clear_before_seq,
            )
            .order_by(Message.seq.asc())
            .all()
        )
        return _json_success({"list": [serialize_message(message) for message in messages]})
    except ChatError as error:
        return jsonify({"code": error.code, "message": error.message}), error.status_code


@conversations_bp.route(
    "/api/chat/conversations/<int:conversation_id>/messages",
    methods=["POST"],
)
def send_chat_message(conversation_id):
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    data = request.json or {}
    try:
        message, created = send_user_text_message(
            conversation_id=conversation_id,
            sender_username=username,
            content=data.get("content"),
            client_msg_id=data.get("client_msg_id"),
        )
        conversation = Conversation.query.get(message.conversation_id)
        message_data = serialize_message(message)
        conversation_data = serialize_conversation(conversation)
        db.session.commit()
        if created:
            emit_message_new(message, conversation, created=created)
        return _json_success(
            {
                "message": message_data,
                "conversation": conversation_data,
                "created": created,
            },
            message="发送成功",
        )
    except ChatError as error:
        if error.should_commit:
            conversation = error.conversation or Conversation.query.get(conversation_id)
            close_message = error.close_message
            db.session.commit()
            if conversation:
                _emit_closed_events(conversation, close_message)
        else:
            db.session.rollback()
        return jsonify({"code": error.code, "message": error.message}), error.status_code


@conversations_bp.route(
    "/api/chat/conversations/<int:conversation_id>/read",
    methods=["POST"],
)
def mark_chat_read(conversation_id):
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    data = request.json or {}
    last_read_seq = data.get("last_read_seq")
    if last_read_seq is None:
        conversation = Conversation.query.get(conversation_id)
        if not conversation:
            return jsonify({"code": 404, "message": "群聊不存在"}), 404
        last_read_seq = conversation.last_seq

    try:
        member = require_member(conversation_id, username)
        member.last_read_seq = max(member.last_read_seq, int(last_read_seq))
        db.session.commit()
        return _json_success({"last_read_seq": member.last_read_seq}, message="已读位置更新成功")
    except ValueError:
        db.session.rollback()
        return jsonify({"code": 400, "message": "last_read_seq格式错误"}), 400
    except ChatError as error:
        db.session.rollback()
        return jsonify({"code": error.code, "message": error.message}), error.status_code


@conversations_bp.route(
    "/api/chat/conversations/<int:conversation_id>/clear",
    methods=["POST"],
)
def clear_chat_history(conversation_id):
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    conversation = Conversation.query.get(conversation_id)
    if not conversation:
        return jsonify({"code": 404, "message": "群聊不存在"}), 404
    try:
        member = require_member(conversation_id, username)
    except ChatError as error:
        return jsonify({"code": error.code, "message": error.message}), error.status_code

    conversation, _, _ = _close_due_conversation_with_notice(conversation)
    if conversation.status == CONVERSATION_STATUS_CLOSED:
        return jsonify({"code": 400, "message": "已关闭群聊请使用删除群聊"}), 400

    try:
        member.clear_before_seq = conversation.last_seq
        member.last_read_seq = max(member.last_read_seq, conversation.last_seq)
        db.session.commit()
        return _json_success(
            {
                "clear_before_seq": member.clear_before_seq,
                "last_read_seq": member.last_read_seq,
            },
            message="聊天历史已清空",
        )
    except ChatError as error:
        db.session.rollback()
        return jsonify({"code": error.code, "message": error.message}), error.status_code


@conversations_bp.route(
    "/api/chat/conversations/<int:conversation_id>/hide",
    methods=["POST"],
)
def hide_closed_chat_conversation(conversation_id):
    username, error_response = _current_username_or_response()
    if error_response:
        return error_response

    conversation = Conversation.query.get(conversation_id)
    if not conversation:
        return jsonify({"code": 404, "message": "群聊不存在"}), 404
    try:
        member = require_member(conversation_id, username)
    except ChatError as error:
        return jsonify({"code": error.code, "message": error.message}), error.status_code

    conversation, _, _ = _close_due_conversation_with_notice(conversation)
    if conversation.status != CONVERSATION_STATUS_CLOSED:
        db.session.rollback()
        return jsonify({"code": 400, "message": "只能删除已关闭群聊"}), 400

    try:
        member.hidden_at = datetime.datetime.now()
        db.session.commit()
        return _json_success(
            {"hidden_at": member.hidden_at.isoformat()},
            message="群聊已从列表删除",
        )
    except ChatError as error:
        db.session.rollback()
        return jsonify({"code": error.code, "message": error.message}), error.status_code
