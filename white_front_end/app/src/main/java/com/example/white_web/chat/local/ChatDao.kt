package com.example.white_web.chat.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


@Dao
interface ChatDao {
    @Query(
        "SELECT * FROM local_conversation " +
            "WHERE ownerUsername = :ownerUsername AND hiddenAt IS NULL " +
            "ORDER BY lastMessageTime DESC"
    )
    fun observeVisibleConversations(ownerUsername: String): Flow<List<LocalConversationEntity>>

    @Transaction
    @Query(
        "SELECT * FROM local_conversation " +
            "WHERE ownerUsername = :ownerUsername AND hiddenAt IS NULL " +
            "ORDER BY lastMessageTime DESC"
    )
    fun observeVisibleConversationRows(ownerUsername: String): Flow<List<LocalConversationWithOrder>>

    @Query(
        "SELECT * FROM local_conversation " +
            "WHERE ownerUsername = :ownerUsername AND conversationId = :conversationId LIMIT 1"
    )
    fun observeConversation(ownerUsername: String, conversationId: Int): Flow<LocalConversationEntity?>

    @Query(
        "SELECT * FROM local_message " +
            "WHERE conversationId = :conversationId " +
            "AND ((seq IS NOT NULL AND seq > :clearBeforeSeq) OR seq IS NULL) " +
            "ORDER BY CASE WHEN seq IS NULL THEN 1 ELSE 0 END ASC, seq ASC, localCreatedAt ASC"
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

    @Query(
        "SELECT * FROM local_conversation " +
            "WHERE ownerUsername = :ownerUsername AND conversationId = :conversationId LIMIT 1"
    )
    suspend fun getConversation(ownerUsername: String, conversationId: Int): LocalConversationEntity?

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

    @Query(
        "UPDATE local_conversation SET lastReadSeq = :lastReadSeq, unreadCount = 0 " +
            "WHERE ownerUsername = :ownerUsername AND conversationId = :conversationId"
    )
    suspend fun updateLastReadSeq(ownerUsername: String, conversationId: Int, lastReadSeq: Int)

    @Query(
        "UPDATE local_conversation SET clearBeforeSeq = :clearBeforeSeq, " +
            "lastReadSeq = :lastReadSeq, unreadCount = 0, " +
            "lastMessageContent = NULL, lastMessageTime = NULL " +
            "WHERE ownerUsername = :ownerUsername AND conversationId = :conversationId"
    )
    suspend fun updateClearBeforeSeq(
        ownerUsername: String,
        conversationId: Int,
        clearBeforeSeq: Int,
        lastReadSeq: Int
    )

    @Query(
        "UPDATE local_conversation SET hiddenAt = :hiddenAt " +
            "WHERE ownerUsername = :ownerUsername AND conversationId = :conversationId"
    )
    suspend fun updateHiddenAt(ownerUsername: String, conversationId: Int, hiddenAt: String)

    @Query(
        "DELETE FROM local_message " +
            "WHERE conversationId = :conversationId AND seq IS NOT NULL AND seq <= :clearBeforeSeq"
    )
    suspend fun deleteMessagesBefore(conversationId: Int, clearBeforeSeq: Int)

    @Query("DELETE FROM local_message WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: Int)

    @Query(
        "DELETE FROM local_message " +
            "WHERE conversationId = :conversationId " +
            "AND seq IS NULL " +
            "AND sendStatus != :sentStatus"
    )
    suspend fun deleteUnconfirmedMessages(
        conversationId: Int,
        sentStatus: Int
    )

    @Query("DELETE FROM local_conversation WHERE ownerUsername = :ownerUsername AND conversationId = :conversationId")
    suspend fun deleteConversation(ownerUsername: String, conversationId: Int)

    @Query("SELECT COUNT(*) FROM local_conversation WHERE conversationId = :conversationId")
    suspend fun countConversationOwners(conversationId: Int): Int

    @Query(
        "SELECT conversationId FROM local_conversation " +
            "WHERE ownerUsername = :ownerUsername AND hiddenAt IS NULL"
    )
    suspend fun getVisibleConversationIds(ownerUsername: String): List<Int>

    @Query("DELETE FROM local_order_chat_cache WHERE conversationId = :conversationId")
    suspend fun deleteOrderCacheByConversation(conversationId: Int)

    @Transaction
    suspend fun deleteConversationCache(ownerUsername: String, conversationId: Int) {
        deleteConversation(ownerUsername, conversationId)
        if (countConversationOwners(conversationId) == 0) {
            deleteMessages(conversationId)
            deleteOrderCacheByConversation(conversationId)
        }
    }

    @Transaction
    suspend fun hideConversationAndDeleteCache(ownerUsername: String, conversationId: Int, hiddenAt: String) {
        updateHiddenAt(ownerUsername, conversationId, hiddenAt)
        if (countConversationOwners(conversationId) <= 1) {
            deleteMessages(conversationId)
            deleteOrderCacheByConversation(conversationId)
        }
    }
}
