package com.salaheldin.ghost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.salaheldin.ghost.ui.theme.GhostTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhostTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GhostDashboard(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun GhostDashboard(modifier: Modifier = Modifier, viewModel: GhostViewModel = viewModel()) {
    val conversations by viewModel.conversations.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "👻 Ghost",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No conversations yet 👻")
            }
        } else {
            LazyColumn {
                items(conversations) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        viewModel = viewModel,
                        onMarkReplied = { viewModel.markAsReplied(conversation) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationRow(
    conversation: ConversationEntity,
    viewModel: GhostViewModel,
    onMarkReplied: () -> Unit
) {
    val priorityColor = when (conversation.priority) {
        "HIGH" -> Color(0xFFE53935)
        "MEDIUM" -> Color(0xFFFB8C00)
        else -> Color(0xFF43A047)
    }

    var baseline by remember(conversation.id) { mutableStateOf<BaselineCalculator.Baseline?>(null) }
    var isUnusual by remember(conversation.id) { mutableStateOf<Boolean?>(null) }
    var risk by remember(conversation.id) { mutableStateOf<RiskAssessment?>(null) }
    var expanded by remember(conversation.id) { mutableStateOf(false) }

    LaunchedEffect(conversation.id, conversation.status) {
        val (b, unusual) = viewModel.getDelayInfo(conversation)
        baseline = b
        isUnusual = unusual
        risk = viewModel.getRiskAssessment(conversation)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(priorityColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.contactIdentifier, style = MaterialTheme.typography.titleMedium)
                Text(
                    conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    conversation.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                val b = baseline
                if (b != null && b.hasEnoughData) {
                    val baselineSeconds = b.averageResponseTimeMs / 1000
                    val baselineText = if (baselineSeconds < 60) {
                        "Usually replies in ~${baselineSeconds}s"
                    } else {
                        "Usually replies in ~${baselineSeconds / 60}m"
                    }
                    Text(
                        text = if (isUnusual == true) "⚠️ $baselineText — this is longer than usual" else baselineText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUnusual == true) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
                    )
                }

                val r = risk
                if (r != null && r.score > 0) {
                    val riskColor = when {
                        r.score >= 70 -> Color(0xFFE53935)
                        r.score >= 40 -> Color(0xFFFB8C00)
                        else -> Color(0xFF43A047)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { expanded = !expanded }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(riskColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Ghost Risk: ${r.score}",
                            style = MaterialTheme.typography.labelSmall,
                            color = riskColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (expanded) "▲" else "▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (expanded && r.reasons.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 4.dp, start = 12.dp)) {
                            Text(
                                "Why am I seeing this?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            r.reasons.forEach { reason ->
                                Text(
                                    "• $reason",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
            if (conversation.status == "WAITING_FOR_REPLY") {
                TextButton(onClick = onMarkReplied) {
                    Text("Mark Replied")
                }
            }
        }
    }
    HorizontalDivider()
}