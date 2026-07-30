package com.kodereview.app.ui.screen.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodereview.app.analyzer.AnalysisIssue
import com.kodereview.app.analyzer.Severity
import com.kodereview.app.ui.theme.*

@Composable
fun DiagnosticPanel(
    issues: List<AnalysisIssue>,
    totalIssues: Int,
    errorCount: Int,
    warningCount: Int,
    infoCount: Int,
    selectedIssue: AnalysisIssue?,
    onIssueClick: (AnalysisIssue) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .background(SurfaceColor)
            .animateContentSize()
    ) {
        // Header / summary bar
        DiagnosticSummaryBar(
            totalIssues = totalIssues,
            errorCount = errorCount,
            warningCount = warningCount,
            infoCount = infoCount,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            onDismiss = onDismiss
        )

        // Issues list
        if (expanded && issues.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(issues, key = { "${it.line}:${it.column}:${it.ruleId}" }) { issue ->
                    IssueItem(
                        issue = issue,
                        isSelected = issue == selectedIssue,
                        onClick = { onIssueClick(issue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSummaryBar(
    totalIssues: Int,
    errorCount: Int,
    warningCount: Int,
    infoCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (errorCount > 0) Icons.Default.Warning else Icons.Default.Info,
            contentDescription = "Issues",
            tint = if (errorCount > 0) ErrorColor else OnSurface,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "$totalIssues issue${if (totalIssues != 1) "s" else ""}",
            style = MaterialTheme.typography.labelLarge,
            color = OnSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(12.dp))

        if (errorCount > 0) {
            SeverityBadge(count = errorCount, color = ErrorColor, label = "error${if (errorCount != 1) "s" else ""}")
        }
        if (warningCount > 0) {
            SeverityBadge(count = warningCount, color = WarningColor, label = "warning${if (warningCount != 1) "s" else ""}")
        }
        if (infoCount > 0) {
            SeverityBadge(count = infoCount, color = InfoColor, label = "info")
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = if (expanded) "▾" else "▸",
            color = OnSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(Modifier.width(4.dp))

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close panel",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SeverityBadge(count: Int, color: Color, label: String) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IssueItem(
    issue: AnalysisIssue,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, color) = when (issue.severity) {
        Severity.ERROR -> Icons.Default.Clear to ErrorColor
        Severity.WARNING -> Icons.Default.Warning to WarningColor
        Severity.INFO, Severity.SUGGESTION -> Icons.Default.Info to InfoColor
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        color = if (isSelected) SurfaceVariant else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "L${issue.line + 1}:${issue.column + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = OnSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = issue.ruleId,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = color,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = issue.severity.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = color.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = issue.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (issue.suggestion != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "→ ${issue.suggestion}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = PrimaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
