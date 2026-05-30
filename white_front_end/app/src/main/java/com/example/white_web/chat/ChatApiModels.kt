package com.example.white_web.chat

import com.google.gson.annotations.SerializedName


data class ChatConversationResponse(
    val code: Int,
    val message: String,
    val data: ChatConversationData?
)

data class ChatConversationData(
    val conversation: ChatConversationItem?
)

data class ChatConversationListResponse(
    val code: Int,
    val message: String,
    val data: ChatConversationListData?
)

data class ChatConversationListData(
    val list: List<ChatConversationItem>
)

data class ChatConversationItem(
    val conversation: ChatConversationDto,
    val member: ChatMemberDto,
    val order: ChatOrderDto,
    @SerializedName("last_message")
    val lastMessage: ChatMessageDto?,
    @SerializedName("last_message_content")
    val lastMessageContent: String?,
    @SerializedName("last_message_time")
    val lastMessageTime: String?,
    @SerializedName("unread_count")
    val unreadCount: Int
)

data class ChatConversationDto(
    @SerializedName("conversation_id")
    val conversationId: Int,
    @SerializedName("order_id")
    val orderId: Int,
    val status: Int,
    @SerializedName("next_seq")
    val nextSeq: Int,
    @SerializedName("last_seq")
    val lastSeq: Int,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("close_at")
    val closeAt: String?
)

data class ChatMemberDto(
    val role: Int,
    @SerializedName("joined_at")
    val joinedAt: String?,
    @SerializedName("last_read_seq")
    val lastReadSeq: Int,
    @SerializedName("clear_before_seq")
    val clearBeforeSeq: Int,
    @SerializedName("hidden_at")
    val hiddenAt: String?
)

data class ChatOrderDto(
    @SerializedName("order_id")
    val orderId: Int,
    @SerializedName("conversation_id")
    val conversationId: Int,
    val user1: String?,
    val user2: String?,
    val user3: String?,
    val user4: String?,
    val driver: String?,
    @SerializedName("passenger_count")
    val passengerCount: Int,
    @SerializedName("has_driver")
    val hasDriver: Boolean,
    val departure: String,
    val destination: String,
    val date: String?,
    @SerializedName("earliest_departure_time")
    val earliestDepartureTime: String?,
    @SerializedName("latest_departure_time")
    val latestDepartureTime: String?,
    val remark: String?,
    @SerializedName("order_status")
    val orderStatus: Int,
    @SerializedName("user1_arrived")
    val user1Arrived: Boolean,
    @SerializedName("user2_arrived")
    val user2Arrived: Boolean,
    @SerializedName("user3_arrived")
    val user3Arrived: Boolean,
    @SerializedName("user4_arrived")
    val user4Arrived: Boolean,
    @SerializedName("driver_arrived")
    val driverArrived: Boolean,
    @SerializedName("completed_at")
    val completedAt: String?
)

data class ChatMessageDto(
    @SerializedName("message_id")
    val messageId: Long,
    @SerializedName("conversation_id")
    val conversationId: Int,
    val seq: Int,
    @SerializedName("sender_username")
    val senderUsername: String?,
    @SerializedName("message_type")
    val messageType: Int,
    val content: String,
    @SerializedName("client_msg_id")
    val clientMsgId: String?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class ChatMessageListResponse(
    val code: Int,
    val message: String,
    val data: ChatMessageListData?
)

data class ChatMessageListData(
    val list: List<ChatMessageDto>
)

data class SendChatMessageRequest(
    val content: String,
    @SerializedName("client_msg_id")
    val clientMsgId: String
)

data class SendChatMessageResponse(
    val code: Int,
    val message: String,
    val data: SendChatMessageData?
)

data class SendChatMessageData(
    val message: ChatMessageDto,
    val conversation: ChatConversationDto,
    val created: Boolean
)

data class MarkChatReadRequest(
    @SerializedName("last_read_seq")
    val lastReadSeq: Int? = null
)

data class MarkChatReadResponse(
    val code: Int,
    val message: String,
    val data: MarkChatReadData?
)

data class MarkChatReadData(
    @SerializedName("last_read_seq")
    val lastReadSeq: Int
)

data class ClearChatHistoryResponse(
    val code: Int,
    val message: String,
    val data: ClearChatHistoryData?
)

data class ClearChatHistoryData(
    @SerializedName("clear_before_seq")
    val clearBeforeSeq: Int,
    @SerializedName("last_read_seq")
    val lastReadSeq: Int
)

data class HideChatConversationResponse(
    val code: Int,
    val message: String,
    val data: HideChatConversationData?
)

data class HideChatConversationData(
    @SerializedName("hidden_at")
    val hiddenAt: String
)

data class HideClosedChatConversationsResponse(
    val code: Int,
    val message: String,
    val data: HideClosedChatConversationsData?
)

data class HideClosedChatConversationsData(
    @SerializedName("hidden_at")
    val hiddenAt: String,
    @SerializedName("hidden_count")
    val hiddenCount: Int,
    @SerializedName("conversation_ids")
    val conversationIds: List<Int>
)

data class ChatMemberPhoneResponse(
    val code: Int,
    val message: String,
    val data: ChatMemberPhoneData?
)

data class ChatMemberPhoneData(
    val username: String,
    val phonenumber: String
)
