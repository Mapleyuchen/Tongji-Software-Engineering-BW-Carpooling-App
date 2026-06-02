package com.example.white_web.chat.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded


@Entity(
    tableName = "local_conversation",
    primaryKeys = ["ownerUsername", "conversationId"],
    indices = [Index(value = ["conversationId"])]
)
data class LocalConversationEntity(
    val ownerUsername: String,
    val conversationId: Int,
    val orderId: Int,
    val status: Int,
    val lastSeq: Int,
    val lastReadSeq: Int,
    val clearBeforeSeq: Int,
    val hiddenAt: String?,
    val lastMessageContent: String?,
    val lastMessageTime: String?,
    val unreadCount: Int,
    val closeAt: String?,
    val updatedAt: Long
)


@Entity(
    tableName = "local_message",
    indices = [
        Index(value = ["conversationId", "seq"], unique = true),
        Index(value = ["conversationId", "clientMsgId"], unique = true)
    ]
)
data class LocalMessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverMessageId: Long?,
    val conversationId: Int,
    val seq: Int?,
    val senderUsername: String?,
    val messageType: Int,
    val content: String,
    val clientMsgId: String?,
    val sendStatus: Int,
    val createdAt: String?,
    val localCreatedAt: Long
)


@Entity(tableName = "local_order_chat_cache")
data class LocalOrderChatCacheEntity(
    @PrimaryKey val orderId: Int,
    val conversationId: Int,
    val user1: String?,
    val user2: String?,
    val user3: String?,
    val user4: String?,
    val driver: String?,
    val passengerCount: Int,
    val hasDriver: Boolean,
    val departure: String,
    val destination: String,
    val date: String?,
    val earliestDepartureTime: String?,
    val latestDepartureTime: String?,
    val remark: String?,
    val orderStatus: Int,
    val user1Arrived: Boolean,
    val user2Arrived: Boolean,
    val user3Arrived: Boolean,
    val user4Arrived: Boolean,
    val driverArrived: Boolean,
    val completedAt: String?,
    val updatedAt: Long
)


data class LocalConversationWithOrder(
    @Embedded val conversation: LocalConversationEntity,
    @Relation(
        parentColumn = "orderId",
        entityColumn = "orderId"
    )
    val order: LocalOrderChatCacheEntity?,
    @Relation(
        parentColumn = "conversationId",
        entityColumn = "conversationId"
    )
    val messages: List<LocalMessageEntity>
)
