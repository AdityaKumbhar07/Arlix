package com.arlix.shadowvault.ui.screens

import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.ui.SecureVaultTextField
import com.arlix.shadowvault.ui.components.CredentialListItem
import com.arlix.shadowvault.ui.components.CredentialDetailSheetContent
import com.arlix.shadowvault.ui.components.SecureCredentialCard
import com.arlix.shadowvault.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotVaultScreen(
    entries: List<VaultEntry>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onLock: () -> Unit,
    onOpenColdVault: (CharArray) -> Unit,
    onAddEntry: (VaultEntry) -> Unit,
    coldVaultExists: Boolean,
    onColdVaultCreate: (CharArray, CharArray) -> Unit,
    coldVaultError: String?,
    onDismissColdVaultError: () -> Unit
) {
            var showAddSheet by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<com.arlix.shadowvault.domain.VaultEntry?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showColdVaultModal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arlix", color = TextPrimary, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_lock), contentDescription = "Lock Vault", tint = TextPrimary)
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_more_vert), contentDescription = "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HotVaultCanvas)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = HotVaultAccent,
                contentColor = HotVaultCanvas
            ) {
                Icon(painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_plus), contentDescription = "Add Credential")
            }
        },
        containerColor = HotVaultCanvas
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            val categories = listOf("All", "General", "Finance", "Work")
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category, color = if (selectedCategory == category) HotVaultCanvas else TextPrimary) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HotVaultAccent,
                            selectedLabelColor = HotVaultCanvas
                        )
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    CredentialListItem(
                        title = entry.title,
                        username = entry.username,
                        isColdVault = false,
                        onClick = { selectedEntry = entry }
                    )
                }
            }
        }
    }

    
    selectedEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }, containerColor = HotVaultCanvas) {
            BottomSheetSecurity(showSheet = (selectedEntry != null)) { selectedEntry = null }
            CredentialDetailSheetContent(
                title = entry.title,
                username = entry.username,
                passwordSecret = entry.passwordSecret,
                category = entry.category,
                isColdVault = false,
                onClose = { selectedEntry = null },
                onDelete = { /* Dummy Delete */ }
            )
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = HotVaultCanvas) {
            BottomSheetSecurity(showSheet = showAddSheet) { showAddSheet = false }
            AddCredentialForm(onSave = {
                onAddEntry(it)
                showAddSheet = false
            }, isColdVault = false)
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { 
            showSettingsSheet = false 
            onDismissColdVaultError()
        }, containerColor = HotVaultCanvas) {
            BottomSheetSecurity(showSheet = showSettingsSheet) { showSettingsSheet = false }
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    IconButton(onClick = { showSettingsSheet = false }) {
                        Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_close), contentDescription = "Close Settings", tint = TextPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ColdVaultBorder),
                    onClick = {
                        showSettingsSheet = false
                        showColdVaultModal = true
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(ColdVaultCanvas, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_snowflake), contentDescription = null, tint = ColdVaultAccent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Chamber Isolation", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (coldVaultExists) "Access isolated offline credentials" else "Setup isolated offline credentials", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                            }
                        }
                        Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_chevron_right), contentDescription = "Unlock Cold Chamber", tint = TextPrimary)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = HotVaultBorder),
                    onClick = {
                        // Open developer link (No-op for now)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("About Developer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Arlix Security Team", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        }
                        Icon(Icons.Filled.OpenInNew, contentDescription = "About Developer", tint = TextPrimary)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showColdVaultModal) {
        ColdVaultModal(
            coldVaultExists = coldVaultExists,
            coldVaultError = coldVaultError,
            onDismiss = { 
                showColdVaultModal = false 
                onDismissColdVaultError()
            },
            onOpenColdVault = onOpenColdVault,
            onColdVaultCreate = onColdVaultCreate
        )
    }
}

@Composable
fun ColdVaultModal(
    coldVaultExists: Boolean,
    coldVaultError: String?,
    onDismiss: () -> Unit,
    onOpenColdVault: (CharArray) -> Unit,
    onColdVaultCreate: (CharArray, CharArray) -> Unit
) {
    var coldPassword by remember { mutableStateOf("") }
    var coldConfirm by remember { mutableStateOf("") }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = ColdVaultCanvas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = TextPrimary
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(ColdVaultBorder, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(if (coldVaultExists) com.arlix.shadowvault.R.drawable.ic_lock else com.arlix.shadowvault.R.drawable.ic_key),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = ColdVaultAccent
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (coldVaultExists) "Unlock Cold Chamber" else "Setup Cold Chamber",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (coldVaultExists) "Enter your isolated vault key" else "Create a separate key for offline credentials",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                SecureVaultTextField(
                    value = coldPassword,
                    onValueChange = { coldPassword = it },
                    label = if (coldVaultExists) "Cold Vault Key" else "New Cold Vault Key",
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (!coldVaultExists) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SecureVaultTextField(
                        value = coldConfirm,
                        onValueChange = { coldConfirm = it },
                        label = "Confirm Key",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                if (coldVaultError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(coldVaultError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (coldVaultExists) onOpenColdVault(coldPassword.toCharArray())
                        else onColdVaultCreate(coldPassword.toCharArray(), coldConfirm.toCharArray())
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = coldPassword.isNotEmpty() && (coldVaultExists || coldConfirm.isNotEmpty()),
                    colors = ButtonDefaults.buttonColors(containerColor = ColdVaultAccent)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (coldVaultExists) "Unlock vault" else "Setup key",
                            style = MaterialTheme.typography.labelLarge,
                            color = ColdVaultCanvas
                        )
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = ColdVaultCanvas,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColdVaultScreen(
    entries: List<VaultEntry>,
    onBack: () -> Unit,
    onAddEntry: (VaultEntry) -> Unit
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<com.arlix.shadowvault.domain.VaultEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cold Chamber", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_arrow_left), contentDescription = "Lock Cold Vault", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ColdVaultCanvas)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = ColdVaultAccent,
                contentColor = ColdVaultCanvas
            ) {
                Icon(painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_plus), contentDescription = "Add Credential")
            }
        },
        containerColor = ColdVaultCanvas
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            items(entries) { entry ->
                CredentialListItem(
                    title = entry.title,
                    username = entry.username,
                    isColdVault = true,
                    onClick = { selectedEntry = entry }
                )
            }
        }
    }

    selectedEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }, containerColor = ColdVaultCanvas) {
            BottomSheetSecurity(showSheet = (selectedEntry != null)) { selectedEntry = null }
            CredentialDetailSheetContent(
                title = entry.title,
                username = entry.username,
                passwordSecret = entry.passwordSecret,
                category = entry.category,
                isColdVault = true,
                onClose = { selectedEntry = null },
                onDelete = { /* Dummy Delete */ }
            )
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = ColdVaultCanvas) {
            BottomSheetSecurity(showSheet = showAddSheet) { showAddSheet = false }
            AddCredentialForm(onSave = {
                onAddEntry(it)
                showAddSheet = false
            }, isColdVault = true)
        }
    }
}

@Composable
fun AddCredentialForm(onSave: (VaultEntry) -> Unit, isColdVault: Boolean) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().padding(bottom = 8.dp).verticalScroll(rememberScrollState())) {
        Text("Add Credential", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        SecureVaultTextField(value = password, onValueChange = { password = it }, label = "Password", modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        
        // Category selection
        if (!isColdVault) {
            Text("Category", style = MaterialTheme.typography.labelMedium)
            val categories = listOf("General", "Finance", "Work")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                onSave(VaultEntry(title = title, username = username, notes = notes, passwordSecret = password.toCharArray(), category = category))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = if (isColdVault) ColdVaultAccent else HotVaultAccent)
        ) {
            Text("Save Credential", color = if (isColdVault) ColdVaultCanvas else HotVaultCanvas)
        }
    }
}
