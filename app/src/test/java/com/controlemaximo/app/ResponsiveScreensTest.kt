package com.controlemaximo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.controlemaximo.app.ui.MainActivity
import com.controlemaximo.app.ui.ReportsActivity
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
@Config(sdk = [28], qualifiers = "w360dp-h800dp-xxhdpi")
class ResponsiveScreensTest {
    private val width = 1080
    private val height = 2400
    private val dir = File("build/responsive").apply { mkdirs() }

    @Test fun mobileScreens() {
        val context = ApplicationProvider.getApplicationContext<ControleMaximoApplication>()
        context.deleteDatabase("controle_maximo.db")
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        idle(main)
        main.select(R.id.navRegister); idle(main); capture(main.window.decorView, "360_register")
        main.select(R.id.navAlerts); idle(main); capture(main.window.decorView, "360_alerts")
        main.select(R.id.navStock); idle(main); capture(main.window.decorView, "360_movements")

        val showcase = Robolectric.buildActivity(ShowcaseActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(showcase.window.decorView, "360_showcase")

        val reports = Robolectric.buildActivity(ReportsActivity::class.java).setup().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        capture(reports.window.decorView, "360_reports")
    }

    private fun idle(activity: MainActivity) {
        activity.supportFragmentManager.executePendingTransactions()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun capture(view: View, name: String) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
