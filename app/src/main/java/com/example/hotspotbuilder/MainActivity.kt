package com.example.hotspotbuilder

import android.Manifest
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.net.Uri
import android.util.Base64
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnInsertImage: Button
    private lateinit var btnInsertIcon: Button
    private lateinit var btnInsertTable: Button
    private lateinit var btnPreview: Button
    private lateinit var btnSaveHtml: Button
    private lateinit var tvStatus: TextView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { u ->
            contentResolver.openInputStream(u)?.use { input ->
                val bytes = input.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                val js = "javascript:(function(){ var img = document.createElement('img'); img.src='data:image/*;base64,$base64'; img.style.maxWidth='100%'; img.style.height='auto'; document.getElementById('editor').appendChild(img); })()"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 123)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.editorWebView)
        btnInsertImage = findViewById(R.id.btnInsertImage)
        btnInsertIcon = findViewById(R.id.btnInsertIcon)
        btnInsertTable = findViewById(R.id.btnInsertTable)
        btnPreview = findViewById(R.id.btnPreview)
        btnSaveHtml = findViewById(R.id.btnSaveHtml)
        tvStatus = findViewById(R.id.tvStatus)

        setupWebView()

        btnInsertImage.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnInsertIcon.setOnClickListener {
            val script = "javascript:(function(){ var i = document.createElement('i'); i.className='fa fa-wifi'; i.style.fontSize='32px'; i.style.margin='8px'; document.getElementById('editor').appendChild(i); })()"
            webView.evaluateJavascript(script, null)
        }

        btnInsertTable.setOnClickListener {
            val script = "javascript:(function(){ var tbl = document.createElement('table'); tbl.border=1; tbl.style.width='100%'; var tr = document.createElement('tr'); var td1 = document.createElement('td'); td1.innerText='خلية 1'; var td2 = document.createElement('td'); td2.innerText='خلية 2'; tr.appendChild(td1); tr.appendChild(td2); tbl.appendChild(tr); document.getElementById('editor').appendChild(tbl); })()"
            webView.evaluateJavascript(script, null)
        }

        btnPreview.setOnClickListener {
            getEditorHtml { html ->
                webView.loadDataWithBaseURL(null, wrapHtml(html), "text/html", "utf-8", null)
            }
        }

        btnSaveHtml.setOnClickListener {
            getEditorHtml { html ->
                saveHtmlAndZip(html)
            }
        }
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        val initial = baseEditorHtml()
        webView.loadDataWithBaseURL(null, initial, "text/html", "utf-8", null)
    }

    private fun baseEditorHtml(): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta name='viewport' content='width=device-width, initial-scale=1'/>
              <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
              <style>
                body { font-family: Arial, Helvetica, sans-serif; padding:10px; background:#f4f4f4; direction: rtl; }
                #editor { min-height:400px; background: white; padding: 12px; border-radius:8px; box-shadow:0 0 4px rgba(0,0,0,0.1); }
                table { border-collapse: collapse; }
                td, th { padding:8px; border:1px solid #ddd; }
              </style>
            </head>
            <body>
              <h3>محرر صفحة الهوتسبوت</h3>
              <div id="editor" contenteditable="true">
                <h2 style="text-align:center">أدخل عنوان هنا</h2>
                <p style="text-align:center">وصف أو تعليمات تسجيل الدخول</p>
              </div>
              <script>
                function getContent(){ return document.getElementById('editor').innerHTML; }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun wrapHtml(bodyInner: String): String {
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name='viewport' content='width=device-width, initial-scale=1'/>
              <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
              <style>
                body { margin:0; padding:0; font-family: Arial, Helvetica, sans-serif; background: #f0f0f0; direction: rtl; }
                .container { max-width: 720px; margin:40px auto; background: white; padding:20px; border-radius:8px; box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
                button { padding:10px 16px; border-radius:6px; border:1px solid #ccc; }
                input, select { padding:10px; border-radius:6px; border:1px solid #ccc; width:100%; }
              </style>
            </head>
            <body>
              <div class="container">
                ${'$'}bodyInner
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getEditorHtml(callback: (String)->Unit) {
        webView.evaluateJavascript("getContent();") { value ->
            val unq = if (value != null && value.length>=2 && value.startsWith("\"") && value.endsWith("\"")) {
                value.substring(1, value.length-1).replace("\\u003C","<").replace("\\n","").replace("\\\"","\"")
            } else value ?: ""
            callback(unq)
        }
    }

    private fun saveHtmlAndZip(htmlInner: String) {
        try {
            val html = wrapHtml(htmlInner)
            val baseFolder = File(getExternalFilesDir(null), "hotspot_export")
            if (!baseFolder.exists()) baseFolder.mkdirs()

            val pageFile = File(baseFolder, "page.html")
            pageFile.writeText(html, Charsets.UTF_8)

            val zipFile = File(getExternalFilesDir(null), "hotspot_page.zip")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                addFileToZip(zos, pageFile, "page.html")
            }

            runOnUiThread { tvStatus.text = "تم التصدير: " + zipFile.absolutePath }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { tvStatus.text = "خطأ أثناء التصدير: " + e.message }
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }
}
