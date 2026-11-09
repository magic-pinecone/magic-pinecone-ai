package org.mpc.calendar

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** Asks for calendar access when needed, then runs [onExport]; runs [onPermissionDenied] when access is refused. */
@Composable
internal fun CalendarExportButton(
    enabled: Boolean,
    onExport: suspend () -> Unit,
    onPermissionDenied: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    val export: () -> Unit = {
        scope.launch {
            isExporting = true
            try {
                onExport()
            } finally {
                isExporting = false
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) export() else scope.launch { onPermissionDenied() }
    }
    IconButton(
        enabled = enabled && !isExporting,
        onClick = {
            val granted = ClassCalendar.permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) export() else permissionLauncher.launch(ClassCalendar.permissions)
        },
    ) {
        Icon(Icons.Default.EditCalendar, contentDescription = "加入行事曆")
    }
}
