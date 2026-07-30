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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
    val expanded = remember { mutableStateOf(true) }

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
            expanded = expanded.value,
            onToggle = { expanded.value = !expanded.value },
            onDismiss = onDismiss
        )

        // Issues list
        if (expanded.value && issues.isNotEmpty()) {
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
            imageVector = Icons.Default.BugReport,
            contentDescription = "Issues",
            tint = OnSurface,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "$totalIssues issues",
            style = MaterialTheme.typography.labelLarge,
            color = OnSurface
        )

        Spacer(Modifier.width(12.dp))

        if (errorCount > 0) {
            SeverityBadge(count = errorCount, color = ErrorColor, label = "errors")
        }
        if (warningCount > 0) {
            SeverityBadge(count = warningCount, color = WarningColor, label = "warnings")
        }
        if (infoCount > 0) {
            SeverityBadge(count = infoCount, color = InfoColor, label = "info")
        }

        Spacer(Modifier.weight(1f))

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
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            color = OnSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun IssueItem(
    issue: AnalysisIssue,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, color) = when (issue.severity) {
        Severity.ERROR -> Icons.Default.Info to ErrorColor
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
                Row {
                    Text(
                        text = "[L${issue.line + 1}:${issue.column + 1}]",
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
                }

                Text(
                    text = issue.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (issue.suggestion != null) {
                    Text(
                        text = "💡 ${issue.suggestion}",
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
