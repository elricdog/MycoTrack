package com.mikotrack.ui.update

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mikotrack.BuildConfig
import com.mikotrack.R
import com.mikotrack.update.GitHubRelease
import com.mikotrack.update.releaseVersion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The changelog of the application: every published release, newest first, with the
 * notes written at publication time. The list is fetched live from the GitHub releases,
 * so it also shows the versions newer than the installed one.
 */
@Composable
fun ChangelogDialog(
    fetch: suspend () -> List<GitHubRelease>,
    onDismiss: () -> Unit,
) {
    var releases by remember { mutableStateOf<List<GitHubRelease>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(retry) {
        releases = null
        failed = false
        runCatching { fetch() }
            .onSuccess { releases = it }
            .onFailure { failed = true }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.changelog_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                }
                HorizontalDivider()
                val list = releases
                when {
                    list != null && list.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        items(list) { release ->
                            ChangelogEntry(release)
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }

                    list != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.changelog_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.changelog_error),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            TextButton(onClick = { retry++ }) {
                                Text(stringResource(R.string.changelog_retry))
                            }
                        }
                    }

                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogEntry(release: GitHubRelease) {
    val version = releaseVersion(release.tagName)
    val installed = version == BuildConfig.VERSION_NAME
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.name.ifBlank { release.tagName },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (installed) {
                AssistChip(
                    onClick = { },
                    label = { Text(stringResource(R.string.changelog_installed)) },
                )
            }
        }
        val date = formatReleaseDate(release.publishedAt)
        if (date.isNotEmpty()) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val notes = cleanMarkdown(release.body)
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "2026-11-17T14:22:18Z" to a short local date; empty when unparseable. */
@Composable
private fun formatReleaseDate(publishedAt: String): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern(
        stringResource(R.string.changelog_date_pattern),
    ).withZone(ZoneId.systemDefault())
    formatter.format(Instant.parse(publishedAt))
}.getOrDefault("")

/**
 * Makes the release notes readable as plain text: the Markdown of the GitHub releases
 * (headings, emphasis, bullets) is reduced to its content.
 */
private fun cleanMarkdown(body: String?): String = body
    ?.lines()
    ?.joinToString("\n") { line ->
        line.trim()
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
            .replace("`", "")
            .replace(Regex("^[-*] "), "• ")
    }
    ?.trim()
    .orEmpty()
