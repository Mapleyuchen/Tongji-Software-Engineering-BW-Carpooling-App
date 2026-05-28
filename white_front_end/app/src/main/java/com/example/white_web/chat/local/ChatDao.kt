package com.example.white_web.chat.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


@Dao
interface ChatDao {
    @Query("SELECT * FROM local_conversation WHERE hiddenAt IS NULL ORDER BY lastMessageTime DESC")
    fun observeVisibleConversations(): Flow<List<LocalConversationEntity>>

    @Query("SELECT * FROM local_conversation WHERE conversationId = :conversationId LIMIT 1")
    fun observeConversation(conversationId: Int): Flow<LocalConversationEntity?>

    @Query(
        "SELECT * FROM local_message " +
            "WHERE conversationId = :conversationId AND seq > :clearBeforeSeq " +
            "ORDER BY seq ASC, localCreatedAt ASC"
    )
    fun observeMessages(
        conversationId: Int,
        clearBeforeSeq: Int = 0
    ): Flow<List<LocalMessageEntity>>

    @Query("SELECT * FROM local_order_chat_cache WHERE conversationId = :conversationId LIMIT 1")
    fun observeOrderCache(conversationId: Int): Flow<LocalOrderChatCacheEntity?>

    @Query("SELECT COALESCE(MAX(seq), 0) FROM local_message WHERE conversationId = :conversationId")
    suspend fun getMaxSeq(conversationId: Int): Int

    @Query("SELECT * FROM local_message WHERE conversationId = :conversationId AND seq = :seq LIMIT 1")
    suspend fun getMessageBySeq(conversationId: Int, seq: Int): LocalMessageEntity?

    @Query("SELECT * FROM local_conversation WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getConversation(conversationId: Int): LocalConversationEntity?

    @Query("SELECT * FROM local_message WHERE conversationId = :conversationId AND clientMsgId = :clientMsgId LIMIT 1")
    suspend fun getMessageByClientMsgId(conversationId: Int, clientMsgId: String): LocalMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: LocalConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrderCache(orderCache: LocalOrderChatCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LocalMessageEntity): Long

    @Query(
        "UPDATE local_message SET serverMessageId = :serverMessageId, seq = :seq, " +
            "sendStatus = :sendStatus, createdAt = :createdAt " +
            "WHERE conversationId = :conversationId AND clientMsgId = :clientMsgId"
    )
    suspend fun updatePendingMessage(
        conversationId: Int,
        clientMsgId: String,
        serverMessageId: Long,
        seq: Int,
        sendStatus: Int,
        createdAt: String?
    ): Int

    @Query(
        "UPDATE local_message SET sendStatus = :sendStatus " +
            "WHERE conversationId = :conversationId AND clientMsgId = :clientMsgId"
    )
    suspend fun updateSendStatus(conversationId: Int, clientMsgId: String, sendStatus: Int)

    @Query("UPDATE local_conversation SET lastReadSeq = :lastReadSeq, unreadCount = 0 WHERE conversationId = :conversationId")
    suspend fun updateLastReadSeq(conversationId: Int, lastReadSeq: Int)

    @Query(
        "UPDATE local_conversation SET clearBeforeSeq = :clearBeforeSeq, " +
            "lastReadSeq = :lastReadSeq, unreadCount = 0 WHERE conversationId = :conversationId"
    )
    suspend fun updateClearBeforeSeq(conversationId: Int, clearBeforeSeq: Int, lastReadSeq: Int)

    @Query("UPDATE local_conversation SET hiddenAt = :hiddenAt WHERE conversationId = :conversationId")
    suspend fun updateHiddenAt(conversationId: Int, hiddenAt: String)

    @Query("DELETE FROM local_message WHERE conversationId = :conversationId AND seq <= :clearBeforeSeq")
    suspend fun deleteMessagesBefore(conversationId: Int, clearBeforeSeq: Int)

    @Query("DELETE FROM local_message WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: Int)

    @Query("DELETE FROM local_conversation WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: Int)

    @Query("DELETE FROM local_order_chat_cache WHERE conversationId = :conversationId")
    suspend fun deleteOrderCacheByConversation(conversationId: Int)

    @Transaction
    suspend fun deleteConversationCache(conversationId: Int) {
        deleteMessages(conversationId)
        deleteOrderCacheByConversation(conversationId)
        deleteConversation(conversationId)
    }

    @Transaction
    suspend fun hideConversationAndDeleteCache(conversationId: Int, hiddenAt: String) {
        updateHiddenAt(conversationId, hiddenAt)
        deleteMessages(conversationId)
        deleteOrderCacheByConversation(conversationId)
    }
}
