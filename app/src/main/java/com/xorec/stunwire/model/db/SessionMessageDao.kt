package com.xorec.stunwire.model.db

import androidx.paging.PagingSource
import androidx.room.*

@Dao
interface SessionMessageDao {
    @Query("SELECT * FROM sessionMessage")
    fun getSessionMessagesPaged(): PagingSource<Int, SessionMessage>

    @Query("SELECT * FROM sessionMessage WHERE isSent = :isSent AND messageCode = :messageCode")
    fun getSessionMessage(isSent: Boolean, messageCode: Int): SessionMessage

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(sessionMessage: SessionMessage)

    @Query("UPDATE sessionMessage SET status = 1 WHERE isSent = 1 AND messageCode = :messageCode AND status != 2")
    fun setMessageDelivered(messageCode: Int)

    @Query("UPDATE sessionMessage SET status = 2 WHERE isSent = 1 AND messageCode = :messageCode")
    fun setMessageRead(messageCode: Int)

    @Query("UPDATE sessionMessage SET isReadSent = 1 WHERE isSent = 0 AND messageCode = :messageCode")
    fun setReadSent(messageCode: Int)

    @Query("DELETE FROM sessionMessage WHERE isSent = :isSent AND messageCode = :messageCode")
    fun delete(isSent: Boolean, messageCode: Int)

    @Query("SELECT COUNT(*) FROM sessionMessage")
    fun count(): Int
}