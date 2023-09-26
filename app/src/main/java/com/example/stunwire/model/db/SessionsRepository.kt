package com.example.stunwire.model.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionsRepository private constructor(private val context: Context, private val scope: CoroutineScope) {
    companion object {
        @Volatile
        private var instance: SessionsRepository? = null

        fun getInstance(context: Context, scope: CoroutineScope) =
            instance ?: synchronized(this) {
                instance ?: SessionsRepository(context, scope).also { instance = it }
            }
    }

    private var dbName: String? = null
    private var db: SessionDatabase? = null

    fun openSessionDatabase(databaseName: String) {
        val db = Room.databaseBuilder(context, SessionDatabase::class.java, databaseName).build()
        this.db = db
        dbName = databaseName
    }

    fun get(isSent: Boolean, messageCode: Int): SessionMessage {
        return db!!.sessionMessageDao().getSessionMessage(
            isSent,
            messageCode
        )
    }

    fun getAllPaged(): PagingSource<Int, SessionMessage> {
        return db!!.sessionMessageDao().getSessionMessagesPaged()
    }

    fun insert(message: SessionMessage) {
        scope.launch(Dispatchers.IO) {
            db!!.sessionMessageDao().insert(message)
        }
    }

    fun delete(isSent: Boolean, messageCode: Int) {
        scope.launch(Dispatchers.IO) {
            db!!.sessionMessageDao().delete(isSent, messageCode)
        }
    }

    fun setMessageDelivered(messageCode: Int) {
        scope.launch(Dispatchers.IO) {
            db!!.sessionMessageDao().setMessageDelivered(
                messageCode
            )
        }
    }

    fun setMessageRead(messageCode: Int) {
        scope.launch(Dispatchers.IO) {
            db!!.sessionMessageDao().setMessageRead(
                messageCode
            )
        }
    }

    fun getDatabaseName(): String? {
        return dbName
    }
}