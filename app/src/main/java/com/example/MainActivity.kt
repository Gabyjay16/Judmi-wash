package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auth.AuthManager
import com.example.ui.screens.AiDressScanScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomerPortalScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NewSessionScreen
import com.example.ui.screens.SessionDetailScreen
import com.example.ui.screens.SessionsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LaundryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LaundryViewModel by viewModels()
    private val authManager by lazy { AuthManager(this) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentUser by authManager.currentUser.collectAsState()

                if (currentUser == null) {
                    SignInScreen(authManager = authManager)
                } else {
                    val currentScreen by viewModel.currentScreen.collectAsState()

                    // Back navigation handling
                    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
                        viewModel.navigateBack()
                    }

                    val isStaffMode = currentScreen != AppScreen.CUSTOMER_PORTAL
                    val showBottomBar = currentScreen in listOf(
                        AppScreen.DASHBOARD,
                        AppScreen.SESSIONS,
                        AppScreen.AI_DRESS_SCAN,
                        AppScreen.CUSTOMERS
                    )

                    Scaffold(
                        topBar = {
                            if (currentScreen != AppScreen.SESSION_DETAIL &&
                                currentScreen != AppScreen.NEW_SESSION &&
                                currentScreen != AppScreen.CUSTOMER_DETAIL &&
                                currentScreen != AppScreen.CUSTOMER_PORTAL
                            ) {
                                TopAppBar(
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.LocalLaundryService,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Judmi Wash",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.titleLarge
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.CloudDone,
                                                        contentDescription = null,
                                                        tint = Color(0xFF00897B),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Cloud Synced",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF00897B)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    actions = {
                                        // Portal Switcher Chip
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                viewModel.navigateTo(AppScreen.CUSTOMER_PORTAL)
                                            },
                                            label = {
                                                Text(
                                                    text = "Customer Login",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .testTag("top_bar_customer_portal_button")
                                        )

                                        IconButton(
                                            onClick = { authManager.signOut() },
                                            modifier = Modifier.testTag("sign_out_button")
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ExitToApp,
                                                contentDescription = "Sign Out",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            }
                        },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.DASHBOARD,
                                    onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentScreen == AppScreen.DASHBOARD) Icons.Default.Dashboard else Icons.Outlined.Dashboard,
                                            contentDescription = "Dashboard"
                                        )
                                    },
                                    label = { Text("Dashboard") },
                                    modifier = Modifier.testTag("nav_item_dashboard")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.SESSIONS,
                                    onClick = { viewModel.navigateTo(AppScreen.SESSIONS) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentScreen == AppScreen.SESSIONS) Icons.Default.LocalLaundryService else Icons.Outlined.LocalLaundryService,
                                            contentDescription = "Sessions"
                                        )
                                    },
                                    label = { Text("Sessions") },
                                    modifier = Modifier.testTag("nav_item_sessions")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.AI_DRESS_SCAN,
                                    onClick = { viewModel.navigateTo(AppScreen.AI_DRESS_SCAN) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentScreen == AppScreen.AI_DRESS_SCAN) Icons.Default.AutoAwesome else Icons.Outlined.AutoAwesome,
                                            contentDescription = "AI Matcher"
                                        )
                                    },
                                    label = { Text("AI Matcher") },
                                    modifier = Modifier.testTag("nav_item_ai_scan")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.CUSTOMERS,
                                    onClick = { viewModel.navigateTo(AppScreen.CUSTOMERS) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentScreen == AppScreen.CUSTOMERS) Icons.Default.People else Icons.Outlined.People,
                                            contentDescription = "Customers"
                                        )
                                    },
                                    label = { Text("Customers") },
                                    modifier = Modifier.testTag("nav_item_customers")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                            AppScreen.SESSIONS -> SessionsScreen(viewModel = viewModel)
                            AppScreen.SESSION_DETAIL -> SessionDetailScreen(viewModel = viewModel)
                            AppScreen.NEW_SESSION -> NewSessionScreen(viewModel = viewModel)
                            AppScreen.CUSTOMERS -> CustomersScreen(viewModel = viewModel)
                            AppScreen.CUSTOMER_DETAIL -> CustomerDetailScreen(viewModel = viewModel)
                            AppScreen.AI_DRESS_SCAN -> AiDressScanScreen(viewModel = viewModel)
                            AppScreen.CUSTOMER_PORTAL -> CustomerPortalScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
}
