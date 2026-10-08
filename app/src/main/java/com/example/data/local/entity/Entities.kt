package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["phone"], unique = true)]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "laundry_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId")]
)
data class LaundrySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val sessionCode: String,
    val price: Double,
    val cost: Double = 0.0,
    val serviceType: String = "Standard Wash & Fold",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val profit: Double
        get() = price - cost
}

@Entity(
    tableName = "dress_items",
    foreignKeys = [
        ForeignKey(
            entity = LaundrySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("customerId")]
)
data class DressItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val customerId: Long,
    val imageUri: String,
    val dressName: String = "Dress Item",
    val color: String = "",
    val category: String = "Dress",
    val isPoked: Boolean = false,
    val pokedMessage: String = "",
    val pokedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
