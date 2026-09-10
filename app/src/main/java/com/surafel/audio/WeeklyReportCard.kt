package com.surafel.audio

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout

class WeeklyReportCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    init {
        isClickable = true
        isFocusable = true
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                performClick()
            }
            true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        context.startActivity(Intent(context, WeeklyReportActivity::class.java))
        return true
    }
}
