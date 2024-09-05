package com.example.chatapp.data.repository

import com.example.chatapp.data.dao.UserDao
import com.example.chatapp.data.model.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepository @Inject constructor(private val userDao: UserDao) {
    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()

    suspend fun getUserById(id: String): User? = userDao.getUserById(id)

    suspend fun getUserByName(name: String): User? = userDao.getUserByName(name)

    suspend fun insertUser(user: User) = userDao.insertUser(user)

    suspend fun updateUser(user: User) = userDao.updateUser(user)

    suspend fun deleteUser(user: User) = userDao.deleteUser(user)
}