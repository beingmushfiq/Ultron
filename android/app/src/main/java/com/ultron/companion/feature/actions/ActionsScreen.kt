package com.ultron.companion.feature.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultron.companion.core.designsystem.*
import com.ultron.companion.core.model.PendingApproval
import com.ultron.companion.core.model.UltronAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    viewModel: ActionsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions by viewModel.actions.collectAsState()
    val approvals by viewModel.pendingApprovals.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val feedback by viewModel.uiFeedback.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ACTION CENTER", style = MaterialTheme.typography.titleMedium.copy(color = CrimsonPrimary))
                        Text("AUTHORIZED WORKSTATION PROTOCOLS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadActions() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBlack)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Approval Queue Section ───────────────────────────────────────
            if (approvals.isNotEmpty()) {
                item {
                    Text(
                        text = "APPROVAL QUEUE (${approvals.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            color = CrimsonBright,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                items(approvals, key = { it.approval_id }) { approval ->
                    ApprovalCard(
                        approval = approval,
                        onApprove = { viewModel.resolveApproval(approval, true) },
                        onReject = { viewModel.resolveApproval(approval, false) }
                    )
                }
            }

            // ── Action Catalogue ─────────────────────────────────────────────
            item {
                Text(
                    text = "REMOTE CAPABILITIES CATALOGUE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        color = TextTertiary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (isLoading && actions.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CrimsonPrimary)
                    }
                }
            }

            items(actions, key = { it.id }) { action ->
                ActionItemRow(
                    action = action,
                    onExecute = { viewModel.executeAction(action) }
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    UltronCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = CrimsonDark.copy(alpha = 0.35f),
        borderColor = CrimsonPrimary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HIGH IMPACT CONFIRMATION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = CrimsonBright,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "${approval.time_remaining_sec}s remaining",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = approval.title,
            style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary)
        )
        Text(
            text = approval.detail,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GraphiteElevated),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("REJECT", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }

            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CONFIRM", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ActionItemRow(
    action: UltronAction,
    onExecute: () -> Unit
) {
    UltronCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = GraphitePanel
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.name,
                    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontSize = 12.sp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            RiskBadge(riskLevel = action.risk_level)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onExecute,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (action.isHighImpact) CrimsonDark else GraphiteElevated
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (action.isHighImpact) "EXECUTE (APPROVAL REQ)" else "RUN ACTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun RiskBadge(riskLevel: String) {
    val (label, bg, fg) = when (riskLevel.lowercase()) {
        "high_impact" -> Triple("HIGH IMPACT", CrimsonDark, CrimsonBright)
        "reversible" -> Triple("REVERSIBLE", GraphiteElevated, SignalGreen)
        else -> Triple("READ ONLY", GraphiteElevated, TextSecondary)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        )
    }
}
