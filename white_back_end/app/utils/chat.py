import datetime

from app.extensions import db
from app.models import Conversation, ConversationMember, Message


ROLE_PASSENGER = 1
ROLE_DRIVER = 2

MESSAGE_TYPE_USER_TEXT = 1
MESSAGE_TYPE_SYSTEM_NOTICE = 2

CONVERSATION_STATUS_OPEN = 0
CONVERSATION_STATUS_CLOSED = 1


class ChatError(Exception):
    def __init__(self, message, status_code=400, code=400, should_commit=False):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.code = code
        self.should_commit = should_commit


def get_passenger_usernames(order):
    return [
        username
        for username in [order.user1, order.user2, order.user3, order.user4]
        if username
    ]


def has_any_participant(order):
    return bool(get_passenger_usernames(order) or order.driver)


def ensure_conversation_for_order(order):
    conversation = get_order_conversation_for_update(order.order_id)
    if conversation:
        return conversation

    conversation = Conversation(order_id=order.order_id)
    db.session.add(conversation)
    db.session.flush()

    for username in get_passenger_usernames(order):
        add_member(conversation, username, ROLE_PASSENGER)

    if order.driver:
        add_member(conversation, order.driver, ROLE_DRIVER)

    append_system_message(conversation, "拼车群聊已创建")
    return conversation


def create_conversation_for_new_order(order, creator_username):
    conversation = Conversation(order_id=order.order_id)
    db.session.add(conversation)
    db.session.flush()

    add_member(conversation, creator_username, ROLE_PASSENGER)
    append_system_message(conversation, f"{creator_username}已发布拼车需求")
    return conversation


def get_order_conversation_for_update(order_id):
    return (
        Conversation.query.filter_by(order_id=order_id)
        .with_for_update()
        .one_or_none()
    )


def get_conversation_for_update(conversation_id):
    return (
        Conversation.query.filter_by(conversation_id=conversation_id)
        .with_for_update()
        .one_or_none()
    )


def get_member(conversation_id, username):
    return ConversationMember.query.get((conversation_id, username))


def require_member(conversation_id, username):
    member = get_member(conversation_id, username)
    if not member:
        raise ChatError("您不是该群聊成员", status_code=403, code=403)
    return member


def add_member(conversation, username, role):
    existing_member = get_member(conversation.conversation_id, username)
    if existing_member:
        existing_member.role = role
        existing_member.hidden_at = None
        return existing_member

    member = ConversationMember(
        conversation_id=conversation.conversation_id,
        username=username,
        role=role,
    )
    db.session.add(member)
    return member


def add_member_to_order_conversation(order, username, role):
    conversation = ensure_conversation_for_order(order)
    return add_member(conversation, username, role)


def remove_member_from_order_conversation(order_id, username):
    conversation = get_order_conversation_for_update(order_id)
    if not conversation:
        return None

    member = get_member(conversation.conversation_id, username)
    if member:
        db.session.delete(member)
    return conversation


def append_system_message_for_order(order_id, content):
    conversation = get_order_conversation_for_update(order_id)
    if not conversation:
        return None
    return append_system_message(conversation, content)


def append_system_message(conversation, content):
    return append_message(
        conversation=conversation,
        message_type=MESSAGE_TYPE_SYSTEM_NOTICE,
        content=content,
        sender_username=None,
        client_msg_id=None,
    )


def send_user_text_message(conversation_id, sender_username, content, client_msg_id):
    if not client_msg_id:
        raise ChatError("client_msg_id缺失")

    content = (content or "").strip()
    if not content:
        raise ChatError("消息内容不能为空")
    if len(content) > 500:
        raise ChatError("消息内容不能超过500个字符")

    conversation = get_conversation_for_update(conversation_id)
    if not conversation:
        raise ChatError("群聊不存在", status_code=404, code=404)

    require_member(conversation.conversation_id, sender_username)
    existing_message = Message.query.filter_by(
        conversation_id=conversation.conversation_id,
        sender_username=sender_username,
        client_msg_id=client_msg_id,
    ).one_or_none()
    if existing_message:
        return existing_message, False

    closed_now = close_conversation_if_due(conversation)
    ensure_conversation_can_send(conversation, should_commit=closed_now)

    message = append_message(
        conversation=conversation,
        message_type=MESSAGE_TYPE_USER_TEXT,
        content=content,
        sender_username=sender_username,
        client_msg_id=client_msg_id,
    )
    return message, True


def append_message(conversation, message_type, content, sender_username=None, client_msg_id=None):
    seq = conversation.next_seq
    message = Message(
        conversation_id=conversation.conversation_id,
        seq=seq,
        sender_username=sender_username,
        message_type=message_type,
        content=content,
        client_msg_id=client_msg_id,
    )
    db.session.add(message)
    db.session.flush()

    conversation.last_seq = seq
    conversation.next_seq = seq + 1
    return message


def close_conversation_if_due(conversation, now=None):
    if conversation.status == CONVERSATION_STATUS_CLOSED:
        return False
    if not conversation.close_at:
        return False

    now = now or datetime.datetime.now()
    if conversation.close_at > now:
        return False

    conversation.status = CONVERSATION_STATUS_CLOSED
    return True


def ensure_conversation_can_send(conversation, should_commit=False):
    if conversation.status == CONVERSATION_STATUS_CLOSED:
        raise ChatError(
            "群聊已关闭，无法发送消息",
            status_code=403,
            code=403,
            should_commit=should_commit,
        )


def schedule_conversation_close(order_id, completed_at):
    conversation = get_order_conversation_for_update(order_id)
    if not conversation:
        return None

    close_at = completed_at + datetime.timedelta(minutes=10)
    conversation.status = CONVERSATION_STATUS_OPEN
    conversation.close_at = close_at
    append_system_message(conversation, "订单已完成，群聊将在10分钟后关闭")
    return conversation


def serialize_message(message):
    return {
        "message_id": message.message_id,
        "conversation_id": message.conversation_id,
        "seq": message.seq,
        "sender_username": message.sender_username,
        "message_type": message.message_type,
        "content": message.content,
        "client_msg_id": message.client_msg_id,
        "created_at": message.created_at.isoformat() if message.created_at else None,
    }


def serialize_conversation(conversation):
    return {
        "conversation_id": conversation.conversation_id,
        "order_id": conversation.order_id,
        "status": conversation.status,
        "next_seq": conversation.next_seq,
        "last_seq": conversation.last_seq,
        "created_at": conversation.created_at.isoformat() if conversation.created_at else None,
        "close_at": conversation.close_at.isoformat() if conversation.close_at else None,
    }
