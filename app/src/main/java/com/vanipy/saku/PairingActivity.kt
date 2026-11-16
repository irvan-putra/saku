package com.vanipy.saku

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PairingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            setPadding(48, 48, 48, 48); textSize = 16f
            text = "Not paired.\n\nPairing is disabled in this mock build. " +
                "When enabled it will require owner verification of the full " +
                "fingerprint out-of-band (WhatsApp) and an explicit approval before " +
                "any sync is accepted. Nothing is accepted silently."
        })
    }
}
