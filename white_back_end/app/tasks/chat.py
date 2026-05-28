from app.celery_app import celery
from app.extensions import db
from app.models import Conversation
from app.utils.chat import (
    CONVERSATION_STATUS_OPEN,
    close_conversation_now,
)
from app.utils.chat_events import (
    emit_conversation_closed,
    emit_conversation_updated,
    emit_message_new,
)


def _emit_closed_events(conversation, message):
    if message:
        emit_message_new(message, conversation)
    emit_conversation_closed(conversation)
    emit_conversation_updated(
        conversation,
        reason="conversation_closed",
        message=message,
    )


@celery.task(bind=True, name="chat.close_conversation")
def close_conversation_task(self, conversation_id):
    try:
        conversation, message, closed_now = close_conversation_now(conversation_id)
        db.session.commit()

        if closed_now and conversation:
            _emit_closed_events(conversation, message)

        return {
            "conversation_id": conversation_id,
            "closed_now": closed_now,
            "message_id": message.message_id if message else None,
        }
    except Exception as exc:
        db.session.rollback()
        raise self.retry(exc=exc, countdown=10, max_retries=3)


@celery.task(bind=True, name="chat.scan_due_conversations")
def scan_due_conversations_task(self):
    try:
        due_conversation_ids = [
            row[0]
            for row in db.session.query(Conversation.conversation_id)
            .filter(
                Conversation.status == CONVERSATION_STATUS_OPEN,
                Conversation.close_at.isnot(None),
                Conversation.close_at <= db.func.now(),
            )
            .all()
        ]

        closed_count = 0
        for conversation_id in due_conversation_ids:
            try:
                conversation, message, closed_now = close_conversation_now(conversation_id)
                db.session.commit()
                if closed_now and conversation:
                    closed_count += 1
                    _emit_closed_events(conversation, message)
            except Exception:
                db.session.rollback()

        return {
            "scanned_count": len(due_conversation_ids),
            "closed_count": closed_count,
        }
    except Exception as exc:
        db.session.rollback()
        raise self.retry(exc=exc, countdown=30, max_retries=3)
