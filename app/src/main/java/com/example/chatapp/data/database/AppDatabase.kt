package com.example.chatapp.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.chatapp.data.dao.MessageDao
import com.example.chatapp.data.dao.UserDao
import com.example.chatapp.data.model.Message
import com.example.chatapp.data.model.User

/**
 * 应用数据库类
 * 定义了数据库的结构和版本,以及提供数据访问对象的方法
 */
@Database(entities = [User::class, Message::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    /**
     * 获取用户数据访问对象
     *
     * @return UserDao实例
     */
    abstract fun userDao(): UserDao

    /**
     * 获取消息数据访问对象
     *
     * @return MessageDao实例
     */
    abstract fun messageDao(): MessageDao
}