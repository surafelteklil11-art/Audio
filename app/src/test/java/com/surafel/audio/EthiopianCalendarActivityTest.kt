package com.surafel.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.app.AlertDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 35], qualifiers = "w360dp-h800dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class EthiopianCalendarActivityTest {
    private val newYear = EthiopianCalendar.epochDay(EthiopianDate(2019, 1, 1)) * EthiopianCalendar.DAY_MILLIS - 3 * 3600_000L

    @Test fun opensPagumenAndNavigatesThirteenMonths() {
        Robolectric.buildActivity(EthiopianCalendarActivity::class.java).use { controller ->
            controller.get().nowMillis = { newYear - 1000 }
            val activity = controller.setup().visible().get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            layout(root)
            assertEquals(5, days(root).count())
            root.findViewWithTag<View>("next-month").performClick()
            assertEquals(EthiopianDate(2019, 1, 1), activity.selectedDate)
            assertEquals(30, days(root).count())
            root.findViewWithTag<View>("previous-month").performClick()
            assertEquals(5, days(root).count())
            root.findViewWithTag<View>("calendar-today").performClick()
            assertEquals(EthiopianDate(2018, 13, 5), activity.selectedDate)
        }
    }
    @Test fun midnightUpdatesOpenPageAndResumeCatchesUp() {
        var now = newYear - 1000
        Robolectric.buildActivity(EthiopianCalendarActivity::class.java).use { controller ->
            controller.get().nowMillis = { now }
            val activity = controller.setup().visible().get()
            now = newYear
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
            assertEquals(EthiopianDate(2019, 1, 1), activity.selectedDate)
            controller.pause().stop()
            now += EthiopianCalendar.DAY_MILLIS
            controller.start().resume().visible()
            assertEquals(EthiopianDate(2019, 1, 2), activity.selectedDate)
        }
    }
    @Test fun midnightKeepsBrowsedMonthAndNotesPersist() {
        var now = newYear - 1000
        Robolectric.buildActivity(EthiopianCalendarActivity::class.java).use { controller ->
            controller.get().nowMillis = { now }
            val activity = controller.setup().visible().get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.findViewWithTag<View>("previous-month").performClick()
            val selected = activity.selectedDate
            root.findViewWithTag<View>("calendar-note").performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val input = descendants(dialog.findViewById(android.R.id.content)).filterIsInstance<EditText>().single()
            input.setText("የሙዚቃ ልምምድ")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            now = newYear
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
            assertEquals(selected, activity.selectedDate)
            assertTrue(root.findViewWithTag<TextView>("calendar-note").text.contains("የሙዚቃ ልምምድ"))
            controller.recreate()
            assertEquals(selected, controller.get().selectedDate)
        }
    }
    @Test @Config(sdk = [33, 35]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun exportCalendarScreenshots() {
        for ((name, date) in listOf("pagumen" to EthiopianDate(2018, 13, 4), "meskerem" to EthiopianDate(2019, 1, 1))) {
            Robolectric.buildActivity(EthiopianCalendarActivity::class.java).use { controller ->
                controller.get().nowMillis = { EthiopianCalendar.epochDay(date) * EthiopianCalendar.DAY_MILLIS }
                val root = controller.setup().visible().get().findViewById<ViewGroup>(android.R.id.content)
                layout(root)
                val bitmap = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val file = File("build/calendar-previews/$name-api-${android.os.Build.VERSION.SDK_INT}.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
    @Test @Config(sdk = [33, 35])
    fun dateConversionAgreesWithAndroidIcu() {
        val calendar = android.icu.util.Calendar.getInstance(android.icu.util.TimeZone.getTimeZone("Africa/Addis_Ababa"), android.icu.util.ULocale("am_ET@calendar=ethiopic"))
        for (year in listOf(1900, 1999, 2015, 2018, 2019, 2092, 2100)) for (month in 1..13) {
            val date = EthiopianDate(year, month, EthiopianCalendar.daysInMonth(year, month))
            calendar.timeInMillis = EthiopianCalendar.epochDay(date) * EthiopianCalendar.DAY_MILLIS
            assertEquals(date.year, calendar.get(android.icu.util.Calendar.YEAR))
            assertEquals(date.month - 1, calendar.get(android.icu.util.Calendar.MONTH))
            assertEquals(date.day, calendar.get(android.icu.util.Calendar.DAY_OF_MONTH))
        }
    }
    private fun days(view: View) = descendants(view).filter { it.tag?.toString()?.startsWith("calendar-day-") == true }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun layout(root: View) {
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 720, 1600); shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
