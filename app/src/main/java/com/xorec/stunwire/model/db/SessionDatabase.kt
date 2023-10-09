package com.xorec.stunwire.model.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.*


@Database(entities = [SessionMessage::class], version = 1, exportSchema = false)
@TypeConverters(SessionDatabaseConverters::class)
abstract class SessionDatabase: RoomDatabase() {
    abstract fun sessionMessageDao(): SessionMessageDao
}