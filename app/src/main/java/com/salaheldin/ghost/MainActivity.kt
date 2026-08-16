package com.salaheldin.ghost

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salaheldin.ghost.ui.theme.GhostTheme
import com.salaheldin.ghost.ui.theme.PriorityHigh
import com.salaheldin.ghost.ui.theme.PriorityLow
import com.salaheldin.ghost.ui.theme.PriorityMedium
import com.salaheldin.ghost.ui.theme.Spacing
import com.salaheldin.ghost.ui.theme.ThemeMode

sealed class Screen {
    object Dashboard : Screen()
    data class Detail(val conversation: ConversationEntity) : Screen()
    object Settings : Screen()
    object Statistics : Screen()
}

// Human-readable labels — technical enum/status strings never shown to the user
fun statusLabel(status: String): String = when (status) {
    "WAITING_FOR_REPLY" -> "Waiting for reply"
    "REPLIED" -> "Replied"
    "NEW" -> "No reply needed"
    "IGNORED" -> "Ignored"
    "ARCHIVED" -> "Archived"
    else -> status
}

fun riskLabel(score: Int): String = when {
    score >= 70 -> "High"
    score >= 40 -> "Medium"
    else -> "Low"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(loadThemeMode(context)) }
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            GhostTheme(darkTheme = useDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GhostApp(
                        modifier = Modifier.padding(innerPadding),
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            saveThemeMode(context, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GhostApp(
    modifier: Modifier = Modifier,
    viewModel: GhostViewModel = viewModel(),
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !hasPermission -> OnboardingScreen(modifier = modifier)
        else -> when (val s = screen) {
            is Screen.Dashboard -> GhostDashboard(
                modifier = modifier,
                viewModel = viewModel,
                onConversationClick = { screen = Screen.Detail(it) },
                onSettingsClick = { screen = Screen.Settings }
            )
            is Screen.Detail -> ConversationDetailScreen(
                modifier = modifier,
                conversation = s.conversation,
                viewModel = viewModel,
                onBack = { screen = Screen.Dashboard }
            )
            is Screen.Settings -> SettingsScreen(
                modifier = modifier,
                viewModel = viewModel,
                onBack = { screen = Screen.Dashboard },
                onStatisticsClick = { screen = Screen.Statistics },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
            is Screen.Statistics -> StatisticsScreen(
                modifier = modifier,
                viewModel = viewModel,
                onBack = { screen = Screen.Settings }
            )
        }
    }
}

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("👻", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text("Welcome to Ghost", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            "Ghost needs Notification Access to detect messages you haven't replied to.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            "Your messages stay on this device — nothing is uploaded, shared, or sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("Enable Notification Access")
        }
    }
}

@Composable
fun GhostDashboard(
    modifier: Modifier = Modifier,
    viewModel: GhostViewModel,
    onConversationClick: (ConversationEntity) -> Unit,
    onSettingsClick: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val needsAttention = conversations.count { it.status == "WAITING_FOR_REPLY" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ghost_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Ghost", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Your conversations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (needsAttention > 0) {
            Text(
                text = "Needs your attention · $needsAttention",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
        }

        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No conversations yet 👻", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(conversations) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        viewModel = viewModel,
                        onMarkReplied = { viewModel.markAsReplied(conversation) },
                        onClick = { onConversationClick(conversation) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationCard(
    conversation: ConversationEntity,
    viewModel: GhostViewModel,
    onMarkReplied: () -> Unit,
    onClick: () -> Unit
) {
    val priorityColor = when (conversation.priority) {
        "HIGH" -> PriorityHigh
        "MEDIUM" -> PriorityMedium
        else -> PriorityLow
    }

    var baseline by remember(conversation.id) { mutableStateOf<BaselineCalculator.Baseline?>(null) }
    var isUnusual by remember(conversation.id) { mutableStateOf<Boolean?>(null) }
    var risk by remember(conversation.id) { mutableStateOf<RiskAssessment?>(null) }
    var expanded by remember(conversation.id) { mutableStateOf(false) }

    LaunchedEffect(conversation.id, conversation.status) {
        val info = viewModel.getRowInfo(conversation)
        baseline = info.baseline
        isUnusual = info.isUnusual
        risk = info.risk
    }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // Who
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(priorityColor, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // What happened
            Text(
                conversation.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Do I need to care + action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    statusLabel(conversation.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.status == "WAITING_FOR_REPLY") {
                    FilledTonalButton(
                        onClick = onMarkReplied,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark replied", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Baseline
            val b = baseline
            if (b != null && b.hasEnoughData) {
                val baselineSeconds = b.averageResponseTimeMs / 1000
                val baselineText = if (baselineSeconds < 60) {
                    "Usually ~${baselineSeconds}s"
                } else {
                    "Usually ~${baselineSeconds / 60}m"
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isUnusual == true) "⚠️ $baselineText — longer than usual" else baselineText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUnusual == true) PriorityHigh else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Why — Ghost Risk
            val r = risk
            if (r != null && r.score > 0) {
                val riskColor = when {
                    r.score >= 70 -> PriorityHigh
                    r.score >= 40 -> PriorityMedium
                    else -> PriorityLow
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { expanded = !expanded }
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(riskColor, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        "Ghost Risk · ${r.score} · ${riskLabel(r.score)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = riskColor
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        if (expanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded && r.reasons.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            "Why am I seeing this?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        r.reasons.forEach { reason ->
                            Text(
                                "• $reason",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationDetailScreen(
    modifier: Modifier = Modifier,
    conversation: ConversationEntity,
    viewModel: GhostViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.getMessagesFlow(conversation.id).collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg)
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(conversation.displayName, style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages stored yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                items(messages) { message ->
                    Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                        Text(message.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        if (conversation.status == "WAITING_FOR_REPLY") {
            Button(
                onClick = {
                    viewModel.markAsReplied(conversation)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mark replied")
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: GhostViewModel,
    onBack: () -> Unit,
    onStatisticsClick: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val conversations by viewModel.conversations.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg)
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemeMode.entries.forEach { mode ->
                    val selected = themeMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onThemeModeChange(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        },
                        modifier = Modifier.padding(end = Spacing.sm)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text("Data", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                "${conversations.size} conversations stored on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Ghost only stores data locally. Nothing is uploaded or shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onStatisticsClick) {
                Text("View Statistics")
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Manage Notification Access")
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = PriorityHigh)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = PriorityHigh)
            ) {
                Text("Delete All Data")
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently deletes every conversation and message stored by Ghost on this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllData()
                    showDeleteConfirm = false
                    onBack()
                }) {
                    Text("Delete", color = PriorityHigh)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: GhostViewModel,
    onBack: () -> Unit
) {
    var stats by remember { mutableStateOf<GhostViewModel.Statistics?>(null) }

    LaunchedEffect(Unit) {
        stats = viewModel.getStatistics()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(Spacing.lg)
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back")
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text("Your Stats 👻", style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        val s = stats
        if (s == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                StatRow("Messages detected", s.totalMessages.toString())
                StatRow("Replies needed", s.repliesNeeded.toString())
                StatRow("Replies completed", s.repliesCompleted.toString())
                StatRow("Response rate", "${s.responseRatePercent}%")

                val avgSeconds = s.averageResponseTimeMs / 1000
                val avgText = when {
                    s.averageResponseTimeMs == 0L -> "Not enough data yet"
                    avgSeconds < 60 -> "~${avgSeconds}s"
                    else -> "~${avgSeconds / 60}m"
                }
                StatRow("Average response time", avgText)
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}