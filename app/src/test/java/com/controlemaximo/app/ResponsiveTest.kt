package com.controlemaximo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.controlemaximo.app.ui.MainActivity
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
class ResponsiveTest {
    private fun capture(tag: String, width: Int, height: Int) {
        val context = ApplicationProvider.getApplicationContext<ControleMaximoApplication>()
        context.deleteDatabase("controle_maximo.db")
        val main = Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
        main.supportFragmentManager.executePendingTransactions()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val view = main.window.decorView
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/responsive").apply { mkdirs() }
        File(dir, "$tag.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        main.finish()
    }

    @Test @Config(sdk = [28], qualifiers = "w360dp-h800dp-xxhdpi")
    fun width360() = capture("360", 1080, 2400)

    @Test @Config(sdk = [28], qualifiers = "w390dp-h844dp-xxhdpi")
    fun width390() = capture("390", 1170, 2532)

    @Test @Config(sdk = [28], qualifiers = "w768dp-h1024dp-xhdpi")
    fun width768() = capture("768", 1536, 2048)

    @Test @Config(sdk = [28], qualifiers = "w1440dp-h900dp-hdpi")
    fun width1440() = capture("1440", 1440, 900)
}
