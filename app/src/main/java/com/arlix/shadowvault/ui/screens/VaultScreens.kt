package com.arlix.shadowvault.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.ViewCompat.animate
import com.arlix.shadowvault.BuildConfig
import com.arlix.shadowvault.R
import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.ui.PlainTextKeyboardOptions
import com.arlix.shadowvault.ui.SecurePassphraseField
import com.arlix.shadowvault.ui.components.CredentialDetailSheetContent
import com.arlix.shadowvault.ui.components.CredentialListItem
import com.arlix.shadowvault.ui.theme.*
import com.arlix.shadowvault.ui.toSecretChars

// ---------------------------------------------------------------------------
// HOT VAULT
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotVaultScreen(
    entries: List<VaultEntry>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onLock: () -> Unit,
    onOpenColdVault: (CharArray) -> Unit,
    onAddEntry: (VaultEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    coldVaultExists: Boolean,
    onColdVaultCreate: (CharArray, CharArray) -> Unit,
    coldVaultError: String?,
    onDismissColdVaultError: () -> Unit
) {
    var showAddSheet by remember { mutableStateOf(false) }
    // The UI keeps only entry IDs, never entry objects: the ViewModel owns (and wipes) the
    // password arrays, so a stale object could point at an already-wiped password.
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showColdVaultModal by remember { mutableStateOf(false) }

    val selectedEntry = entries.firstOrNull { it.id == selectedId }
    val editingEntry = entries.firstOrNull { it.id == editingId }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Arlix", color = TextPrimary, fontWeight = FontWeight.Bold)},
                    actions = {
                        IconButton(onClick = onLock) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lock),
                                contentDescription = "Lock Vault",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_gear),
                                contentDescription = "Settings",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HotVaultCanvas)
                )
                HorizontalDivider(
                    modifier = Modifier.offset(y = 5.dp),
                    color = HotVaultBorder,
                    thickness = 1.dp
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = HotVaultAccent,
                contentColor = HotVaultCanvas
            ) {
                Icon(painter = painterResource(R.drawable.ic_plus), contentDescription = "Add Credential")
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
                        shape = RoundedCornerShape(50),
                        label = {
                            Text(
                                category,
                                color = if (selectedCategory == category) HotVaultCanvas else TextPrimary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HotVaultAccent,
                            selectedLabelColor = HotVaultCanvas,
                            containerColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == category,
                            borderColor = if (selectedCategory == category) HotVaultAccent else HotVaultBorder
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.offset(y = -3.dp),
                color = HotVaultBorder,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(15.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { entry ->
                    CredentialListItem(
                        title = entry.title,
                        username = entry.username,
                        isColdVault = false,
                        onClick = { selectedId = entry.id }
                    )
                }
            }
        }
    }

    // --- Detail sheet ---
    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = HotVaultCanvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ObscuredTouchGuard()
            BottomSheetSecurity(showSheet = true) { selectedId = null }
            CredentialDetailSheetContent(
                title = entry.title,
                username = entry.username,
                passwordSecret = entry.passwordSecret,
                notes = entry.notes,
                category = entry.category,
                isColdVault = false,
                onClose = { selectedId = null },
                onEdit = { editingId = entry.id; selectedId = null },
                onDelete = { onDeleteEntry(entry.id); selectedId = null }
            )
        }
    }

    // --- Add / Edit sheet ---
    if (showAddSheet || editingEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false; editingId = null },
            containerColor = HotVaultCanvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ObscuredTouchGuard()
            BottomSheetSecurity(showSheet = true) { showAddSheet = false; editingId = null }
            AddCredentialForm(
                initial = editingEntry,
                onSave = {
                    onAddEntry(it)
                    showAddSheet = false
                    editingId = null
                },
                onClose = { showAddSheet = false; editingId = null },
                isColdVault = false
            )
        }
    }

    // --- Settings sheet ---
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSettingsSheet = false
                onDismissColdVaultError()
            },
            containerColor = HotVaultCanvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ObscuredTouchGuard()
            BottomSheetSecurity(showSheet = true) { showSettingsSheet = false }
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Settings & Info",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    IconButton(
                        onClick = { showSettingsSheet = false },
                        modifier = Modifier.size(40.dp).border(1.dp, HotVaultBorder, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close Settings",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = HotVaultBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(20.dp))

                Text("Chamber Isolation", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, HotVaultBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(ColdVaultCanvas, RoundedCornerShape(12.dp))
                                    .border(1.dp, ColdVaultBorder, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_snowflake),
                                    contentDescription = null,
                                    tint = ColdVaultAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Cold Vault Chamber", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Separate vault with its own passphrase",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showSettingsSheet = false
                                showColdVaultModal = true
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColdVaultAccent)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_snowflake),
                                    contentDescription = null,
                                    tint = ColdVaultCanvas,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (coldVaultExists) "Unlock Cold Chamber" else "Setup Cold Chamber",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ColdVaultCanvas
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text("About Developer", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, HotVaultBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Arlix", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFedf2e7), RoundedCornerShape(16.dp))
                                    .border(1.dp, Color(0xFFd5dfc9), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF9AAB84)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Crafted by Aditya Kumbhar.\nLocal-only password vault: AES-256 encrypted storage, Argon2id key derivation, and no network permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            lineHeight = 20.sp
                        )
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

// ---------------------------------------------------------------------------
// COLD VAULT DIALOG + SCREEN
// ---------------------------------------------------------------------------

@Composable
fun ColdVaultModal(
    coldVaultExists: Boolean,
    coldVaultError: String?,
    onDismiss: () -> Unit,
    onOpenColdVault: (CharArray) -> Unit,
    onColdVaultCreate: (CharArray, CharArray) -> Unit
) {
    val coldState = remember { TextFieldState() }
    val confirmState = remember { TextFieldState() }

    Dialog(onDismissRequest = onDismiss) {
        ObscuredTouchGuard()
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = TextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier.size(64.dp).background(ColdVaultBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (coldVaultExists) R.drawable.ic_lock else R.drawable.ic_key),
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
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                SecurePassphraseField(
                    state = coldState,
                    placeholder = if (coldVaultExists) "Cold Vault Key" else "New Cold Vault Key",
                    modifier = Modifier.fillMaxWidth().testTag("ColdVaultPasswordField")
                )

                if (!coldVaultExists) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurePassphraseField(
                        state = confirmState,
                        placeholder = "Confirm Key",
                        modifier = Modifier.fillMaxWidth().testTag("ColdVaultConfirmPasswordField")
                    )
                }

                if (coldVaultError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        coldVaultError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (coldVaultExists) onOpenColdVault(coldState.toSecretChars())
                        else onColdVaultCreate(coldState.toSecretChars(), confirmState.toSecretChars())
                        // Clear the fields after every attempt so a wrong key is retyped, not kept.
                        coldState.clearText()
                        confirmState.clearText()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = coldState.text.isNotEmpty() && (coldVaultExists || confirmState.text.isNotEmpty()),
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
                            painter = painterResource(R.drawable.ic_chevron_right),
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
    onAddEntry: (VaultEntry) -> Unit,
    onDeleteEntry: (String) -> Unit
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }

    val selectedEntry = entries.firstOrNull { it.id == selectedId }
    val editingEntry = entries.firstOrNull { it.id == editingId }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Arlix", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_left),
                                contentDescription = "Lock Cold Vault",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ColdVaultCanvas)
                )
                HorizontalDivider(color = ColdVaultBorder, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = ColdVaultAccent,
                contentColor = ColdVaultCanvas
            ) {
                Icon(painter = painterResource(R.drawable.ic_plus), contentDescription = "Add Credential")
            }
        },
        containerColor = ColdVaultCanvas
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            items(entries, key = { it.id }) { entry ->
                CredentialListItem(
                    title = entry.title,
                    username = entry.username,
                    isColdVault = true,
                    onClick = { selectedId = entry.id }
                )
            }
        }
    }

    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedId = null },
            containerColor = ColdVaultCanvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ObscuredTouchGuard()
            BottomSheetSecurity(showSheet = true) { selectedId = null }
            CredentialDetailSheetContent(
                title = entry.title,
                username = entry.username,
                passwordSecret = entry.passwordSecret,
                notes = entry.notes,
                category = entry.category,
                isColdVault = true,
                onClose = { selectedId = null },
                onEdit = { editingId = entry.id; selectedId = null },
                onDelete = { onDeleteEntry(entry.id); selectedId = null }
            )
        }
    }

    if (showAddSheet || editingEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false; editingId = null },
            containerColor = ColdVaultCanvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ObscuredTouchGuard()
            BottomSheetSecurity(showSheet = true) { showAddSheet = false; editingId = null }
            AddCredentialForm(
                initial = editingEntry,
                onSave = {
                    onAddEntry(it)
                    showAddSheet = false
                    editingId = null
                },
                onClose = { showAddSheet = false; editingId = null },
                isColdVault = true
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ADD / EDIT FORM
// ---------------------------------------------------------------------------

/** [initial] == null means "new credential"; otherwise the form edits that entry (same id). */

// Input limits keep a single row small enough to always load (Android cursors fail on huge rows).
private const val MAX_TITLE = 100
private const val MAX_USERNAME = 200
private const val MAX_NOTES = 4000

@Composable
fun AddCredentialForm(
    initial: VaultEntry? = null,
    onSave: (VaultEntry) -> Unit,
    onClose: () -> Unit,
    isColdVault: Boolean
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "General") }
    // remember (not rememberTextFieldState): the password must never be saved to the Bundle.
    // Editing pre-fills the existing password, which briefly creates one String copy.
    val passwordState = remember {
        TextFieldState(initialText = initial?.let { String(it.passwordSecret) } ?: "")
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (initial == null) "New Credential" else "Edit Credential",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp).border(1.dp, HotVaultBorder, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        val borderColor = if (isColdVault) ColdVaultBorder else HotVaultBorder
        val accentColor = if (isColdVault) ColdVaultAccent else HotVaultAccent
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = borderColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(20.dp))

        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = accentColor,
            unfocusedBorderColor = borderColor
        )

        Text("SERVICE / TITLE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = title,
           onValueChange = { if (it.length <= MAX_TITLE) title = it },
            placeholder = { Text("e.g. ProtonMail, GitHub", color = TextMuted) },
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors,
            keyboardOptions = PlainTextKeyboardOptions,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("USERNAME / EMAIL", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { if (it.length <= MAX_USERNAME) username = it },
            placeholder = { Text("e.g. user@example.com", color = TextMuted) },
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors,
            keyboardOptions = PlainTextKeyboardOptions,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("PASSWORD / SECRET", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        SecurePassphraseField(
            state = passwordState,
            placeholder = "Enter secret",
            modifier = Modifier.fillMaxWidth().testTag("AddCredentialPasswordField")
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (!isColdVault) {
            Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            val categories = listOf("General", "Finance", "Work")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = {
                            Text(
                                cat,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                color = if (category == cat) HotVaultCanvas else TextPrimary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HotVaultAccent,
                            containerColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
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
            value = notes,
            onValueChange = { if (it.length <= MAX_NOTES) notes = it },
            placeholder = { Text("Additional secret notes...", color = TextMuted) },
            shape = RoundedCornerShape(12.dp),
            colors = textFieldColors,
            keyboardOptions = PlainTextKeyboardOptions,
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val secret = passwordState.toSecretChars()
                val entry = if (initial != null) {
                    // Edit: same id and created date, new modified date.
                    VaultEntry(
                        id = initial.id,
                        title = title,
                        username = username,
                        notes = notes,
                        passwordSecret = secret,
                        category = category,
                        createdAt = initial.createdAt
                    )
                } else {
                    VaultEntry(
                        title = title,
                        username = username,
                        notes = notes,
                        passwordSecret = secret,
                        category = category
                    )
                }
                passwordState.clearText()
                onSave(entry)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = title.isNotBlank() && passwordState.text.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isColdVault) ColdVaultAccent else HotVaultAccent)
        ) {
            Text(
                if (initial == null) "Save Credential" else "Save Changes",
                style = MaterialTheme.typography.labelLarge,
                color = if (isColdVault) ColdVaultCanvas else HotVaultCanvas
            )
        }
    }
}
