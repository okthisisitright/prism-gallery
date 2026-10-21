package com.prism.gallery.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.prism.gallery.R
import com.prism.gallery.feature.settings.Preferences

val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalPrismPreferences = staticCompositionLocalOf { Preferences() }
private val Manrope = FontFamily(Font(R.font.manrope))
val SoftShape = RoundedCornerShape(28.dp)
val PrismSpring = spring<Float>(dampingRatio = .88f, stiffness = 460f)
@Composable fun PrismTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val dark = when (preferences.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(primary = Color(0xFFD2E4CC), onPrimary = Color(0xFF233126), background = Color(0xFF111511), surface = Color(0xFF1B211C), surfaceVariant = Color(0xFF2C342D), onSurface = Color(0xFFEAEDE4), onBackground = Color(0xFFEAEDE4), outline = Color(0xFF7D887B))
    else lightColorScheme(primary = Color(0xFF354C3D), onPrimary = Color(0xFFF7FAF3), background = Color(0xFFF5F4F0), surface = Color(0xFFECEEE6), surfaceVariant = Color(0xFFE1E6DC), onSurface = Color(0xFF202820), onBackground = Color(0xFF202820), outline = Color(0xFF667362))
    val typography = Typography(
        displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-1.6).sp),
        headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
        headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-.6).sp),
        titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    )
    CompositionLocalProvider(LocalPrismPreferences provides preferences) { MaterialTheme(colorScheme = colors, typography = typography, content = content) }
}
@Composable fun PrismGlassSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalGlassBackdrop.current
    val mode = LocalPrismPreferences.current.glass
    val context = LocalContext.current
    val lowRam = remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice }
    val quality = if (mode == "Auto") { if (lowRam || Build.VERSION.SDK_INT < 33) "Reduced" else "Balanced" } else mode
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val tint = if (dark) Color(0xFF18211B) else Color(0xFFF9FBF5)
    val glass = if (backdrop != null && Build.VERSION.SDK_INT >= 33 && quality != "Reduced") Modifier.drawBackdrop(backdrop = backdrop, shape = { SoftShape }, effects = {
        blur(if (quality == "High") 20.dp.toPx() else 10.dp.toPx())
        if (quality == "High") { vibrancy(); lens(12.dp.toPx(), 22.dp.toPx()) }
    }, onDrawSurface = { drawRect(tint.copy(alpha = if (dark) .68f else .62f)) }) else Modifier.background(tint.copy(alpha = .96f), SoftShape)
    Box(modifier.shadow(18.dp, SoftShape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f)).then(glass).border(.8.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .3f else .95f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .4f))), SoftShape).clip(SoftShape)) { content() }
}
@Composable fun GlassButton(label: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)) }; Text(label) }
}
@Composable fun GlassToolbar(title: String, subtitle: String? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineLarge); if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action()
    }
}
@Composable fun GlassSearch(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onValueChange, modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp), placeholder = { Text("Filename, date, folder, category…") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = .6f), unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .2f)))
}
@Composable fun GlassSegmentedControl(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> FilterChip(selected == option, { onSelect(option) }, { Text(option) }, shape = CircleShape) } }
}
@Composable fun GlassDialog(title: String, onDismiss: () -> Unit, confirm: @Composable () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = SoftShape, title = { Text(title) }, text = content, confirmButton = confirm, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) { ModalBottomSheet(onDismiss, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), content = content) }
@Composable fun GlassSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, steps: Int = 0) { Slider(value, onChange, valueRange = range, steps = steps) }
@Composable fun GlassSelectionBar(count: Int, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    PrismGlassSurface(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("$count selected", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium); TextButton(onClose) { Text("Done") } }; Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly, content = actions) } }
}
@Composable fun GlassTabBar(tabs: List<Triple<String, String, ImageVector>>, current: String, onSelect: (String) -> Unit) {
    PrismGlassSurface(Modifier.fillMaxWidth()) { Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        tabs.forEach { (route, label, icon) ->
            val selected = current == route
            val alpha by animateFloatAsState(if (selected) 1f else .65f, if (LocalPrismPreferences.current.motion) PrismSpring else snap(), label = "tab")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { onSelect(route) }.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                Icon(icon, label, Modifier.size(22.dp), tint = color)
                Spacer(Modifier.height(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    } }
}
@Composable fun SectionLabel(text: String, trailing: String? = null) { Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable fun EmptyState(icon: ImageVector, title: String, message: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.padding(top = 25.dp).size(45.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
