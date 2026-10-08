package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.dao.SessionWithDresses
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DressItemEntity
import com.example.data.local.entity.LaundrySessionEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreManager(private val context: Context) {

    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    // --- Customers ---
    suspend fun saveCustomer(customer: CustomerEntity): String {
        val docRef = if (customer.id > 0) {
            db.collection("customers").document(customer.id.toString())
        } else {
            db.collection("customers").document()
        }

        val data = hashMapOf(
            "id" to docRef.id,
            "localId" to customer.id,
            "name" to customer.name,
            "phone" to customer.phone,
            "address" to customer.address,
            "notes" to customer.notes,
            "createdAt" to customer.createdAt
        )
        docRef.set(data, SetOptions.merge()).await()
        return docRef.id
    }

    suspend fun deleteCustomer(customerLocalId: Long) {
        val snapshot = db.collection("customers")
            .whereEqualTo("localId", customerLocalId)
            .get()
            .await()
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }
    }

    // Real-time customers flow from cloud
    fun getCustomersFlow(): Flow<List<CustomerEntity>> = callbackFlow {
        val listener: ListenerRegistration = db.collection("customers")
            .orderBy("name")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreManager", "Error listening to customers", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val localId = doc.getLong("localId") ?: doc.id.hashCode().toLong()
                        CustomerEntity(
                            id = localId,
                            name = doc.getString("name") ?: "",
                            phone = doc.getString("phone") ?: "",
                            address = doc.getString("address") ?: "",
                            notes = doc.getString("notes") ?: "",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    // --- Laundry Sessions ---
    suspend fun saveSession(session: LaundrySessionEntity): String {
        val docRef = if (session.id > 0) {
            db.collection("sessions").document(session.id.toString())
        } else {
            db.collection("sessions").document()
        }

        val data = hashMapOf(
            "id" to docRef.id,
            "localId" to session.id,
            "customerId" to session.customerId,
            "customerName" to session.customerName,
            "customerPhone" to session.customerPhone,
            "sessionCode" to session.sessionCode,
            "price" to session.price,
            "cost" to session.cost,
            "serviceType" to session.serviceType,
            "isCompleted" to session.isCompleted,
            "completedAt" to session.completedAt,
            "notes" to session.notes,
            "createdAt" to session.createdAt
        )
        docRef.set(data, SetOptions.merge()).await()
        return docRef.id
    }

    suspend fun updateSessionCompletion(sessionId: Long, isCompleted: Boolean, completedAt: Long?) {
        val query = db.collection("sessions").whereEqualTo("localId", sessionId).get().await()
        for (doc in query.documents) {
            doc.reference.update(
                mapOf(
                    "isCompleted" to isCompleted,
                    "completedAt" to completedAt
                )
            ).await()
        }
    }

    suspend fun deleteSession(sessionLocalId: Long) {
        val query = db.collection("sessions").whereEqualTo("localId", sessionLocalId).get().await()
        for (doc in query.documents) {
            // Delete dresses subcollection
            val dresses = doc.reference.collection("dresses").get().await()
            for (d in dresses.documents) {
                d.reference.delete().await()
            }
            doc.reference.delete().await()
        }
    }

    // --- Dresses ---
    suspend fun saveDress(dress: DressItemEntity) {
        val sessionQuery = db.collection("sessions").whereEqualTo("localId", dress.sessionId).get().await()
        val sessionDoc = sessionQuery.documents.firstOrNull() ?: return

        val dressDoc = if (dress.id > 0) {
            sessionDoc.reference.collection("dresses").document(dress.id.toString())
        } else {
            sessionDoc.reference.collection("dresses").document()
        }

        val data = hashMapOf(
            "id" to dressDoc.id,
            "localId" to dress.id,
            "sessionId" to dress.sessionId,
            "customerId" to dress.customerId,
            "imageUri" to dress.imageUri,
            "dressName" to dress.dressName,
            "color" to dress.color,
            "category" to dress.category,
            "isPoked" to dress.isPoked,
            "pokedMessage" to dress.pokedMessage,
            "pokedAt" to dress.pokedAt,
            "createdAt" to dress.createdAt
        )
        dressDoc.set(data, SetOptions.merge()).await()
    }

    suspend fun updateDressPokeStatus(sessionId: Long, dressId: Long, isPoked: Boolean, message: String, pokedAt: Long?) {
        val sessionQuery = db.collection("sessions").whereEqualTo("localId", sessionId).get().await()
        val sessionDoc = sessionQuery.documents.firstOrNull() ?: return

        val dressQuery = sessionDoc.reference.collection("dresses").whereEqualTo("localId", dressId).get().await()
        for (d in dressQuery.documents) {
            d.reference.update(
                mapOf(
                    "isPoked" to isPoked,
                    "pokedMessage" to message,
                    "pokedAt" to pokedAt
                )
            ).await()
        }
    }

    suspend fun deleteDress(sessionId: Long, dressId: Long) {
        val sessionQuery = db.collection("sessions").whereEqualTo("localId", sessionId).get().await()
        val sessionDoc = sessionQuery.documents.firstOrNull() ?: return

        val dressQuery = sessionDoc.reference.collection("dresses").whereEqualTo("localId", dressId).get().await()
        for (d in dressQuery.documents) {
            d.reference.delete().await()
        }
    }
}
