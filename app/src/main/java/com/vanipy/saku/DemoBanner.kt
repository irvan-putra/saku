package com.vanipy.saku

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView

/** Persistent warning shown on every screen of this demo build. It is not dismissible. */
object DemoBanner {
    const val TEXT = "DEMO - NOT FOR REAL PASSWORDS. Fake sample entries only."
    const val TAG = "saku-demo-banner"

    fun make(context: Context): TextView = TextView(context).apply {
        text = TEXT
        tag = TAG
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setBackgroundColor(0xFFB00020.toInt())
        setPadding(24, 20, 24, 20)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
