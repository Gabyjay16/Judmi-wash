package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.dao.SessionWithDresses
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.DressItemEntity
import com.example.data.local.entity.LaundrySessionEntity
import com.example.data.remote.AiDressAnalysisResult
import com.example.data.remote.FirestoreManager
import com.example.data.repository.LaundryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class AppScreen {
    DASHBOARD,
    SESSIONS,
    SESSION_DETAIL,
    NEW_SESSION,
    CUSTOMERS,
    CUSTOMER_DETAIL,
    AI_DRESS_SCAN,
    CUSTOMER_PORTAL
}

enum class ProfitPeriod(val label: String) {
    PAST_7_DAYS("Past 7 Days"),
    THIS_MONTH("This Month"),
    PAST_30_DAYS("Past 30 Days"),
    ALL_TIME("All Time")
}

data class ProfitSummary(
    val period: ProfitPeriod,
    val totalRevenue: Double,
    val totalCost: Double,
    val totalProfit: Double,
    val completedSessions: Int,
    val openSessions: Int,
    val averageProfit: Double,
    val profitDailyTrend: List<Pair<String, Double>> // Day label to profit
)

sealed interface AiScanState {
    data object Idle : AiScanState
    data object Analyzing : AiScanState
    data class Success(val result: AiDressAnalysisResult, val imageBitmap: Bitmap) : AiScanState
    data class Error(val message: String) : AiScanState
}

class LaundryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LaundryRepository

    init {
        val database = AppDatabase.getInstance(application)
        val firestoreManager = FirestoreManager(application)
        repository = LaundryRepository(
            dao = database.laundryDao(),
            firestoreManager = firestoreManager
        )
        viewModelScope.launch {
            repository.seedDemoDataIfEmpty()
        }
    }

    // --- Navigation State ---
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _screenBackStack = MutableStateFlow<List<AppScreen>>(listOf(AppScreen.DASHBOARD))

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow<Long?>(null)
    val selectedCustomerId: StateFlow<Long?> = _selectedCustomerId.asStateFlow()

    private val _preselectedCustomerForNewSession = MutableStateFlow<CustomerEntity?>(null)
    val preselectedCustomerForNewSession: StateFlow<CustomerEntity?> = _preselectedCustomerForNewSession.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            _screenBackStack.value = _screenBackStack.value + screen
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        val stack = _screenBackStack.value
        if (stack.size > 1) {
            val updated = stack.dropLast(1)
            _screenBackStack.value = updated
            _currentScreen.value = updated.last()
            return true
        }
        return false
    }

    fun openSessionDetail(sessionId: Long) {
        _selectedSessionId.value = sessionId
        navigateTo(AppScreen.SESSION_DETAIL)
    }

    fun openCustomerDetail(customerId: Long) {
        _selectedCustomerId.value = customerId
        navigateTo(AppScreen.CUSTOMER_DETAIL)
    }

    fun startNewSessionForCustomer(customer: CustomerEntity) {
        _preselectedCustomerForNewSession.value = customer
        navigateTo(AppScreen.NEW_SESSION)
    }

    fun clearPreselectedCustomer() {
        _preselectedCustomerForNewSession.value = null
    }

    // --- Core Data Flows ---
    val allCustomers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<LaundrySessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessionsWithDresses: StateFlow<List<SessionWithDresses>> = repository.allSessionsWithDresses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Selected Session Flow ---
    private val _selectedSessionWithDresses = MutableStateFlow<SessionWithDresses?>(null)
    val selectedSessionWithDresses: StateFlow<SessionWithDresses?> = _selectedSessionWithDresses.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_selectedSessionId, repository.allSessionsWithDresses) { id, list ->
                if (id == null) null else list.firstOrNull { it.session.id == id }
            }.collect {
                _selectedSessionWithDresses.value = it
            }
        }
    }

    // --- Profit Analytics ---
    private val _profitPeriod = MutableStateFlow(ProfitPeriod.PAST_30_DAYS)
    val profitPeriod: StateFlow<ProfitPeriod> = _profitPeriod.asStateFlow()

    fun setProfitPeriod(period: ProfitPeriod) {
        _profitPeriod.value = period
    }

    val profitSummary: StateFlow<ProfitSummary> = combine(
        allSessions,
        _profitPeriod
    ) { sessions, period ->
        val now = System.currentTimeMillis()
        val filtered = when (period) {
            ProfitPeriod.PAST_7_DAYS -> {
                val cutoff = now - TimeUnit.DAYS.toMillis(7)
                sessions.filter { it.createdAt >= cutoff }
            }
            ProfitPeriod.PAST_30_DAYS -> {
                val cutoff = now - TimeUnit.DAYS.toMillis(30)
                sessions.filter { it.createdAt >= cutoff }
            }
            ProfitPeriod.THIS_MONTH -> {
                // Approximate 30 days or start of month
                val cutoff = now - TimeUnit.DAYS.toMillis(30)
                sessions.filter { it.createdAt >= cutoff }
            }
            ProfitPeriod.ALL_TIME -> sessions
        }

        val totalRev = filtered.sumOf { it.price }
        val totalCost = filtered.sumOf { it.cost }
        val totalProf = totalRev - totalCost
        val completedCount = filtered.count { it.isCompleted }
        val openCount = filtered.count { !it.isCompleted }
        val avgProfit = if (filtered.isNotEmpty()) totalProf / filtered.size else 0.0

        // Build daily trend for past 7 days
        val trend = (6 downTo 0).map { daysAgo ->
            val dayStart = now - TimeUnit.DAYS.toMillis(daysAgo.toLong())
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            val daySessions = sessions.filter { it.createdAt in dayStart until dayEnd }
            val dayProfit = daySessions.sumOf { it.price - it.cost }
            val label = when (daysAgo) {
                0 -> "Today"
                1 -> "Y'day"
                else -> "-${daysAgo}d"
            }
            Pair(label, dayProfit)
        }

        ProfitSummary(
            period = period,
            totalRevenue = totalRev,
            totalCost = totalCost,
            totalProfit = totalProf,
            completedSessions = completedCount,
            openSessions = openCount,
            averageProfit = avgProfit,
            profitDailyTrend = trend
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProfitSummary(ProfitPeriod.PAST_30_DAYS, 0.0, 0.0, 0.0, 0, 0, 0.0, emptyList())
    )

    // --- Session CRUD Actions ---
    fun toggleSessionCompletion(sessionId: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.updateSessionCompletion(sessionId, isCompleted)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
                navigateBack()
            }
        }
    }

    fun createSession(
        customerId: Long,
        customerName: String,
        customerPhone: String,
        price: Double,
        cost: Double,
        serviceType: String,
        notes: String,
        photos: List<Pair<String, String>>, // uri to dress name
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val code = "LS-${(1000..9999).random()}"
            val session = LaundrySessionEntity(
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                sessionCode = code,
                price = price,
                cost = cost,
                serviceType = serviceType,
                isCompleted = false,
                notes = notes,
                createdAt = System.currentTimeMillis()
            )
            val newId = repository.createSessionWithDresses(session, photos)
            onSuccess(newId)
        }
    }

    fun addPhotosToSession(sessionId: Long, customerId: Long, photos: List<Pair<String, String>>) {
        viewModelScope.launch {
            repository.addDressesToSession(sessionId, customerId, photos)
        }
    }

    fun deleteDress(dress: DressItemEntity) {
        viewModelScope.launch {
            repository.deleteDress(dress)
        }
    }

    fun deleteMultipleDresses(dresses: List<DressItemEntity>) {
        viewModelScope.launch {
            repository.deleteDresses(dresses)
        }
    }

    // --- Customer Management ---
    fun addCustomer(name: String, phone: String, address: String, notes: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val c = CustomerEntity(
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                notes = notes.trim()
            )
            val id = repository.saveCustomer(c)
            onDone(id)
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    // --- Customer Portal Login & State ---
    private val _loggedInCustomer = MutableStateFlow<CustomerEntity?>(null)
    val loggedInCustomer: StateFlow<CustomerEntity?> = _loggedInCustomer.asStateFlow()

    private val _customerLoginError = MutableStateFlow<String?>(null)
    val customerLoginError: StateFlow<String?> = _customerLoginError.asStateFlow()

    fun loginCustomerByPhone(phone: String, onFound: () -> Unit) {
        _customerLoginError.value = null
        if (phone.isBlank()) {
            _customerLoginError.value = "Please enter your phone number."
            return
        }

        viewModelScope.launch {
            val customer = repository.getCustomerByPhone(phone)
            if (customer != null) {
                _loggedInCustomer.value = customer
                _customerLoginError.value = null
                onFound()
            } else {
                _customerLoginError.value = "No customer record found for '$phone'. Please check the phone number or ask staff."
            }
        }
    }

    fun logoutCustomer() {
        _loggedInCustomer.value = null
        _customerLoginError.value = null
    }

    fun pokeDress(dressId: Long, message: String) {
        viewModelScope.launch {
            repository.pokeDress(dressId, isPoked = true, message = message)
        }
    }

    fun unpokeDress(dressId: Long) {
        viewModelScope.launch {
            repository.pokeDress(dressId, isPoked = false, message = "")
        }
    }

    // --- AI Dress Scanner ---
    private val _aiScanState = MutableStateFlow<AiScanState>(AiScanState.Idle)
    val aiScanState: StateFlow<AiScanState> = _aiScanState.asStateFlow()

    fun resetAiScanState() {
        _aiScanState.value = AiScanState.Idle
    }

    fun analyzeDress(bitmap: Bitmap) {
        _aiScanState.value = AiScanState.Analyzing
        viewModelScope.launch {
            val result = repository.identifyDress(bitmap)
            result.onSuccess {
                _aiScanState.value = AiScanState.Success(it, bitmap)
            }.onFailure {
                _aiScanState.value = AiScanState.Error(it.localizedMessage ?: "Failed to analyze dress.")
            }
        }
    }
}
