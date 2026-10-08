package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DressItemEntity
import com.example.data.local.entity.LaundrySessionEntity
import com.example.data.repository.LaundryRepository
import com.example.util.CurrencyUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: LaundryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LaundryRepository(db.laundryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `verify app name resource matches Judmi Wash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Judmi Wash", appName)
    }

    @Test
    fun `verify CFA Franc currency formatting`() {
        assertEquals("15,000 FCFA", CurrencyUtils.formatCfa(15000.0))
        assertEquals("8,500 FCFA", CurrencyUtils.formatCfa(8500.0))
        assertEquals("0 FCFA", CurrencyUtils.formatCfa(0.0))
    }

    @Test
    fun `customer creation and phone lookup for login`() = runBlocking {
        val customer = CustomerEntity(
            name = "Sarah Miller",
            phone = "08011223344",
            address = "12 Ocean Way",
            notes = "Cold wash only"
        )
        val customerId = repository.saveCustomer(customer)
        assertTrue(customerId > 0)

        // Customer login lookup by phone number
        val found = repository.getCustomerByPhone("08011223344")
        assertNotNull(found)
        assertEquals("Sarah Miller", found?.name)
    }

    @Test
    fun `laundry session creation with photos and profit calculation`() = runBlocking {
        val customer = CustomerEntity(name = "David K", phone = "08099887766")
        val customerId = repository.saveCustomer(customer)

        val session = LaundrySessionEntity(
            customerId = customerId,
            customerName = "David K",
            customerPhone = "08099887766",
            sessionCode = "LS-9001",
            price = 45.0,
            cost = 12.0,
            serviceType = "Dry Clean",
            isCompleted = false
        )

        val dressPhotos = listOf(
            Pair("file:///dummy/dress1.jpg", "Navy Silk Dress"),
            Pair("file:///dummy/dress2.jpg", "White Evening Gown")
        )

        val sessionId = repository.createSessionWithDresses(session, dressPhotos)
        assertTrue(sessionId > 0)

        val retrievedSession = repository.getSessionById(sessionId)
        assertNotNull(retrievedSession)
        assertEquals(33.0, retrievedSession!!.profit, 0.001)
        assertFalse(retrievedSession.isCompleted)

        // Mark session as completed
        repository.updateSessionCompletion(sessionId, true)
        val completedSession = repository.getSessionById(sessionId)
        assertTrue(completedSession!!.isCompleted)

        // Verify dresses attached
        val dresses = repository.getDressesForSession(sessionId).first()
        assertEquals(2, dresses.size)

        // Test poking a dress by customer
        val dress = dresses.first()
        repository.pokeDress(dress.id, isPoked = true, message = "Rush this dress!")
        val updatedDresses = repository.getDressesForSession(sessionId).first()
        val pokedDress = updatedDresses.first { it.id == dress.id }
        assertTrue(pokedDress.isPoked)
        assertEquals("Rush this dress!", pokedDress.pokedMessage)

        // Delete singular dress
        repository.deleteDress(dress)
        val dressesAfterDelete = repository.getDressesForSession(sessionId).first()
        assertEquals(1, dressesAfterDelete.size)

        // Delete session
        repository.deleteSession(sessionId)
        val sessionsAfterDelete = repository.allSessions.first()
        assertTrue(sessionsAfterDelete.none { it.id == sessionId })
    }

    @Test
    fun `verify customer sessions retrieval for profile view`() = runBlocking {
        val customer = CustomerEntity(name = "Grace Hopper", phone = "08012399999")
        val customerId = repository.saveCustomer(customer)

        val session1 = LaundrySessionEntity(
            customerId = customerId,
            customerName = "Grace Hopper",
            customerPhone = "08012399999",
            sessionCode = "LS-7001",
            price = 10000.0,
            cost = 3000.0,
            isCompleted = false
        )
        val session2 = LaundrySessionEntity(
            customerId = customerId,
            customerName = "Grace Hopper",
            customerPhone = "08012399999",
            sessionCode = "LS-7002",
            price = 15000.0,
            cost = 4000.0,
            isCompleted = true
        )
        repository.createSessionWithDresses(session1, emptyList())
        repository.createSessionWithDresses(session2, emptyList())

        val customerSessions = repository.getSessionsForCustomer(customerId).first()
        assertEquals(2, customerSessions.size)
    }
}
