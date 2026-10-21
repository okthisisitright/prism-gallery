package com.prism.gallery

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real activity/navigation/Room/DataStore path; saves public-only UI evidence. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @Test fun createCategoryApplyThemeAndProtectVaultWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PrismApplication
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue("Main activity did not render", device.wait(Until.hasObject(By.desc("Albums")), 30_000))
            device.findObject(By.desc("Albums")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("New category")), 15_000))
            device.findObject(By.desc("New category")).click()
            val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 15_000)
            assertNotNull(input); input.text = "Prism UI Test"
            device.findObject(By.text("Save")).click()
            assertTrue("Created category was not displayed", device.wait(Until.hasObject(By.textContains("Prism UI Test")), 15_000))
            screenshot("albums-light")
            runBlocking { app.settings.string("theme", "Dark") }
            device.waitForIdle(); screenshot("albums-dark")
            // Scroll the real Albums view to its secured destination.
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
            val vault = device.wait(Until.findObject(By.text("Private Vault")), 15_000)
            assertNotNull("Vault entry missing", vault); vault.click()
            assertTrue(device.wait(Until.hasObject(By.text("A private place.")), 15_000))
            scenario.onActivity { activity -> assertTrue("Vault window allows screenshots", activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            // Process/lifecycle safety must leave secrets locked when activity stops.
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertFalse(app.vault.unlocked.value)
        }
        runBlocking {
            app.settings.string("theme", "System")
            app.database.dao().categories().first().filter { it.name == "Prism UI Test" }.forEach { app.database.dao().deleteCategory(it.id) }
        }
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val folder = File(instrumentation.targetContext.filesDir, "screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
