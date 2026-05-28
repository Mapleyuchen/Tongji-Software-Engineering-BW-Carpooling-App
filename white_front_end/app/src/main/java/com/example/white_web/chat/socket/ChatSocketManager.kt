package com.example.white_web.chat.socket

import android.content.Context
import kotlinx.coroutines.flow.StateFlow


object ChatSocketManager {
    @Volatile
    private var client: ChatSocketClient? = null

    fun getClient(context: Context): ChatSocketClient {
        return client ?: synchronized(this) {
            client ?: ChatSocketClient(context.applicationContext).also {
                client = it
            }
        }
    }

    fun connect(context: Context) {
        getClient(context).connect()
    }

    fun reconnectWithLatestToken(context: Context) {
        getClient(context).reconnectWithLatestToken()
    }

    fun disconnect() {
        client?.disconnect()
    }

    fun joinConversation(context: Context, conversationId: Int) {
        getClient(context).joinConversation(conversationId)
    }

    fun leaveConversation(context: Context, conversationId: Int) {
        getClient(context).leaveConversation(conversationId)
    }

    fun connectionState(context: Context): StateFlow<ChatSocketConnectionState> {
        return getClient(context).connectionState
    }
}
