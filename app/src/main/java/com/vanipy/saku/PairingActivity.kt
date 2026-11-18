package com.vanipy.saku

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vanipy.saku.sync.SyncBridge
import java.util.concurrent.Executors

/**
 * DeltaChat pairing for the sync prototype.
 *
 * The SecureJoin QR IS the fingerprint verification: it carries the inviting
 * device's key fingerprint, and scanning it is the out-of-band check. Nothing
 * is accepted silently - pairing needs this explicit screen on one device and
 * the join action on the other. Synthetic accounts only in this prototype.
 */
class PairingActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var qrImage: ImageView
    private lateinit var qrText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(DemoBanner.make(this))
        status = TextView(this).apply { textSize = 14f; text = "Prototype pairing. arm64 devices only." }
        val showBtn = Button(this).apply { text = "Show my pairing QR" }
        val joinField = EditText(this).apply { hint = "Paste the peer's invite QR text" }
        val joinBtn = Button(this).apply { text = "Join peer's QR" }
        qrImage = ImageView(this).apply { visibility = View.GONE }
        qrText = TextView(this).apply {
            textSize = 10f; visibility = View.GONE; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(showBtn)
        root.addView(joinField)
        root.addView(joinBtn)
        root.addView(qrImage)
        root.addView(qrText)
        setContentView(ScrollView(this).apply { addView(root) })

        showBtn.setOnClickListener {
            setBusy("Starting sync runtime...")
            io.execute {
                try {
                    val bridge = SyncBridge(this@PairingActivity)
                    bridge.start()
                    val qr = bridge.pairQr()
                    val addr = bridge.accountAddr()
                    bridge.stop()
                    runOnUiThread {
                        qrImage.setImageBitmap(qrBitmap(qr))
                        qrImage.visibility = View.VISIBLE
                        qrText.text = qr
                        qrText.visibility = View.VISIBLE
                        setBusy("Invite ready for $addr. Let the peer scan or join this QR.")
                    }
                } catch (u: SyncBridge.RuntimeUnavailable) {
                    runOnUiThread { setBusy("Sync runtime unavailable on this device (${u.message})") }
                } catch (t: Throwable) {
                    runOnUiThread { setBusy("Pairing failed: ${t.message}") }
                }
            }
        }
        joinBtn.setOnClickListener {
            val qr = joinField.text.toString().trim()
            if (qr.isEmpty()) { setBusy("Paste the peer's invite QR text first."); return@setOnClickListener }
            setBusy("Joining...")
            io.execute {
                try {
                    val bridge = SyncBridge(this@PairingActivity)
                    bridge.start()
                    bridge.acceptQr(qr)
                    bridge.stop()
                    runOnUiThread { setBusy("Join started. Keep both devices online until verified.") }
                } catch (u: SyncBridge.RuntimeUnavailable) {
                    runOnUiThread { setBusy("Sync runtime unavailable on this device (${u.message})") }
                } catch (t: Throwable) {
                    runOnUiThread { setBusy("Join failed: ${t.message}") }
                }
            }
        }
    }

    private fun setBusy(msg: String) { status.text = msg }

    private fun qrBitmap(contents: String): Bitmap {
        val size = 768
        val matrix = QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        return bmp
    }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}
