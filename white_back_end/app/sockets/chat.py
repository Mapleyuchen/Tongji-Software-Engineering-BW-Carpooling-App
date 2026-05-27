import jwt
from flask import request
from flask_socketio import disconnect, emit, join_room, leave_room

from app.extensions import db, socketio
from app.models import Conversation, User
from app.utils.chat import (
    ChatError,
    close_conversation_if_due,
    get_conversation_for_update,
    require_member,
    send_user_text_message,
    serialize_conversation,
    serialize_message,
)


_sid_to_username = {}


def conversation_room(conversation_id):
    return f"conversation:{conversation_id}"


def _normalize_token(token):
    if not token:
        return None
    if token.startswith("Bearer "):
        return token[7:]
    return token


def _extract_token(auth):
    if isinstance(auth, dict):
        token = auth.get("token") or auth.get("Authorization")
        if token:
            return _normalize_token(token)
    token = request.args.get("token") or request.headers.get("Authorization")
    return _normalize_token(token)


def _authenticate(auth):
    token = _extract_token(auth)
    if not token:
        return None

    try:
        payload = jwt.decode(token, "secret_key", algorithms=["HS256"])
    except jwt.PyJWTError:
        return None

    username = payload.get("username")
    if not username:
        return None

    user = User.query.filter_by(username=username).first()
    if not user:
        return None
    return username


def _current_username():
    return _sid_to_username.get(request.sid)


def _error_response(error):
    return {
        "code": error.code,
        "message": error.message,
    }


def _handle_unexpected_error():
    db.session.rollback()
    return {
        "code": 500,
        "message": "服务器内部错误",
    }


@socketio.on("connect")
def handle_connect(auth):
    username = _authenticate(auth)
    if not username:
        return False

    _sid_to_username[request.sid] = username
    emit("connected", {"code": 200, "message": "连接成功", "data": {"username": username}})


@socketio.on("disconnect")
def handle_disconnect():
    _sid_to_username.pop(request.sid, None)


@socketio.on("join_conversation")
def handle_join_conversation(data):
    username = _current_username()
    if not username:
        disconnect()
        return {"code": 401, "message": "未登录"}

    conversation_id = (data or {}).get("conversation_id")
    if not conversation_id:
        return {"code": 400, "message": "conversation_id缺失"}

    try:
        conversation = get_conversation_for_update(conversation_id)
        if not conversation:
            raise ChatError("群聊不存在", status_code=404, code=404)

        require_member(conversation.conversation_id, username)
        closed_now = close_conversation_if_due(conversation)
        conversation_data = serialize_conversation(conversation)
        db.session.commit()

        room = conversation_room(conversation.conversation_id)
        join_room(room)

        if closed_now:
            socketio.emit(
                "conversation:closed",
                {"conversation": conversation_data},
                to=room,
            )

        return {
            "code": 200,
            "message": "加入群聊成功",
            "data": {"conversation": conversation_data},
        }
    except ChatError as error:
        db.session.rollback()
        return _error_response(error)
    except Exception:
        return _handle_unexpected_error()


@socketio.on("leave_conversation")
def handle_leave_conversation(data):
    conversation_id = (data or {}).get("conversation_id")
    if not conversation_id:
        return {"code": 400, "message": "conversation_id缺失"}

    leave_room(conversation_room(conversation_id))
    return {"code": 200, "message": "离开群聊房间成功"}


@socketio.on("send_message")
def handle_send_message(data):
    username = _current_username()
    if not username:
        disconnect()
        return {"code": 401, "message": "未登录"}

    data = data or {}
    conversation_id = data.get("conversation_id")
    content = data.get("content")
    client_msg_id = data.get("client_msg_id")
    if not conversation_id:
        return {"code": 400, "message": "conversation_id缺失"}

    try:
        message, created = send_user_text_message(
            conversation_id=conversation_id,
            sender_username=username,
            content=content,
            client_msg_id=client_msg_id,
        )
        conversation = Conversation.query.get(message.conversation_id)
        message_data = serialize_message(message)
        conversation_data = serialize_conversation(conversation)
        db.session.commit()

        payload = {
            "message": message_data,
            "conversation": conversation_data,
            "created": created,
        }
        if created:
            socketio.emit("message:new", payload, to=conversation_room(conversation_id))
        return {"code": 200, "message": "发送成功", "data": payload}
    except ChatError as error:
        conversation_data = None
        if error.should_commit:
            conversation = Conversation.query.get(conversation_id)
            conversation_data = serialize_conversation(conversation) if conversation else None
            db.session.commit()
            if conversation_data:
                socketio.emit(
                    "conversation:closed",
                    {"conversation": conversation_data},
                    to=conversation_room(conversation_id),
                )
        else:
            db.session.rollback()
        response = _error_response(error)
        if conversation_data:
            response["data"] = {"conversation": conversation_data}
        return response
    except Exception:
        return _handle_unexpected_error()
