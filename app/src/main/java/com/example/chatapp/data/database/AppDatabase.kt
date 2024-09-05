package com.example.chatapp.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.chatapp.data.dao.MessageDao
import com.example.chatapp.data.dao.UserDao
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.model.User

@Database(entities = [User::class, Message::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
}