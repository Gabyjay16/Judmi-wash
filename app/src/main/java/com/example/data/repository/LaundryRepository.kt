package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.data.local.dao.LaundryDao
import com.example.data.local.dao.SessionWithDresses
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DressItemEntity
import com.example.data.local.entity.LaundrySessionEntity
import com.example.data.remote.AiDressAnalysisResult
import com.example.data.remote.FirestoreManager
import com.example.data.remote.GeminiService
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class LaundryRepository(
    private val dao: LaundryDao,
    private val geminiService: GeminiService = GeminiService(),
    private val firestoreManager: FirestoreManager? = null
) {

    // --- Customers ---
    val allCustomers: Flow<List<CustomerEntity>> = dao.getAllCustomers()

    suspend fun getCustomerById(id: Long): CustomerEntity? = dao.getCustomerById(id)

    suspend fun getCustomerByPhone(phone: String): CustomerEntity? {
        val cleanPhone = phone.trim().replace(" ", "").replace("-", "")
        val all = dao.getAllCustomers().first()
        return all.firstOrNull {
            it.phone.trim().replace(" ", "").replace("-", "") == cleanPhone
        }
    }

    suspend fun saveCustomer(customer: CustomerEntity): Long {
        val id = dao.insertCustomer(customer)
        try {
            firestoreManager?.saveCustomer(customer.copy(id = id))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return id
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        try {
            firestoreManager?.deleteCustomer(customer.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        dao.deleteCustomer(customer)
    }

    suspend fun deleteCustomerById(customerId: Long) {
        try {
            firestoreManager?.deleteCustomer(customerId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        dao.deleteCustomerById(customerId)
    }

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = dao.searchCustomers(query)

    // --- Sessions ---
    val allSessions: Flow<List<LaundrySessionEntity>> = dao.getAllSessions()
    val allSessionsWithDresses: Flow<List<SessionWithDresses>> = dao.getAllSessionsWithDresses()

    suspend fun getSessionById(id: Long): LaundrySessionEntity? = dao.getSessionById(id)

    fun getSessionWithDresses(sessionId: Long): Flow<SessionWithDresses?> = dao.getSessionWithDresses(sessionId)

    fun getSessionsForCustomer(customerId: Long): Flow<List<LaundrySessionEntity>> =
        dao.getSessionsForCustomer(customerId)

    suspend fun createSessionWithDresses(
        session: LaundrySessionEntity,
        dressPhotos: List<Pair<String, String>> // Pair<imageUri, dressName>
    ): Long {
        val sessionId = dao.insertSession(session)
        val savedSession = session.copy(id = sessionId)
        try {
            firestoreManager?.saveSession(savedSession)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val dressEntities = dressPhotos.map { (uri, name) ->
            DressItemEntity(
                sessionId = sessionId,
                customerId = session.customerId,
                imageUri = uri,
                dressName = name.ifBlank { "Dress Item" },
                category = "Dress",
                createdAt = System.currentTimeMillis()
            )
        }
        if (dressEntities.isNotEmpty()) {
            val ids = dao.insertDresses(dressEntities)
            try {
                dressEntities.forEachIndexed { index, dressItem ->
                    val dressWithId = dressItem.copy(id = ids.getOrElse(index) { 0L })
                    firestoreManager?.saveDress(dressWithId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return sessionId
    }

    suspend fun updateSessionCompletion(sessionId: Long, isCompleted: Boolean) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        dao.updateSessionCompletion(sessionId, isCompleted, completedAt)
        try {
            firestoreManager?.updateSessionCompletion(sessionId, isCompleted, completedAt)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        try {
            firestoreManager?.deleteSession(sessionId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Clean up image files
        val dresses = dao.getDressesForSessionSync(sessionId)
        for (d in dresses) {
            ImageStorageHelper.deleteImageFile(d.imageUri)
        }
        dao.deleteSessionById(sessionId)
    }

    // --- Dress Items ---
    fun getDressesForSession(sessionId: Long): Flow<List<DressItemEntity>> =
        dao.getDressesForSession(sessionId)

    suspend fun addDressesToSession(
        sessionId: Long,
        customerId: Long,
        photos: List<Pair<String, String>>
    ) {
        val entities = photos.map { (uri, name) ->
            DressItemEntity(
                sessionId = sessionId,
                customerId = customerId,
                imageUri = uri,
                dressName = name.ifBlank { "Dress Item" },
                category = "Dress"
            )
        }
        val ids = dao.insertDresses(entities)
        try {
            entities.forEachIndexed { index, dressItem ->
                val dressWithId = dressItem.copy(id = ids.getOrElse(index) { 0L })
                firestoreManager?.saveDress(dressWithId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteDress(dress: DressItemEntity) {
        try {
            firestoreManager?.deleteDress(dress.sessionId, dress.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ImageStorageHelper.deleteImageFile(dress.imageUri)
        dao.deleteDress(dress)
    }

    suspend fun deleteDresses(dresses: List<DressItemEntity>) {
        dresses.forEach { dress ->
            try {
                firestoreManager?.deleteDress(dress.sessionId, dress.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            ImageStorageHelper.deleteImageFile(dress.imageUri)
        }
        dao.deleteDressesByIds(dresses.map { it.id })
    }

    suspend fun pokeDress(dressId: Long, isPoked: Boolean, message: String) {
        val pokedAt = if (isPoked) System.currentTimeMillis() else null
        dao.updateDressPokeStatus(dressId, isPoked, message, pokedAt)
        try {
            val dresses = dao.getAllDressesSync()
            val dress = dresses.firstOrNull { it.id == dressId }
            if (dress != null) {
                firestoreManager?.updateDressPokeStatus(dress.sessionId, dressId, isPoked, message, pokedAt)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- AI Dress Matching ---
    suspend fun identifyDress(queryBitmap: Bitmap): Result<AiDressAnalysisResult> {
        val allSessions = dao.getAllSessionsWithDresses().first()
        return geminiService.analyzeAndMatchDress(queryBitmap, allSessions)
    }

    // --- Seed Demo Data If Database Is Empty ---
    suspend fun seedDemoDataIfEmpty() {
        val existing = dao.getAllCustomers().first()
        if (existing.isNotEmpty()) return

        val now = System.currentTimeMillis()
        val dayMillis = TimeUnit.DAYS.toMillis(1)

        // Customer 1
        val c1Id = dao.insertCustomer(
            CustomerEntity(
                name = "Eleanor Vance",
                phone = "08012345678",
                address = "14 Maple Avenue, Suite 4",
                notes = "Prefers organic unscented detergent",
                createdAt = now - (dayMillis * 18)
            )
        )

        // Customer 2
        val c2Id = dao.insertCustomer(
            CustomerEntity(
                name = "Marcus Sterling",
                phone = "08098765432",
                address = "78 Pine Ridge Road",
                notes = "Heavy starch for formal shirts",
                createdAt = now - (dayMillis * 14)
            )
        )

        // Customer 3
        val c3Id = dao.insertCustomer(
            CustomerEntity(
                name = "Amara Okafor",
                phone = "08123456789",
                address = "22 Palm Grove Crescent",
                notes = "Delicate silks - handle with care",
                createdAt = now - (dayMillis * 8)
            )
        )

        // Customer 4
        val c4Id = dao.insertCustomer(
            CustomerEntity(
                name = "Liam O'Connor",
                phone = "08055566778",
                address = "5 Highland Terrace",
                notes = "Express turnaround regular",
                createdAt = now - (dayMillis * 3)
            )
        )

        // Sessions for Eleanor (Completed + Open)
        val s1Id = dao.insertSession(
            LaundrySessionEntity(
                customerId = c1Id,
                customerName = "Eleanor Vance",
                customerPhone = "08012345678",
                sessionCode = "LS-1001",
                price = 15000.0,
                cost = 4500.0,
                serviceType = "Delicate Silk & Wool",
                isCompleted = true,
                completedAt = now - (dayMillis * 12),
                notes = "Steam press finished nicely",
                createdAt = now - (dayMillis * 14)
            )
        )
        dao.insertDresses(
            listOf(
                DressItemEntity(sessionId = s1Id, customerId = c1Id, imageUri = "", dressName = "Emerald Silk Evening Gown", color = "Emerald Green", category = "Gown"),
                DressItemEntity(sessionId = s1Id, customerId = c1Id, imageUri = "", dressName = "White Embroidered Blouse", color = "White", category = "Blouse")
            )
        )

        val s2Id = dao.insertSession(
            LaundrySessionEntity(
                customerId = c1Id,
                customerName = "Eleanor Vance",
                customerPhone = "08012345678",
                sessionCode = "LS-1004",
                price = 8500.0,
                cost = 2500.0,
                serviceType = "Standard Wash & Fold",
                isCompleted = false,
                notes = "Customer requested rush delivery for Friday event",
                createdAt = now - (dayMillis * 1)
            )
        )
        dao.insertDresses(
            listOf(
                DressItemEntity(sessionId = s2Id, customerId = c1Id, imageUri = "", dressName = "Navy Floral Sundress", color = "Navy Blue", category = "Sundress", isPoked = true, pokedMessage = "Please expedite for Friday party!", pokedAt = now - 3600000)
            )
        )

        // Sessions for Marcus
        val s3Id = dao.insertSession(
            LaundrySessionEntity(
                customerId = c2Id,
                customerName = "Marcus Sterling",
                customerPhone = "08098765432",
                sessionCode = "LS-1002",
                price = 25000.0,
                cost = 7000.0,
                serviceType = "Dry Cleaning & Press",
                isCompleted = true,
                completedAt = now - (dayMillis * 7),
                notes = "3-Piece Tuxedo + Oxford shirts",
                createdAt = now - (dayMillis * 9)
            )
        )
        dao.insertDresses(
            listOf(
                DressItemEntity(sessionId = s3Id, customerId = c2Id, imageUri = "", dressName = "Charcoal Wool Tuxedo Jacket", color = "Charcoal", category = "Jacket"),
                DressItemEntity(sessionId = s3Id, customerId = c2Id, imageUri = "", dressName = "Crisp White Oxford Shirt", color = "White", category = "Shirt"),
                DressItemEntity(sessionId = s3Id, customerId = c2Id, imageUri = "", dressName = "Tailored Black Trousers", color = "Black", category = "Trousers")
            )
        )

        // Sessions for Amara
        val s4Id = dao.insertSession(
            LaundrySessionEntity(
                customerId = c3Id,
                customerName = "Amara Okafor",
                customerPhone = "08123456789",
                sessionCode = "LS-1003",
                price = 18000.0,
                cost = 5000.0,
                serviceType = "Premium Wash & Steam",
                isCompleted = false,
                notes = "Traditional Ankara print dresses",
                createdAt = now - (dayMillis * 2)
            )
        )
        dao.insertDresses(
            listOf(
                DressItemEntity(sessionId = s4Id, customerId = c3Id, imageUri = "", dressName = "Golden & Indigo Ankara Maxi Dress", color = "Gold & Indigo", category = "Maxi Dress"),
                DressItemEntity(sessionId = s4Id, customerId = c3Id, imageUri = "", dressName = "Coral Pleated Midi Dress", color = "Coral Red", category = "Dress")
            )
        )

        // Session for Liam
        val s5Id = dao.insertSession(
            LaundrySessionEntity(
                customerId = c4Id,
                customerName = "Liam O'Connor",
                customerPhone = "08055566778",
                sessionCode = "LS-1005",
                price = 12000.0,
                cost = 3500.0,
                serviceType = "Wash & Iron",
                isCompleted = true,
                completedAt = now - (dayMillis * 1),
                notes = "Ready for customer pickup",
                createdAt = now - (dayMillis * 3)
            )
        )
        dao.insertDresses(
            listOf(
                DressItemEntity(sessionId = s5Id, customerId = c4Id, imageUri = "", dressName = "Sky Blue Linen Shirt", color = "Light Blue", category = "Shirt")
            )
        )
    }
}
