package com.mikotrack.ui.history

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikotrack.R
import com.mikotrack.export.ExportManager

/** Lets the user choose the format in which the trip is exported. */
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportManager.Format) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_detail_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ExportManager.Format.entries.forEach { format ->
                    Text(
                        text = stringResource(format.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExport(format) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
