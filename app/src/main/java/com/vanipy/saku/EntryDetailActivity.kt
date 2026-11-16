package com.vanipy.saku

import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

class EntryDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val i = intent
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val hidden = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
        val pw = TextView(this).apply { text = "password: $hidden (tap to reveal)"; textSize = 16f }
        pw.setOnClickListener {
            pw.text = if (pw.text.contains(hidden)) "password: ${i.getStringExtra("password")}"
                      else "password: $hidden (tap to reveal)"
        }
        val sync = i.getLongExtra("lastSync", -1L)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48)
        }
        fun line(s: String) = TextView(this).apply { text = s; textSize = 16f; setPadding(0, 12, 0, 12) }
        layout.addView(line("site: ${i.getStringExtra("site")}"))
        layout.addView(line("username: ${i.getStringExtra("username")}"))
        layout.addView(pw)
        layout.addView(line("origin: ${i.getStringExtra("origin")}  version: ${i.getIntExtra("version", 0)}"))
        layout.addView(line("last change: ${df.format(Date(i.getLongExtra("lastChange", 0L)))}"))
        layout.addView(line("last sync: " + (if (sync > 0) df.format(Date(sync)) else "never")))
        setContentView(layout)
    }
}
