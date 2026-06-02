package com.example.white_web.chat.socket

import android.content.Context
import com.example.white_web.SERVER_BASE_URL
import com.example.white_web.TOKEN
import com.example.white_web.chat.ChatConversationDto
import com.example.white_web.chat.ChatMessageDto
import com.example.white_web.chat.ChatRepository
import com.google.gson.Gson
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Collections


class ChatSocketClient(
    context: Context,
    private val repository: ChatRepository = ChatRepository(context.applicationContext),
    private val tokenProvider: () -> String? = { TOKEN },
    private val serverUrl: String = SERVER_BASE_URL
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val joinedConversationIds = Collections.synchronizedSet(mutableSetOf<Int>())

    private val _connectionState = MutableStateFlow(ChatSocketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ChatSocketConnectionState> = _connectionState

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var currentToken: String? = null

    fun connect() {
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        val existingSocket = socket
        if (existingSocket != null && currentToken == token) {
            if (!existingSocket.connected()) {
                _connectionState.value = ChatSocketConnectionState.CONNECTING
                existingSocket.connect()
            }
            return
        }

        disconnect()
        currentToken = token
        _connectionState.value = ChatSocketConnectionState.CONNECTING

        val options = IO.Options().apply {
            reconnection = true
            forceNew = true
            auth = mapOf("token" to token)
        }
        socket = IO.socket(serverUrl.trimEnd('/'), options).also { newSocket ->
            registerListeners(newSocket)
            newSocket.connect()
        }
    }

    fun disconnect() {
        socket?.let { existingSocket ->
            existingSocket.off()
            existingSocket.disconnect()
            existingSocket.close()
        }
        socket = null
        currentToken = null
        _connectionState.value = ChatSocketConnectionState.DISCONNECTED
    }

    fun joinConversation(conversationId: Int) {
        joinedConversationIds.add(conversationId)
        val payload = JSONObject().put("conversation_id", conversationId)
        socket?.emit(EVENT_JOIN_CONVERSATION, payload, Ack { args ->
            parse<ChatSocketAckResponse>(args.firstOrNull())
                ?.data
                ?.conversation
                ?.let { cacheConversation(it) }
        })
    }

    fun leaveConversation(conversationId: Int) {
        joinedConversationIds.remove(conversationId)
        val payload = JSONObject().put("conversation_id", conversationId)
        socket?.emit(EVENT_LEAVE_CONVERSATION, payload)
    }

    fun reconnectWithLatestToken() {
        currentToken = null
        connect()
    }

    private fun registerListeners(target: Socket) {
        target.on(Socket.EVENT_CONNECT) {
            _connectionState.value = ChatSocketConnectionState.CONNECTED
            joinedConversationIds.toList().forEach { joinConversation(it) }
        }
        target.on(Socket.EVENT_DISCONNECT) {
            _connectionState.value = ChatSocketConnectionState.DISCONNECTED
        }
        target.on(Socket.EVENT_CONNECT_ERROR) {
            _connectionState.value = ChatSocketConnectionState.ERROR
        }
        target.on(EVENT_MESSAGE_NEW) { args ->
            parse<ChatSocketMessagePayload>(args.firstOrNull())?.let { payload ->
                cacheMessage(payload.message, payload.conversation)
            }
        }
        target.on(EVENT_MEMBER_CHANGED) { args ->
            parse<ChatSocketMemberChangedPayload>(args.firstOrNull())?.let { payload ->
                cacheConversationEvent(payload.conversation, payload.message, refreshDetail = true)
            }
        }
        target.on(EVENT_CONVERSATION_UPDATED) { args ->
            parse<ChatSocketConversationPayload>(args.firstOrNull())?.let { payload ->
                if (
                    payload.conversation.status == CONVERSATION_STATUS_CLOSED ||
                    payload.reason == "conversation_closed"
                ) {
                    closeConversation(payload.conversation, payload.message)
                } else {
                    cacheConversationEvent(payload.conversation, payload.message, refreshDetail = true)
                }
            }
        }
        target.on(EVENT_CONVERSATION_CLOSED) { args ->
            parse<ChatSocketClosedPayload>(args.firstOrNull())?.let { payload ->
                closeConversation(payload.conversation)
            }
        }
        target.on(EVENT_CONVERSATION_DELETED) { args ->
            parse<ChatSocketDeletedPayload>(args.firstOrNull())?.let { payload ->
                scope.launch {
                    repository.deleteConversationCache(payload.conversationId)
                }
            }
        }
    }

    private fun cacheMessage(message: ChatMessageDto, conversation: ChatConversationDto) {
        scope.launch {
            repository.cacheSocketMessage(message, conversation)
        }
    }

    private fun cacheConversation(conversation: ChatConversationDto) {
        scope.launch {
            repository.cacheSocketConversation(conversation)
        }
    }

    private fun closeConversation(
        conversation: ChatConversationDto,
        message: ChatMessageDto? = null
    ) {
        scope.launch {
            if (message != null) {
                repository.cacheSocketMessage(message, conversation)
            } else {
                repository.cacheSocketConversation(conversation)
            }
            repository.closeConversationCache(conversation.conversationId)
        }
    }

    private fun cacheConversationEvent(
        conversation: ChatConversationDto,
        message: ChatMessageDto?,
        refreshDetail: Boolean
    ) {
        scope.launch {
            if (message != null) {
                repository.cacheSocketMessage(message, conversation)
            } else {
                repository.cacheSocketConversation(conversation)
            }
            if (refreshDetail) {
                runCatching { repository.refreshConversationDetail(conversation.conversationId) }
            }
        }
    }

    private inline fun <reified T> parse(value: Any?): T? {
        return when (value) {
            is JSONObject -> runCatching { gson.fromJson(value.toString(), T::class.java) }.getOrNull()
            is String -> runCatching { gson.fromJson(value, T::class.java) }.getOrNull()
            else -> null
        }
    }

    companion object {
        private const val EVENT_JOIN_CONVERSATION = "join_conversation"
        private const val EVENT_LEAVE_CONVERSATION = "leave_conversation"
        private const val EVENT_MESSAGE_NEW = "message:new"
        private const val EVENT_MEMBER_CHANGED = "member:changed"
        private const val EVENT_CONVERSATION_UPDATED = "conversation:updated"
        private const val EVENT_CONVERSATION_CLOSED = "conversation:closed"
        private const val EVENT_CONVERSATION_DELETED = "conversation:deleted"
        private const val CONVERSATION_STATUS_CLOSED = 1
    }
}
