package com.example.white_web.chat.socket

import com.example.white_web.chat.ChatConversationDto
import com.example.white_web.chat.ChatMessageDto
import com.google.gson.annotations.SerializedName


data class ChatSocketAckResponse(
    val code: Int,
    val message: String?,
    val data: ChatSocketAckData?
)

data class ChatSocketAckData(
    val conversation: ChatConversationDto?
)

data class ChatSocketMessagePayload(
    val message: ChatMessageDto,
    val conversation: ChatConversationDto,
    val created: Boolean = true
)

data class ChatSocketConversationPayload(
    val conversation: ChatConversationDto,
    val reason: String?,
    val message: ChatMessageDto?
)

data class ChatSocketClosedPayload(
    val conversation: ChatConversationDto
)

data class ChatSocketMemberChangedPayload(
    val conversation: ChatConversationDto,
    val member: ChatSocketMemberPayload?,
    val message: ChatMessageDto?
)

data class ChatSocketMemberPayload(
    val username: String?,
    val role: Int?,
    val action: String?
)

data class ChatSocketDeletedPayload(
    @SerializedName("conversation_id")
    val conversationId: Int,
    @SerializedName("order_id")
    val orderId: Int
)

enum class ChatSocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
