package com.example.chatapp.data.dao

import androidx.room.*
import com.example.chatapp.data.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 用户数据访问对象接口
 * 定义了与用户相关的数据库操作
 */
@Dao
interface UserDao {
    /**
     * 获取所有用户
     *
     * @return 包含所有用户的Flow对象
     */
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 匹配的用户对象,如果不存在则返回null
     */
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): User?

    /**
     * 根据用户名获取用户
     *
     * @param name 用户名
     * @return 匹配的用户对象,如果不存在则返回null
     */
    @Query("SELECT * FROM users WHERE name = :name")
    suspend fun getUserByName(name: String): User?

    /**
     * 插入新用户或更新现有用户
     *
     * @param user 要插入或更新的用户对象
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    /**
     * 更新用户信息
     *
     * @param user 要更新的用户对象
     */
    @Update
    suspend fun updateUser(user: User)

    /**
     * 删除用户
     *
     * @param user 要删除的用户对象
     */
    @Delete
    suspend fun deleteUser(user: User)
}