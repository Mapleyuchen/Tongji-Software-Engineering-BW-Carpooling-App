package com.example.white_web.chat

import android.content.Context
import com.example.white_web.APISERVICCE
import com.example.white_web.ApiService
import com.example.white_web.TOKEN
import com.example.white_web.USERNAME
import com.example.white_web.chat.local.ChatDao
import com.example.white_web.chat.local.ChatDatabase
import com.example.white_web.chat.local.LocalConversationEntity
import com.example.white_web.chat.local.LocalConversationWithOrder
import com.example.white_web.chat.local.LocalMessageEntity
import com.example.white_web.chat.local.LocalOrderChatCacheEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID


class ChatRepository(
    context: Context,
    private val api: ApiService = APISERVICCE,
    private val tokenProvider: () -> String? = { TOKEN },
    private val ownerProvider: () -> String = { USERNAME.orEmpty() }
) {
    private val dao: ChatDao = ChatDatabase.getInstance(context).chatDao()

    fun observeVisibleConversations(): Flow<List<LocalConversationEntity>> =
        dao.observeVisibleConversations(currentOwner())

    fun observeVisibleConversationRows(): Flow<List<LocalConversationWithOrder>> =
        dao.observeVisibleConversationRows(currentOwner())

    fun observeConversation(conversationId: Int): Flow<LocalConversationEntity?> =
        dao.observeConversation(currentOwner(), conversationId)

    fun observeMessages(
        conversationId: Int,
        clearBeforeSeq: Int = 0
    ): Flow<List<LocalMessageEntity>> = dao.observeMessages(conversationId, clearBeforeSeq)

    fun observeOrderCache(conversationId: Int): Flow<LocalOrderChatCacheEntity?> =
        dao.observeOrderCache(conversationId)

    suspend fun fetchMemberPhone(conversationId: Int, username: String): String {
        val response = api.getChatConversationMemberPhone(
            conversationId = conversationId,
            username = username,
            token = tokenProvider()
        )
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "获取手机号失败")
        }
        return body.data?.phonenumber ?: throw IllegalStateException("手机号为空")
    }

    suspend fun refreshAllConversations(): List<LocalConversationEntity> {
        val conversations = buildList {
            refreshCurrentConversation()?.let { add(it) }
            addAll(refreshNotStartedConversations())
            addAll(refreshHistoryConversations())
        }.distinctBy { it.conversationId }

        val keepIds = conversations.map { it.conversationId }.toSet()
        val ownerUsername = currentOwner()
        dao.getVisibleConversationIds(ownerUsername)
            .filterNot { it in keepIds }
            .forEach { dao.deleteConversationCache(ownerUsername, it) }

        return conversations
    }

    suspend fun refreshCurrentConversation(): LocalConversationEntity? {
        val response = api.getCurrentChatConversation(tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "获取当前聊天失败")
        }
        return body.data?.conversation?.let { cacheConversationItem(it) }
    }

    suspend fun refreshNotStartedConversations(): List<LocalConversationEntity> {
        val response = api.getNotStartedChatConversations(tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "获取未开始聊天失败")
        }
        return body.data?.list.orEmpty().map { cacheConversationItem(it) }
    }

    suspend fun refreshHistoryConversations(): List<LocalConversationEntity> {
        val response = api.getHistoryChatConversations(tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "获取历史聊天失败")
        }
        return body.data?.list.orEmpty().map { cacheConversationItem(it) }
    }

    suspend fun refreshConversationDetail(conversationId: Int): LocalConversationEntity {
        val response = api.getChatConversationDetail(conversationId, tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "获取聊天详情失败")
        }
        val item = body.data?.conversation ?: throw IllegalStateException("聊天详情为空")
        return cacheConversationItem(item)
    }

    suspend fun refreshMessages(conversationId: Int): List<LocalMessageEntity> {
        val afterSeq = dao.getMaxSeq(conversationId)
        val response = api.getChatMessages(conversationId, afterSeq, tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "获取聊天消息失败")
        }
        return body.data?.list.orEmpty().map { cacheServerMessage(it) }
    }

    suspend fun sendTextMessage(conversationId: Int, content: String): LocalMessageEntity {
        val clientMsgId = UUID.randomUUID().toString()
        createPendingTextMessage(conversationId, content, clientMsgId)
        return submitTextMessage(conversationId, content, clientMsgId)
    }

    suspend fun createPendingTextMessage(
        conversationId: Int,
        content: String,
        clientMsgId: String = UUID.randomUUID().toString()
    ): LocalMessageEntity {
        val now = System.currentTimeMillis()
        val message = LocalMessageEntity(
            serverMessageId = null,
            conversationId = conversationId,
            seq = null,
            senderUsername = USERNAME,
            messageType = MESSAGE_TYPE_USER_TEXT,
            content = content,
            clientMsgId = clientMsgId,
            sendStatus = SEND_STATUS_SENDING,
            createdAt = null,
            localCreatedAt = now
        )
        val localId = dao.insertMessage(message)
        return message.copy(localId = localId)
    }

    suspend fun submitTextMessage(
        conversationId: Int,
        content: String,
        clientMsgId: String
    ): LocalMessageEntity {
        return try {
            val response = api.sendChatMessage(
                conversationId = conversationId,
                token = tokenProvider(),
                request = SendChatMessageRequest(content = content, clientMsgId = clientMsgId)
            )
            val body = response.body()
            if (!response.isSuccessful || body?.code != 200) {
                throw IllegalStateException(body?.message ?: "发送消息失败")
            }
            val message = body.data?.message ?: throw IllegalStateException("发送消息响应为空")
            dao.updatePendingMessage(
                conversationId = conversationId,
                clientMsgId = clientMsgId,
                serverMessageId = message.messageId,
                seq = message.seq,
                sendStatus = SEND_STATUS_SENT,
                createdAt = message.createdAt
            )
            body.data.conversation.let {
                dao.upsertConversation(
                    LocalConversationEntity(
                        ownerUsername = currentOwner(),
                        conversationId = it.conversationId,
                        orderId = it.orderId,
                        status = it.status,
                        lastSeq = it.lastSeq,
                        lastReadSeq = 0,
                        clearBeforeSeq = 0,
                        hiddenAt = null,
                        lastMessageContent = message.content,
                        lastMessageTime = message.createdAt,
                        unreadCount = 0,
                        closeAt = it.closeAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            dao.getMessageBySeq(conversationId, message.seq) ?: cacheServerMessage(message)
        } catch (error: Exception) {
            dao.updateSendStatus(conversationId, clientMsgId, SEND_STATUS_FAILED)
            throw error
        }
    }

    suspend fun markMessageUnknown(conversationId: Int, clientMsgId: String) {
        dao.updateSendStatus(conversationId, clientMsgId, SEND_STATUS_UNKNOWN)
    }

    suspend fun markMessageSending(conversationId: Int, clientMsgId: String) {
        dao.updateSendStatus(conversationId, clientMsgId, SEND_STATUS_SENDING)
    }

    suspend fun closeConversationCache(conversationId: Int) {
        dao.deleteUnconfirmedMessages(
            conversationId = conversationId,
            sentStatus = SEND_STATUS_SENT
        )
        runCatching { refreshConversationDetail(conversationId) }
        runCatching { refreshMessages(conversationId) }
    }

    suspend fun markRead(conversationId: Int, lastReadSeq: Int? = null) {
        val response = api.markChatRead(
            conversationId = conversationId,
            token = tokenProvider(),
            request = MarkChatReadRequest(lastReadSeq)
        )
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "更新已读位置失败")
        }
        body.data?.let { dao.updateLastReadSeq(currentOwner(), conversationId, it.lastReadSeq) }
    }

    suspend fun clearHistory(conversationId: Int) {
        val response = api.clearChatHistory(conversationId, tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "清空聊天历史失败")
        }
        body.data?.let {
            dao.updateClearBeforeSeq(currentOwner(), conversationId, it.clearBeforeSeq, it.lastReadSeq)
            dao.deleteMessagesBefore(conversationId, it.clearBeforeSeq)
        }
    }

    suspend fun hideClosedConversation(conversationId: Int) {
        val response = api.hideChatConversation(conversationId, tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "删除已关闭群聊失败")
        }
        body.data?.let {
            dao.hideConversationAndDeleteCache(currentOwner(), conversationId, it.hiddenAt)
        }
    }

    suspend fun hideAllClosedConversations(): Int {
        val response = api.hideClosedChatConversations(tokenProvider())
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            throw IllegalStateException(body?.message ?: "删除历史群聊失败")
        }
        val data = body.data ?: return 0
        data.conversationIds.forEach { conversationId ->
            dao.hideConversationAndDeleteCache(currentOwner(), conversationId, data.hiddenAt)
        }
        return data.hiddenCount
    }

    suspend fun cacheConversationItem(item: ChatConversationItem): LocalConversationEntity {
        val now = System.currentTimeMillis()
        val conversation = LocalConversationEntity(
            ownerUsername = currentOwner(),
            conversationId = item.conversation.conversationId,
            orderId = item.conversation.orderId,
            status = item.conversation.status,
            lastSeq = item.conversation.lastSeq,
            lastReadSeq = item.member.lastReadSeq,
            clearBeforeSeq = item.member.clearBeforeSeq,
            hiddenAt = item.member.hiddenAt,
            lastMessageContent = item.lastMessageContent,
            lastMessageTime = item.lastMessageTime,
            unreadCount = item.unreadCount,
            closeAt = item.conversation.closeAt,
            updatedAt = now
        )
        dao.upsertConversation(conversation)
        dao.upsertOrderCache(item.order.toLocalOrderCache(now))
        item.lastMessage?.let { cacheServerMessage(it) }
        return conversation
    }

    suspend fun cacheServerMessage(message: ChatMessageDto): LocalMessageEntity {
        val existing = dao.getMessageBySeq(message.conversationId, message.seq)
        val entity = LocalMessageEntity(
            localId = existing?.localId ?: 0,
            serverMessageId = message.messageId,
            conversationId = message.conversationId,
            seq = message.seq,
            senderUsername = message.senderUsername,
            messageType = message.messageType,
            content = message.content,
            clientMsgId = message.clientMsgId,
            sendStatus = SEND_STATUS_SENT,
            createdAt = message.createdAt,
            localCreatedAt = existing?.localCreatedAt ?: System.currentTimeMillis()
        )
        dao.insertMessage(entity)
        return entity
    }

    suspend fun cacheSocketMessage(
        message: ChatMessageDto,
        conversation: ChatConversationDto
    ): LocalMessageEntity {
        val pending = message.clientMsgId?.let {
            dao.getMessageByClientMsgId(message.conversationId, it)
        }
        val entity = if (pending != null) {
            dao.updatePendingMessage(
                conversationId = message.conversationId,
                clientMsgId = message.clientMsgId.orEmpty(),
                serverMessageId = message.messageId,
                seq = message.seq,
                sendStatus = SEND_STATUS_SENT,
                createdAt = message.createdAt
            )
            dao.getMessageBySeq(message.conversationId, message.seq) ?: cacheServerMessage(message)
        } else {
            cacheServerMessage(message)
        }
        cacheSocketConversation(conversation, message)
        return entity
    }

    suspend fun cacheSocketConversation(
        conversation: ChatConversationDto,
        message: ChatMessageDto? = null
    ): LocalConversationEntity {
        val ownerUsername = currentOwner()
        val existing = dao.getConversation(ownerUsername, conversation.conversationId)
        val isIncomingUserMessage =
            message != null &&
                message.messageType == MESSAGE_TYPE_USER_TEXT &&
                message.senderUsername != null &&
                message.senderUsername != USERNAME &&
                message.seq > (existing?.lastReadSeq ?: 0) &&
                message.seq > (existing?.clearBeforeSeq ?: 0)

        val entity = LocalConversationEntity(
            ownerUsername = ownerUsername,
            conversationId = conversation.conversationId,
            orderId = conversation.orderId,
            status = conversation.status,
            lastSeq = maxOf(existing?.lastSeq ?: 0, conversation.lastSeq, message?.seq ?: 0),
            lastReadSeq = existing?.lastReadSeq ?: 0,
            clearBeforeSeq = existing?.clearBeforeSeq ?: 0,
            hiddenAt = existing?.hiddenAt,
            lastMessageContent = message?.content ?: existing?.lastMessageContent,
            lastMessageTime = message?.createdAt ?: existing?.lastMessageTime,
            unreadCount = if (isIncomingUserMessage) {
                (existing?.unreadCount ?: 0) + 1
            } else {
                existing?.unreadCount ?: 0
            },
            closeAt = conversation.closeAt,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertConversation(entity)
        return entity
    }

    suspend fun deleteConversationCache(conversationId: Int) {
        dao.deleteConversationCache(currentOwner(), conversationId)
    }

    private fun currentOwner(): String =
        ownerProvider().takeIf { it.isNotBlank() } ?: "未登录"

    private fun ChatOrderDto.toLocalOrderCache(updatedAt: Long): LocalOrderChatCacheEntity {
        return LocalOrderChatCacheEntity(
            orderId = orderId,
            conversationId = conversationId,
            user1 = user1,
            user2 = user2,
            user3 = user3,
            user4 = user4,
            driver = driver,
            passengerCount = passengerCount,
            hasDriver = hasDriver,
            departure = departure,
            destination = destination,
            date = date,
            earliestDepartureTime = earliestDepartureTime,
            latestDepartureTime = latestDepartureTime,
            remark = remark,
            orderStatus = orderStatus,
            user1Arrived = user1Arrived,
            user2Arrived = user2Arrived,
            user3Arrived = user3Arrived,
            user4Arrived = user4Arrived,
            driverArrived = driverArrived,
            completedAt = completedAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        const val MESSAGE_TYPE_USER_TEXT = 1
        const val MESSAGE_TYPE_SYSTEM_NOTICE = 2
        const val SEND_STATUS_SENDING = 0
        const val SEND_STATUS_SENT = 1
        const val SEND_STATUS_FAILED = 2
        const val SEND_STATUS_UNKNOWN = 3
    }
}
