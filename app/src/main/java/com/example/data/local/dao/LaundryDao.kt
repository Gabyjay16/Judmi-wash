package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Update
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DressItemEntity
import com.example.data.local.entity.LaundrySessionEntity
import kotlinx.coroutines.flow.Flow

data class SessionWithDresses(
    @Embedded val session: LaundrySessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val dresses: List<DressItemEntity>
)

data class CustomerWithSessions(
    @Embedded val customer: CustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customerId"
    )
    val sessions: List<LaundrySessionEntity>
)

@Dao
interface LaundryDao {

    // --- Customers ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteCustomerById(customerId: Long)

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    // --- Laundry Sessions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LaundrySessionEntity): Long

    @Update
    suspend fun updateSession(session: LaundrySessionEntity)

    @Delete
    suspend fun deleteSession(session: LaundrySessionEntity)

    @Query("DELETE FROM laundry_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("SELECT * FROM laundry_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<LaundrySessionEntity>>

    @Query("SELECT * FROM laundry_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): LaundrySessionEntity?

    @Query("SELECT * FROM laundry_sessions WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getSessionsForCustomer(customerId: Long): Flow<List<LaundrySessionEntity>>

    @Query("SELECT * FROM laundry_sessions WHERE customerPhone = :phone ORDER BY createdAt DESC")
    fun getSessionsForCustomerPhone(phone: String): Flow<List<LaundrySessionEntity>>

    @Query("SELECT * FROM laundry_sessions WHERE isCompleted = :isCompleted ORDER BY createdAt DESC")
    fun getSessionsByStatus(isCompleted: Boolean): Flow<List<LaundrySessionEntity>>

    @Query("SELECT * FROM laundry_sessions WHERE createdAt >= :startTime AND createdAt <= :endTime ORDER BY createdAt DESC")
    fun getSessionsBetweenDates(startTime: Long, endTime: Long): Flow<List<LaundrySessionEntity>>

    @androidx.room.Transaction
    @Query("SELECT * FROM laundry_sessions WHERE id = :sessionId LIMIT 1")
    fun getSessionWithDresses(sessionId: Long): Flow<SessionWithDresses?>

    @androidx.room.Transaction
    @Query("SELECT * FROM laundry_sessions ORDER BY createdAt DESC")
    fun getAllSessionsWithDresses(): Flow<List<SessionWithDresses>>

    @Query("UPDATE laundry_sessions SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :sessionId")
    suspend fun updateSessionCompletion(sessionId: Long, isCompleted: Boolean, completedAt: Long?)

    // --- Dress Items ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDress(dress: DressItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDresses(dresses: List<DressItemEntity>): List<Long>

    @Update
    suspend fun updateDress(dress: DressItemEntity)

    @Delete
    suspend fun deleteDress(dress: DressItemEntity)

    @Query("DELETE FROM dress_items WHERE id = :dressId")
    suspend fun deleteDressById(dressId: Long)

    @Query("DELETE FROM dress_items WHERE id IN (:dressIds)")
    suspend fun deleteDressesByIds(dressIds: List<Long>)

    @Query("SELECT * FROM dress_items WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getDressesForSession(sessionId: Long): Flow<List<DressItemEntity>>

    @Query("SELECT * FROM dress_items WHERE sessionId = :sessionId")
    suspend fun getDressesForSessionSync(sessionId: Long): List<DressItemEntity>

    @Query("SELECT * FROM dress_items ORDER BY createdAt DESC")
    suspend fun getAllDressesSync(): List<DressItemEntity>

    @Query("UPDATE dress_items SET isPoked = :isPoked, pokedMessage = :message, pokedAt = :pokedAt WHERE id = :dressId")
    suspend fun updateDressPokeStatus(dressId: Long, isPoked: Boolean, message: String, pokedAt: Long?)
}
