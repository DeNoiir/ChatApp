package com.example.chatapp.data.repository

import com.example.chatapp.data.dao.UserDao
import com.example.chatapp.data.model.User
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 用户数据仓库类
 * 负责处理与用户相关的数据操作
 *
 * @property userDao 用户数据访问对象
 */
class UserRepository @Inject constructor(private val userDao: UserDao) {

    /**
     * 获取所有用户
     *
     * @return 包含所有用户的Flow对象
     */
    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()

    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 匹配的用户对象,如果不存在则返回null
     */
    suspend fun getUserById(id: String): User? = userDao.getUserById(id)

    /**
     * 根据用户名获取用户
     *
     * @param name 用户名
     * @return 匹配的用户对象,如果不存在则返回null
     */
    suspend fun getUserByName(name: String): User? = userDao.getUserByName(name)

    /**
     * 插入新用户
     *
     * @param user 要插入的用户对象
     */
    suspend fun insertUser(user: User) = userDao.insertUser(user)

    /**
     * 更新现有用户信息
     *
     * @param user 要更新的用户对象
     */
    suspend fun updateUser(user: User) = userDao.updateUser(user)

    /**
     * 插入新用户或更新现有用户信息
     *
     * @param id 用户ID
     * @param name 用户名
     */
    suspend fun insertOrUpdateUser(id: String, name: String) {
        val existingUser = getUserById(id)
        if (existingUser == null) {
            insertUser(User(id = id, name = name))
        } else if (existingUser.name != name) {
            updateUser(existingUser.copy(name = name))
        }
    }
}