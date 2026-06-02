import datetime

from sqlalchemy.dialects.mysql import TINYINT

from app.extensions import db


class Conversation(db.Model):
    __tablename__ = "conversation"

    conversation_id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    order_id = db.Column(
        db.Integer,
        db.ForeignKey("orders.order_id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
    )
    status = db.Column(TINYINT(), nullable=False, default=0, server_default=db.text("0"))
    next_seq = db.Column(db.Integer, nullable=False, default=1, server_default=db.text("1"))
    last_seq = db.Column(db.Integer, nullable=False, default=0, server_default=db.text("0"))
    created_at = db.Column(
        db.DateTime,
        nullable=False,
        default=datetime.datetime.now,
        server_default=db.text("CURRENT_TIMESTAMP"),
    )
    close_at = db.Column(db.DateTime, nullable=True)

    __table_args__ = (
        db.Index("idx_conversation_status_close", "status", "close_at"),
    )


class ConversationMember(db.Model):
    __tablename__ = "conversation_member"

    conversation_id = db.Column(
        db.Integer,
        db.ForeignKey("conversation.conversation_id", ondelete="CASCADE"),
        primary_key=True,
    )
    username = db.Column(
        db.String(20),
        db.ForeignKey("user.username", ondelete="RESTRICT", onupdate="CASCADE"),
        primary_key=True,
    )
    role = db.Column(TINYINT(), nullable=False)
    joined_at = db.Column(
        db.DateTime,
        nullable=False,
        default=datetime.datetime.now,
        server_default=db.text("CURRENT_TIMESTAMP"),
    )
    last_read_seq = db.Column(db.Integer, nullable=False, default=0, server_default=db.text("0"))
    clear_before_seq = db.Column(db.Integer, nullable=False, default=0, server_default=db.text("0"))
    hidden_at = db.Column(db.DateTime, nullable=True)
    updated_at = db.Column(
        db.DateTime,
        nullable=False,
        default=datetime.datetime.now,
        onupdate=datetime.datetime.now,
        server_default=db.text("CURRENT_TIMESTAMP"),
    )

    __table_args__ = (
        db.Index("idx_cm_username_hidden", "username", "hidden_at"),
    )


class Message(db.Model):
    __tablename__ = "message"

    message_id = db.Column(db.BigInteger, primary_key=True, autoincrement=True)
    conversation_id = db.Column(
        db.Integer,
        db.ForeignKey("conversation.conversation_id", ondelete="CASCADE"),
        nullable=False,
    )
    seq = db.Column(db.Integer, nullable=False)
    sender_username = db.Column(
        db.String(20),
        db.ForeignKey("user.username", ondelete="SET NULL", onupdate="CASCADE"),
        nullable=True,
    )
    message_type = db.Column(TINYINT(), nullable=False)
    content = db.Column(db.String(500), nullable=False)
    client_msg_id = db.Column(db.String(64), nullable=True)
    created_at = db.Column(
        db.DateTime,
        nullable=False,
        default=datetime.datetime.now,
        server_default=db.text("CURRENT_TIMESTAMP"),
    )

    __table_args__ = (
        db.UniqueConstraint("conversation_id", "seq", name="uk_msg_conversation_seq"),
        db.UniqueConstraint(
            "conversation_id",
            "sender_username",
            "client_msg_id",
            name="uk_msg_client_msg",
        ),
    )
