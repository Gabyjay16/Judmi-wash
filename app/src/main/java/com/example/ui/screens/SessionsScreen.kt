package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.DressPhotoThumbnail
import com.example.ui.components.PokedBadge
import com.example.ui.components.StatusBadge
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.LaundryViewModel
import com.example.util.CurrencyUtils
import java.util.Locale

enum class SessionFilter(val title: String) {
    ALL("All Sessions"),
    OPEN("In Progress"),
    COMPLETED("Completed")
}

@Composable
fun SessionsScreen(
    viewModel: LaundryViewModel,
    modifier: Modifier = Modifier
) {
    val allSessionsWithDresses by viewModel.allSessionsWithDresses.collectAsState()
    var selectedFilter by remember { mutableStateOf(SessionFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(allSessionsWithDresses, selectedFilter, searchQuery) {
        allSessionsWithDresses.filter { item ->
            val matchesFilter = when (selectedFilter) {
                SessionFilter.ALL -> true
                SessionFilter.OPEN -> !item.session.isCompleted
                SessionFilter.COMPLETED -> item.session.isCompleted
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                item.session.customerName.contains(searchQuery, ignoreCase = true) ||
                        item.session.customerPhone.contains(searchQuery, ignoreCase = true) ||
                        item.session.sessionCode.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.navigateTo(AppScreen.NEW_SESSION) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("sessions_fab_add")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Session")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("sessions_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Screen Title
            item {
                Column {
                    Text(
                        text = "Customer Laundry Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track dress loads, prices and completion status",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by customer, phone, code...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sessions_search_input")
                )
            }

            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SessionFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("session_filter_${filter.name}")
                        )
                    }
                }
            }

            // Session List
            if (filteredList.isEmpty()) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No laundry sessions found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try adjusting your search filter or create a new session.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredList) { sessionWithDresses ->
                    val session = sessionWithDresses.session
                    val hasPoked = sessionWithDresses.dresses.any { it.isPoked }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSessionDetail(session.id) }
                            .testTag("session_item_${session.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Customer Info + Checkbox
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = session.customerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        StatusBadge(isCompleted = session.isCompleted)
                                    }
                                    Text(
                                        text = "${session.sessionCode} • Phone: ${session.customerPhone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Done?",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Checkbox(
                                        checked = session.isCompleted,
                                        onCheckedChange = { checked ->
                                            viewModel.toggleSessionCompletion(session.id, checked)
                                        },
                                        modifier = Modifier.testTag("checkbox_complete_${session.id}")
                                    )
                                }
                            }

                            if (hasPoked) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val pokeMsg = sessionWithDresses.dresses.firstOrNull { it.isPoked }?.pokedMessage.orEmpty()
                                PokedBadge(message = pokeMsg)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Dress thumbnail row
                            if (sessionWithDresses.dresses.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(sessionWithDresses.dresses) { dress ->
                                        DressPhotoThumbnail(
                                            imageUri = dress.imageUri,
                                            dressName = dress.dressName,
                                            modifier = Modifier.size(58.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Bottom Bar: Price, Cost, Profit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${sessionWithDresses.dresses.size} dress item(s) • ${session.serviceType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Charge: ${CurrencyUtils.formatCfa(session.price)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Profit: ${CurrencyUtils.formatCfa(session.profit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
