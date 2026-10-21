package com.prism.gallery.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.gallery.BuildConfig
import com.prism.gallery.LibraryViewModel
import com.prism.gallery.core.designsystem.*

@Composable fun SettingsScreen(vm: LibraryViewModel, preferences: Preferences, onPermission: () -> Unit) {
    val context = LocalContext.current
    var privacy by remember { mutableStateOf(false) }; var licenses by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 125.dp)) {
        GlassToolbar("Settings", "Considered details. Personal choices.")
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Appearance")
            Text("Theme", style = MaterialTheme.typography.labelMedium)
            GlassSegmentedControl(listOf("System", "Light", "Dark"), preferences.theme) { vm.launchAction { vm.app.settings.string("theme", it) } }
            Text("Glass quality", style = MaterialTheme.typography.labelMedium)
            GlassSegmentedControl(listOf("Auto", "High", "Balanced", "Reduced"), preferences.glass) { vm.launchAction { vm.app.settings.string("glass", it) } }
            Text("Auto uses a lighter material on older and low-memory devices. Advanced backdrop effects require Android 13 or later. Reduced keeps controls clear and inexpensive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("Library")
            Text("Grid · ${preferences.columns} columns", style = MaterialTheme.typography.labelMedium)
            GlassSlider(preferences.columns.toFloat(), 2f..6f, { vm.launchAction { vm.app.settings.int("columns", it.toInt()) } }, steps = 3)
            Text("Date grouping", style = MaterialTheme.typography.labelMedium)
            GlassSegmentedControl(listOf("Month", "Day", "None"), preferences.grouping) { vm.launchAction { vm.app.settings.string("grouping", it) } }
            SettingSwitch("Oldest first", preferences.oldest) { vm.launchAction { vm.app.settings.boolean("oldest", it) } }
            SettingSwitch("Touch haptics", preferences.haptics) { vm.launchAction { vm.app.settings.boolean("haptics", it) } }
            SettingSwitch("Interface motion", preferences.motion) { vm.launchAction { vm.app.settings.boolean("motion", it) } }
            Text("System animation settings also apply.", style = MaterialTheme.typography.bodySmall)
            SectionLabel("Categories")
            SettingSwitch("Show hidden categories", preferences.showHidden) { vm.launchAction { vm.app.settings.boolean("hidden", it) } }
            GlassSegmentedControl(listOf("Custom", "Name", "Newest"), preferences.categorySort) { vm.launchAction { vm.app.settings.string("categorySort", it) } }
            Text("Category ordering", style = MaterialTheme.typography.bodySmall)
            SectionLabel("Private Vault")
            Text("Idle lock timeout", style = MaterialTheme.typography.labelMedium)
            GlassSegmentedControl(listOf("30 sec", "1 min", "5 min"), when (preferences.vaultTimeout) { 30 -> "30 sec"; 300 -> "5 min"; else -> "1 min" }) { vm.launchAction { vm.app.settings.int("vaultTimeout", when (it) { "30 sec" -> 30; "5 min" -> 300; else -> 60 }) } }
            Text("Always locks on app background and screen-off. Biometric enrollment and manual lock are inside the vault. App data removal and reinstall destroy access; there is no portable backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("This device")
            OutlinedButton({ vm.refresh() }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Refresh, null); Text(" Rescan library") }
            OutlinedButton(onPermission, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PhotoLibrary, null); Text(" Manage / reselect media access") }
            OutlinedButton({ privacy = true }, Modifier.fillMaxWidth()) { Text("Privacy & storage") }
            OutlinedButton({ licenses = true }, Modifier.fillMaxWidth()) { Text("Attribution & licenses") }
            TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/okthisisitright/prism-gallery"))) }) { Text("Source repository ↗") }
            Text("PRISM GALLERY  /  ${BuildConfig.VERSION_NAME}", Modifier.padding(vertical = 18.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (privacy) GlassDialog("Beautifully yours. Locally.", { privacy = false }, { TextButton({ privacy = false }) { Text("Done") } }) { Text("No account, advertising, analytics, cloud backend, or Internet permission. Android share and explicit public export can create external copies. Categories are organization, not encryption. The vault encrypts supported image bytes and metadata; public originals remain until Android confirms deletion. Rooted or compromised devices and external copies are outside this protection. Backups and device transfer of app data are disabled.") }
    if (licenses) GlassDialog("Made with", { licenses = false }, { TextButton({ licenses = false }) { Text("Done") } }) { Text("Manrope · SIL Open Font License 1.1\nAndroidX, Material Icons, Media3, Coil, Kotlin, Coroutines, Backdrop & Shapes · Apache License 2.0\n\nComplete credits, license texts, version references, and design sources are in the repository's docs directory. No Apple fonts are embedded.") }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Switch(checked, onChange) } }
