package com.arlix.shadowvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.ui.SecureVaultTextField
import com.arlix.shadowvault.ui.components.CredentialDetailSheetContent
import com.arlix.shadowvault.ui.components.CredentialListItem
import com.arlix.shadowvault.ui.theme.ColdVaultAccent
import com.arlix.shadowvault.ui.theme.ColdVaultBorder
import com.arlix.shadowvault.ui.theme.ColdVaultCanvas
import com.arlix.shadowvault.ui.theme.HotVaultAccent
import com.arlix.shadowvault.ui.theme.HotVaultBorder
import com.arlix.shadowvault.ui.theme.HotVaultCanvas
import com.arlix.shadowvault.ui.theme.TextMuted
import com.arlix.shadowvault.ui.theme.TextPrimary

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
                        Icon(painter = androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_lock), contentDescription = "Lock Vault", tint = TextPrimary,modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_gear), contentDescription = "Settings", tint = TextPrimary,modifier = Modifier.size(24.dp))

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
                            selectedLabelColor = HotVaultCanvas,
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
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
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }, containerColor = HotVaultCanvas, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = HotVaultCanvas, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            BottomSheetSecurity(showSheet = showAddSheet) { showAddSheet = false }
            AddCredentialForm(onSave = {
                onAddEntry(it)
                showAddSheet = false
            },
                onClose = { showAddSheet = false },
                isColdVault = false)
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = {
            showSettingsSheet = false
            onDismissColdVaultError()
        }, containerColor = HotVaultCanvas, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            BottomSheetSecurity(showSheet = showSettingsSheet) { showSettingsSheet = false }
            Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp).fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings & Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    IconButton(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier.size(40.dp).border(1.dp, HotVaultBorder, androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_close), contentDescription = "Close Settings", tint = TextPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Chamber Isolation Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chamber Isolation", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Chamber Isolation Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HotVaultBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(ColdVaultCanvas, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .border(1.dp, ColdVaultBorder, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_snowflake), contentDescription = null, tint = ColdVaultAccent, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Cold Vault Chamber", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Isolated high-stakes secret vault", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showSettingsSheet = false
                                showColdVaultModal = true
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColdVaultAccent)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_snowflake), contentDescription = null, tint = ColdVaultCanvas, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (coldVaultExists) "Unlock Cold Chamber" else "Setup Cold Chamber", style = MaterialTheme.typography.labelLarge, color = ColdVaultCanvas)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // About Developer Header
                Text("About Developer", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                Spacer(modifier = Modifier.height(12.dp))

                // About Developer Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HotVaultBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Arlix Security Suite", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Box(
                                modifier = Modifier
                                    .background(androidx.compose.ui.graphics.Color(0xFF7D9F81), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .border(1.dp, androidx.compose.ui.graphics.Color(0xFF2D503B), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("v1.0.0", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(
                                    0xFF254631
                                )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Crafted by Aditya Kumbhar.\nZero-knowledge local password management with hardware-backed chamber isolation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = HotVaultBorder)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically

                        ) {
                            Row(modifier = Modifier.clickable { /* No op */ }, verticalAlignment = Alignment.CenterVertically) {
                                Text("GitHub Profile", style = MaterialTheme.typography.labelMedium, color = HotVaultAccent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = HotVaultAccent, modifier = Modifier.size(14.dp))
                            }
                            Row(modifier = Modifier.clickable { /* No op */ }, verticalAlignment = Alignment.CenterVertically) {
                                Text("Repository", style = MaterialTheme.typography.labelMedium, color = HotVaultAccent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = HotVaultAccent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
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
                    placeholder = if (coldVaultExists) "Cold Vault Key" else "New Cold Vault Key",
                    modifier = Modifier.fillMaxWidth()
                )

                if (!coldVaultExists) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SecureVaultTextField(
                        value = coldConfirm,
                        onValueChange = { coldConfirm = it },
                        placeholder = "Confirm Key",
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
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }, containerColor = ColdVaultCanvas, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, containerColor = ColdVaultCanvas, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            BottomSheetSecurity(showSheet = showAddSheet) { showAddSheet = false }
            AddCredentialForm(onSave = {
                onAddEntry(it)
                showAddSheet = false
            },
                onClose = { showAddSheet = false },
                isColdVault = true)
        }
    }
}

@Composable
fun AddCredentialForm(onSave: (VaultEntry) -> Unit, onClose: () -> Unit, isColdVault: Boolean) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }

    Column(modifier = Modifier.padding(24.dp).fillMaxWidth().padding(bottom = 8.dp).verticalScroll(rememberScrollState())) {
        // 1. Header with Close Button
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("New Credential", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp).border(1.dp, HotVaultBorder, androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(androidx.compose.ui.res.painterResource(com.arlix.shadowvault.R.drawable.ic_close), contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        // 2. Outer Labels & Rounded TextFields
        Text("SERVICE / TITLE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            placeholder = { Text("e.g. ProtonMail, GitHub", color = TextMuted) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("USERNAME / EMAIL", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            placeholder = { Text("e.g. user@example.com", color = TextMuted) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("PASSWORD / SECRET", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        SecureVaultTextField(
            value = password, onValueChange = { password = it },
            placeholder = "Enter secret",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 3. Category Chips with weights
        if (!isColdVault) {
            Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            val categories = listOf("General", "Finance", "Work")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                categories.forEach { cat ->
                    androidx.compose.material3.FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = if (category == cat) HotVaultCanvas else TextPrimary) },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HotVaultAccent,
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = (category == cat),
                            borderColor = HotVaultBorder
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("NOTES", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            placeholder = { Text("Additional secret notes...", color = TextMuted) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSave(VaultEntry(title = title, username = username, notes = notes, passwordSecret = password.toCharArray(), category = category)) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = title.isNotBlank() && password.isNotBlank(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isColdVault) ColdVaultAccent else HotVaultAccent)
        ) {
            Text("Save Credential", style = MaterialTheme.typography.labelLarge, color = if (isColdVault) ColdVaultCanvas else HotVaultCanvas)
        }
    }
}
