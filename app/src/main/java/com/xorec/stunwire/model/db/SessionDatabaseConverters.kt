package com.xorec.stunwire.model.db

import androidx.room.TypeConverter

class SessionDatabaseConverters {
    @TypeConverter
    fun toSessionMessageStatus(value: Int) = enumValues<SessionMessageStatus>()[value]

    @TypeConverter
    fun fromSessionMessageStatus(value: SessionMessageStatus) = value.ordinal
}