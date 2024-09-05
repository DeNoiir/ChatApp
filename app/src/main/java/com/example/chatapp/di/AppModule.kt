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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "local_network_chat_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideUserRepository(appDatabase: AppDatabase): UserRepository {
        return UserRepository(appDatabase.userDao())
    }

    @Provides
    @Singleton
    fun provideMessageRepository(appDatabase: AppDatabase): MessageRepository {
        return MessageRepository(appDatabase.messageDao())
    }

    @Provides
    @Singleton
    fun provideUdpDiscoveryService(): UdpDiscoveryService {
        return UdpDiscoveryService()
    }

    @Provides
    @Singleton
    fun provideTcpCommunicationService(): TcpCommunicationService {
        return TcpCommunicationService()
    }
}