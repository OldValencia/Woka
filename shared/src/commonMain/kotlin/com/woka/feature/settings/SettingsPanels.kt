package com.woka.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.SegmentedControl

@Composable
fun AppearanceSettingsPanel(
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel("Appearance")
        Spacer(modifier = Modifier.height(10.dp))
        SegmentedControl(
            options = listOf("System", "Light", "Dark"),
            selected = currentTheme,
            onSelect = onThemeChange
        )
    }
}

@Composable
fun LibrarySettingsPanel(
    globalShowTitles: Boolean,
    onGlobalShowTitlesChange: (Boolean) -> Unit,
    showHiddenFiles: Boolean,
    onShowHiddenFilesChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionLabel("Library")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = "Show Item Titles",
            subtitle = "Display file names under thumbnails everywhere",
            checked = globalShowTitles,
            onCheckedChange = onGlobalShowTitlesChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsToggleRow(
            title = "Show Hidden Files",
            subtitle = "Display files and folders hidden by the OS",
            checked = showHiddenFiles,
            onCheckedChange = onShowHiddenFilesChange
        )
    }
}
