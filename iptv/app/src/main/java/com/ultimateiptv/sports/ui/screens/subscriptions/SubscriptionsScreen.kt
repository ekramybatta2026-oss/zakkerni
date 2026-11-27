package com.ultimateiptv.sports.ui.screens.subscriptions

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.core.network.ErrorMapper
import com.ultimateiptv.sports.data.db.SubscriptionEntity
import com.ultimateiptv.sports.data.db.SubscriptionType
import com.ultimateiptv.sports.data.db.SyncStatus
import com.ultimateiptv.sports.data.iptv.ConnectionTestResult
import com.ultimateiptv.sports.data.iptv.RefreshProgress
import com.ultimateiptv.sports.data.iptv.SubscriptionInput
import com.ultimateiptv.sports.data.iptv.SubscriptionRepository
import com.ultimateiptv.sports.ui.components.EmptyState
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.ScreenHeader
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.navigation.AppActions
import com.ultimateiptv.sports.ui.navigation.Routes
import com.ultimateiptv.sports.ui.theme.LiveRed
import com.ultimateiptv.sports.ui.theme.Pitch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(private val repo: SubscriptionRepository) : ViewModel() {
    val subscriptions: StateFlow<List<SubscriptionEntity>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = repo.progress
    val lastError = MutableStateFlow<Throwable?>(null)

    fun refresh(id: Long) = viewModelScope.launch {
        repo.refresh(id).onFailure { lastError.value = it }.onSuccess { s -> s.epgError?.let { lastError.value = it } }
    }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun addDemo() = viewModelScope.launch { repo.addDemo() }
}

@Composable
fun SubscriptionsScreen(actions: AppActions, vm: SubscriptionsViewModel = hiltViewModel()) {
    val subs by vm.subscriptions.collectAsState()
    val progress by vm.progress.collectAsState()
    val error by vm.lastError.collectAsState()
    var confirmDelete by remember { mutableStateOf<SubscriptionEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { actions.open(Routes.subEdit()) }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_subscription)) }, modifier = Modifier.tvFocusBorder())
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(stringResource(R.string.subscriptions), onBack = { actions.back() })
            if (subs.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.empty_subs_title), stringResource(R.string.empty_subs_message)) {
                    OutlinedButton(onClick = { vm.addDemo() }, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.try_demo)) }
                }
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
                error?.let { e -> item { Text(stringResource(ErrorMapper.messageRes(e)) + " (${ErrorMapper.detail(e)})", color = LiveRed, modifier = Modifier.padding(12.dp)) } }
                items(subs, key = { it.id }) { s ->
                    SubscriptionCard(
                        s, progress[s.id],
                        onRefresh = { vm.refresh(s.id) },
                        onEdit = { actions.open(Routes.subEdit(s.id)) },
                        onDelete = { confirmDelete = s },
                    )
                }
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_subscription)) },
            text = { Text(stringResource(R.string.delete_subscription_message, s.name)) },
            confirmButton = { TextButton(onClick = { vm.delete(s.id); confirmDelete = null }) { Text(stringResource(R.string.delete), color = LiveRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SubscriptionCard(s: SubscriptionEntity, progress: RefreshProgress?, onRefresh: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    FocusCard(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    progress != null || s.status == SyncStatus.SYNCING -> Icons.Filled.Schedule to MaterialTheme.colorScheme.tertiary
                    s.status == SyncStatus.OK -> Icons.Filled.CheckCircle to Pitch
                    s.status == SyncStatus.ERROR -> Icons.Filled.Error to LiveRed
                    else -> Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, null, tint = tint)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text("${typeLabel(s.type)} · ${s.displayHost}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = progress == null, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
                IconButton(onClick = onEdit, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                IconButton(onClick = onDelete, modifier = Modifier.tvFocusBorder()) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
            Spacer(Modifier.height(6.dp))
            when (progress) {
                is RefreshProgress.Downloading -> { Text(stringResource(R.string.sync_channels, progress.channels), style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp)) }
                is RefreshProgress.Epg -> { Text(stringResource(R.string.sync_epg, progress.programmes), style = MaterialTheme.typography.bodySmall); LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp)) }
                null -> {
                    val parts = buildList {
                        add(stringResource(R.string.channels_count, s.channelCount))
                        if (s.programmeCount > 0) add(stringResource(R.string.programmes_count, s.programmeCount))
                        s.lastRefreshAt?.let { add(stringResource(R.string.updated_at, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)))) }
                        s.expiresAt?.let { add(stringResource(R.string.expires_at, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))) }
                        s.maxConnections?.let { add(stringResource(R.string.max_connections, it)) }
                    }
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.status == SyncStatus.ERROR) Text(stringResource(R.string.sync_failed) + (s.lastError?.let { ": $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = LiveRed, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun typeLabel(t: SubscriptionType) = stringResource(
    when (t) {
        SubscriptionType.XTREAM -> R.string.type_xtream
        SubscriptionType.M3U_URL -> R.string.type_m3u_url
        SubscriptionType.M3U_FILE -> R.string.type_m3u_file
        SubscriptionType.DEMO -> R.string.type_demo
    }
)

// ------------------------- Editor -------------------------

data class EditorState(
    val loaded: Boolean = false,
    val id: Long = 0,
    val type: SubscriptionType = SubscriptionType.XTREAM,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val hasStoredPassword: Boolean = false,
    val epgUrl: String = "",
    val userAgent: String = "",
    val outputFormat: String = "m3u8",
    val autoRefreshHours: Int = 24,
)

@HiltViewModel
class SubscriptionEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: SubscriptionRepository,
) : ViewModel() {
    private val id: Long = savedState["id"] ?: 0L
    val state = MutableStateFlow(EditorState(loaded = id == 0L))
    val testing = MutableStateFlow(false)
    val testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val saving = MutableStateFlow(false)
    val saved = MutableStateFlow(false)

    init {
        if (id != 0L) viewModelScope.launch {
            val sub = repo.get(id) ?: return@launch
            val s = repo.secretsOf(sub)
            state.value = EditorState(
                loaded = true, id = id, type = sub.type, name = sub.name, url = s.url, username = s.username,
                password = "", hasStoredPassword = s.password.isNotEmpty(), epgUrl = s.epgUrl.orEmpty(),
                userAgent = sub.userAgent.orEmpty(), outputFormat = sub.outputFormat, autoRefreshHours = sub.autoRefreshHours,
            )
        }
    }

    fun update(f: (EditorState) -> EditorState) { state.value = f(state.value); testResult.value = null }

    private fun input(s: EditorState) = SubscriptionInput(
        name = s.name.trim(), type = s.type, url = s.url.trim(), username = s.username, password = s.password,
        epgUrl = s.epgUrl.trim().ifBlank { null }, userAgent = s.userAgent.trim().ifBlank { null },
        outputFormat = s.outputFormat, autoRefreshHours = s.autoRefreshHours,
    )

    fun valid(s: EditorState): Boolean = when (s.type) {
        SubscriptionType.XTREAM -> s.url.isNotBlank() && s.username.isNotBlank() && (s.password.isNotBlank() || s.hasStoredPassword)
        SubscriptionType.M3U_URL -> s.url.startsWith("http://", true) || s.url.startsWith("https://", true)
        SubscriptionType.M3U_FILE -> s.url.isNotBlank()
        SubscriptionType.DEMO -> true
    }

    fun test() = viewModelScope.launch {
        testing.value = true
        val s = state.value
        var inp = input(s)
        if (s.type == SubscriptionType.XTREAM && s.password.isEmpty() && s.id != 0L) {
            inp = inp.copy(password = repo.secrets(s.id)?.password.orEmpty())
        }
        testResult.value = repo.test(inp)
        testing.value = false
    }

    fun save() = viewModelScope.launch {
        saving.value = true
        val s = state.value
        val newId = if (s.id == 0L) repo.add(input(s)) else { repo.update(s.id, input(s)); s.id }
        saved.value = true
        saving.value = false
        repo.refresh(newId)
    }
}

@Composable
fun SubscriptionEditScreen(actions: AppActions, vm: SubscriptionEditViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val testing by vm.testing.collectAsState()
    val result by vm.testResult.collectAsState()
    val saving by vm.saving.collectAsState()
    val saved by vm.saved.collectAsState()
    val context = LocalContext.current
    var showPassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(saved) { if (saved) actions.back() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.update { it.copy(url = uri.toString(), name = it.name.ifBlank { uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "" }) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(if (s.id == 0L) R.string.add_subscription else R.string.edit_subscription), onBack = { actions.back() })
        if (!s.loaded) { CircularProgressIndicator(Modifier.padding(24.dp)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SubscriptionType.XTREAM, SubscriptionType.M3U_URL, SubscriptionType.M3U_FILE).forEach { t ->
                    FilterChip(selected = s.type == t, onClick = { vm.update { it.copy(type = t) } }, label = { Text(typeLabel(t)) }, modifier = Modifier.tvFocusBorder())
                }
            }
            Field(s.name, { v -> vm.update { it.copy(name = v) } }, R.string.field_name)
            when (s.type) {
                SubscriptionType.XTREAM -> {
                    Field(s.url, { v -> vm.update { it.copy(url = v) } }, R.string.field_server, KeyboardType.Uri, hint = "http://example.com:8080")
                    Field(s.username, { v -> vm.update { it.copy(username = v) } }, R.string.field_username)
                    OutlinedTextField(
                        value = s.password, onValueChange = { v -> vm.update { it.copy(password = v) } }, singleLine = true,
                        label = { Text(stringResource(R.string.field_password)) },
                        placeholder = { if (s.hasStoredPassword) Text(stringResource(R.string.password_stored)) },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) }
                        },
                        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
                    )
                    Text(stringResource(R.string.output_format), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("m3u8" to "HLS (m3u8)", "ts" to "MPEG-TS (ts)").forEach { (v, l) ->
                            FilterChip(selected = s.outputFormat == v, onClick = { vm.update { it.copy(outputFormat = v) } }, label = { Text(l) }, modifier = Modifier.tvFocusBorder())
                        }
                    }
                }
                SubscriptionType.M3U_URL -> Field(s.url, { v -> vm.update { it.copy(url = v) } }, R.string.field_m3u_url, KeyboardType.Uri, hint = "https://example.com/playlist.m3u")
                SubscriptionType.M3U_FILE -> OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().tvFocusBorder()) {
                    Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(8.dp))
                    Text(if (s.url.isBlank()) stringResource(R.string.choose_file) else Uri.parse(s.url).lastPathSegment ?: s.url, maxLines = 1)
                }
                SubscriptionType.DEMO -> Text(stringResource(R.string.type_demo))
            }
            Field(s.epgUrl, { v -> vm.update { it.copy(epgUrl = v) } }, R.string.field_epg_url, KeyboardType.Uri, hint = stringResource(R.string.field_epg_hint))
            Field(s.userAgent, { v -> vm.update { it.copy(userAgent = v) } }, R.string.field_user_agent, hint = stringResource(R.string.field_user_agent_hint))
            Text(stringResource(R.string.auto_refresh), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 6, 12, 24, 72).forEach { h ->
                    FilterChip(selected = s.autoRefreshHours == h, onClick = { vm.update { it.copy(autoRefreshHours = h) } },
                        label = { Text(if (h == 0) stringResource(R.string.off) else stringResource(R.string.hours, h)) }, modifier = Modifier.tvFocusBorder())
                }
            }
            Text(stringResource(R.string.credentials_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            result?.let { r ->
                val text = if (r.ok) buildString {
                    append(stringResource(R.string.test_ok))
                    r.channels?.let { append(" · ").append(stringResource(R.string.channels_count, it)) }
                    r.accountStatus?.let { append(" · ").append(it) }
                    r.expiresAt?.let { append(" · ").append(stringResource(R.string.expires_at, DateFormat.getDateInstance().format(Date(it)))) }
                } else stringResource(r.error?.let { ErrorMapper.messageRes(it) } ?: R.string.error_format) + (r.error?.let { " (${ErrorMapper.detail(it)})" } ?: "")
                Text(text, color = if (r.ok) Pitch else LiveRed, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                OutlinedButton(onClick = { vm.test() }, enabled = vm.valid(s) && !testing, modifier = Modifier.tvFocusBorder()) {
                    if (testing) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.test_connection))
                }
                Button(onClick = { vm.save() }, enabled = vm.valid(s) && !saving, modifier = Modifier.tvFocusBorder()) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, type: KeyboardType = KeyboardType.Text, hint: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(stringResource(label)) },
        placeholder = hint?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
    )
}
