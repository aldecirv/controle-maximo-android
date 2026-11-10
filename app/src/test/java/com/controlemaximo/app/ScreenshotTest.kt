package com.controlemaximo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.controlemaximo.app.ui.MainActivity
import com.controlemaximo.app.ui.ReportsActivity
import com.controlemaximo.app.ui.SecurityActivity
import com.controlemaximo.app.ui.SettingsActivity
import com.controlemaximo.app.ui.ShowcaseActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val dir = File("build/screenshots").apply { mkdirs() }

    @Test fun captureScreens() {
        val context = ApplicationProvider.getApplicationContext<ControleMaximoApplication>()
        context.deleteDatabase("controle_maximo.db")
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        idle(main)
        capture(main.window.decorView, "60_dashboard")

        main.select(R.id.navRegister)
        idle(main)
        capture(main.window.decorView, "61_register")

        main.select(R.id.navAlerts)
        idle(main)
        capture(main.window.decorView, "62_alerts")

        main.select(R.id.navStock)
        idle(main)
        capture(main.window.decorView, "63_movements")

        val showcase = Robolectric.buildActivity(ShowcaseActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(showcase.window.decorView, "64_showcase")

        val reports = Robolectric.buildActivity(ReportsActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(reports.window.decorView, "65_reports")

        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(settings.window.decorView, "66_settings")

        val security = Robolectric.buildActivity(SecurityActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(security.window.decorView, "67_security")
    }

    private fun idle(activity: MainActivity) {
        activity.supportFragmentManager.executePendingTransactions()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun capture(view: View, name: String) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2280, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2280)
        val bitmap = Bitmap.createBitmap(1080, 2280, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
