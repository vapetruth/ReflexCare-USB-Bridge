package th.ac.reflexcare.usbbridge

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors

class MainActivity : Activity(), SerialInputOutputManager.Listener {
    private lateinit var usbManager: UsbManager
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var urlBox: EditText
    private var port: UsbSerialPort? = null
    private var io: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val actionUsbPermission = "th.ac.reflexcare.usbbridge.USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == actionUsbPermission) {
                val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                    openDevice(device)
                } else setStatus("ไม่ได้รับอนุญาต USB")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(actionUsbPermission)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(permissionReceiver, filter)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,16,16,16) }
        status = TextView(this).apply { text = "ยังไม่เชื่อมต่อ USB"; textSize = 18f }
        urlBox = EditText(this).apply {
            hint = "URL หน้าเว็บ ReflexCare"
            setSingleLine(true)
            setText("https://torcm.free.nf/reflextab/5.html")
        }
        val load = Button(this).apply { text = "เปิดหน้าเว็บ" ; setOnClickListener {
            val url = urlBox.text.toString().trim()
            if (url.startsWith("https://") || url.startsWith("http://")) web.loadUrl(url)
            else Toast.makeText(this@MainActivity, "กรุณาใส่ URL ของเว็บ", Toast.LENGTH_SHORT).show()
        } }
        val connect = Button(this).apply { text = "ค้นหา / เชื่อมต่อ CH340"; setOnClickListener { findUsb() } }
        val disconnect = Button(this).apply { text = "ตัดการเชื่อมต่อ"; setOnClickListener { closeSerial() } }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(WebBridge(), "AndroidSerial")
        }
        layout.addView(status)
        layout.addView(urlBox)
        layout.addView(load)
        layout.addView(connect)
        layout.addView(disconnect)
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        web.loadData("<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font:20px sans-serif;padding:20px'><h2>ReflexCare USB Bridge</h2><p>ใส่ URL เว็บ ReflexCare แล้วกดเปิดหน้าเว็บ จากนั้นกดค้นหา/เชื่อมต่อ CH340</p></body></html>", "text/html", "UTF-8")
    }

    private fun findUsb() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) { setStatus("ไม่พบ USB Serial — ตรวจสาย Data และ USB Host"); return }
        val driver = drivers.first()
        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(this, 0, Intent(actionUsbPermission), PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
            usbManager.requestPermission(device, pi)
        } else openDevice(device)
    }

    private fun openDevice(device: android.hardware.usb.UsbDevice) {
        try {
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                .firstOrNull { it.device.deviceId == device.deviceId } ?: throw Exception("ไม่พบไดรเวอร์")
            val connection = usbManager.openDevice(device) ?: throw Exception("เปิด USB ไม่สำเร็จ")
            val serial = driver.ports.first()
            serial.open(connection)
            serial.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = serial
            io = SerialInputOutputManager(serial, this).also { it.start() }
            setStatus("เชื่อมต่อแล้ว: ${device.deviceName} @ 115200")
            emitJs("connected", "{\"baudRate\":115200}")
        } catch (e: Exception) { setStatus("เชื่อมต่อไม่สำเร็จ: ${e.message}") }
    }

    private fun closeSerial() {
        try { io?.stop(); io = null; port?.close(); port = null } catch (_: Exception) {}
        setStatus("ตัดการเชื่อมต่อแล้ว")
        emitJs("disconnected", "{}")
    }

    override fun onNewData(data: ByteArray) {
        val value = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
        runOnUiThread { emitJs("data", "{\"base64\":\"$value\"}") }
    }
    override fun onRunError(e: Exception) { runOnUiThread { setStatus("Serial error: ${e.message}") } }

    private fun setStatus(s: String) { runOnUiThread { status.text = s } }
    private fun emitJs(event: String, payload: String) {
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('usbserial-$event',{detail:$payload}));", null)
    }

    inner class WebBridge {
        @JavascriptInterface fun send(text: String) {
            try { port?.write(text.toByteArray(Charsets.UTF_8), 1000) ?: return }
            catch (e: Exception) { setStatus("ส่งข้อมูลผิดพลาด: ${e.message}") }
        }
        @JavascriptInterface fun sendBase64(data: String) {
            try { port?.write(android.util.Base64.decode(data, android.util.Base64.DEFAULT), 1000) }
            catch (e: Exception) { setStatus("ส่งข้อมูลผิดพลาด: ${e.message}") }
        }
        @JavascriptInterface fun isConnected(): Boolean = port != null
    }

    override fun onDestroy() {
        closeSerial()
        try { unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
        executor.shutdownNow()
        web.destroy()
        super.onDestroy()
    }
}
