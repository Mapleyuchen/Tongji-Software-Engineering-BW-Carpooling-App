from app.extensions import socketio
from app.utils.chat import serialize_conversation, serialize_message


def conversation_room(conversation_id):
    return f"conversation:{conversation_id}"


def user_room(username):
    return f"user:{username}"


def emit_message_new(message, conversation, created=True):
    socketio.emit(
        "message:new",
        {
            "message": serialize_message(message),
            "conversation": serialize_conversation(conversation),
            "created": created,
        },
        to=conversation_room(conversation.conversation_id),
    )


def emit_member_changed(conversation, username, action, role=None, message=None):
    payload = {
        "conversation": serialize_conversation(conversation),
        "member": {
            "username": username,
            "role": role,
            "action": action,
        },
    }
    if message:
        payload["message"] = serialize_message(message)
    socketio.emit("member:changed", payload, to=conversation_room(conversation.conversation_id))


def emit_conversation_updated(conversation, reason=None, message=None):
    payload = {
        "conversation": serialize_conversation(conversation),
        "reason": reason,
    }
    if message:
        payload["message"] = serialize_message(message)
    socketio.emit(
        "conversation:updated",
        payload,
        to=conversation_room(conversation.conversation_id),
    )


def emit_conversation_closed(conversation):
    socketio.emit(
        "conversation:closed",
        {"conversation": serialize_conversation(conversation)},
        to=conversation_room(conversation.conversation_id),
    )


def emit_conversation_deleted(
    conversation_id,
    order_id,
    usernames=None,
    include_conversation_room=True,
):
    payload = {
        "conversation_id": conversation_id,
        "order_id": order_id,
    }
    rooms = set()
    if include_conversation_room:
        rooms.add(conversation_room(conversation_id))
    if usernames:
        rooms.update(user_room(username) for username in usernames if username)

    for room in rooms:
        socketio.emit("conversation:deleted", payload, to=room)
