package com.example.chatapp.di

import android.content.Context
import androidx.room.Room
import com.example.chatapp.data.database.AppDatabase
import com.example.chatapp.data.repository.MessageRepository
import com.example.chatapp.data.repository.UserRepository
import com.example.chatapp.network.TcpCommunicationService
import com.example.chatapp.network.UdpDiscoveryService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 应用程序依赖注入模块
 * 提供应用程序所需的各种依赖项
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供应用数据库实例
     *
     * @param context 应用程序上下文
     * @return AppDatabase实例
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "local_network_chat_db"
        ).build()
    }

    /**
     * 提供用户仓库实例
     *
     * @param appDatabase 应用数据库
     * @return UserRepository实例
     */
    @Provides
    @Singleton
    fun provideUserRepository(appDatabase: AppDatabase): UserRepository {
        return UserRepository(appDatabase.userDao())
    }

    /**
     * 提供消息仓库实例
     *
     * @param appDatabase 应用数据库
     * @return MessageRepository实例
     */
    @Provides
    @Singleton
    fun provideMessageRepository(appDatabase: AppDatabase): MessageRepository {
        return MessageRepository(appDatabase.messageDao())
    }

    /**
     * 提供UDP发现服务实例
     *
     * @return UdpDiscoveryService实例
     */
    @Provides
    @Singleton
    fun provideUdpDiscoveryService(): UdpDiscoveryService {
        return UdpDiscoveryService()
    }

    /**
     * 提供TCP通信服务实例
     *
     * @return TcpCommunicationService实例
     */
    @Provides
    @Singleton
    fun provideTcpCommunicationService(): TcpCommunicationService {
        return TcpCommunicationService()
    }
}