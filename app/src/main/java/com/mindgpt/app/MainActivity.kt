package com.mindgpt.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var cameraUri: Uri? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var keepListening = false
    private var speechLanguage = "fa-IR"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updateDownloadId: Long = -1L
    private var updateProgressRunning = false
        private var awaitingUpdateInstallPermission = false
private val updateApkFile: File
        get() = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MindGPT-update.apk")

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != updateDownloadId) return
            updateProgressRunning = false
            mainHandler.postDelayed({
                if (updateApkFile.exists() && updateApkFile.length() > 0L) {
                    js("window.__updateProgress && window.__updateProgress(100)")
                    js("window.__updateStatus && window.__updateStatus('readyToInstall')")
                    installDownloadedUpdate()
                } else {
                    js("window.__updateStatus && window.__updateStatus('failed')")
                    js("window.__mindgptToast && window.__mindgptToast('فایل بروزرسانی پیدا نشد')")
                }
            }, 350L)
        }
    }

    private fun installDownloadedUpdate() {
        val apk = updateApkFile
        if (!apk.exists() || apk.length() <= 0L) {
            js("window.__updateStatus && window.__updateStatus('downloading')")
            js("window.__mindgptToast && window.__mindgptToast('فایل بروزرسانی هنوز آماده نصب نیست')")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            js("window.__updateStatus && window.__updateStatus('permission')")
                        awaitingUpdateInstallPermission = true
runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }.onFailure {
                js("window.__mindgptToast && window.__mindgptToast('مجوز نصب بروزرسانی باز نشد')")
            }
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            startActivity(installIntent)
            js("window.__updateStatus && window.__updateStatus('installing')")
        }.onFailure {
            runCatching {
                startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                })
                js("window.__updateStatus && window.__updateStatus('installing')")
            }.onFailure {
                js("window.__updateStatus && window.__updateStatus('readyToInstall')")
                js("window.__mindgptToast && window.__mindgptToast('باز کردن نصب‌کننده ممکن نشد')")
            }
        }
    }

    private fun startUpdateProgress() {
        if (updateProgressRunning || updateDownloadId < 0) return
        updateProgressRunning = true
        Thread {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (updateProgressRunning && updateDownloadId >= 0) {
                try {
                    dm.query(DownloadManager.Query().setFilterById(updateDownloadId)).use { c ->
                        if (c != null && c.moveToFirst()) {
                            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                updateProgressRunning = false
                                js("window.__updateProgress && window.__updateProgress(100)")
                                js("window.__updateStatus && window.__updateStatus('readyToInstall')")
                                mainHandler.post { installDownloadedUpdate() }
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                updateProgressRunning = false
                                js("window.__updateStatus && window.__updateStatus('failed')")
                                js("window.__mindgptToast && window.__mindgptToast('دانلود بروزرسانی ناموفق بود')")
                            } else if (total > 0) {
                                // Keep 100% reserved for DownloadManager.STATUS_SUCCESSFUL.
                                val pct = ((done * 99L) / total).coerceIn(0, 99)
                                js("window.__updateProgress && window.__updateProgress($pct)")
                            }
                        }
                    }
                } catch (_: Throwable) { }
                if (updateProgressRunning) Thread.sleep(450)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        // Only retry after returning from Android's unknown-app install permission.
        // Never reopen a stale downloaded APK just because the app resumed.
        if (awaitingUpdateInstallPermission) {
            awaitingUpdateInstallPermission = false
            if (updateApkFile.exists() && updateApkFile.length() > 0L &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls())) {
                installDownloadedUpdate()
            }
        }
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris = when {
            result.resultCode != Activity.RESULT_OK -> emptyArray()
            data?.clipData != null -> Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
            data?.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = cameraUri
        if (result.resultCode == Activity.RESULT_OK && uri != null) fileCallback?.onReceiveValue(arrayOf(uri))
        else fileCallback?.onReceiveValue(emptyArray())
        fileCallback = null
        cameraUri = null
    }

    private val speechPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginSpeech() else js("window.__speechError && window.__speechError('permission')")
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val req = pendingWebPermission
        pendingWebPermission = null
        if (req != null) {
            val allowed = mutableListOf<String>()
            if (grants[Manifest.permission.RECORD_AUDIO] == true) allowed += PermissionRequest.RESOURCE_AUDIO_CAPTURE
            if (grants[Manifest.permission.CAMERA] == true) allowed += PermissionRequest.RESOURCE_VIDEO_CAPTURE
            if (allowed.isNotEmpty()) req.grant(allowed.toTypedArray()) else req.deny()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(updateReceiver, filter)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        webView = WebView(this)
        setContentView(webView)
        tts = TextToSpeech(this, this)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "$userAgentString MindGPT/5.3"
        }
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.addJavascriptInterface(NativeBridge(), "AndroidApi")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val wantsImage = fileChooserParams?.acceptTypes?.any { it.startsWith("image/") || it == "image/*" } == true
                if (fileChooserParams?.isCaptureEnabled == true && wantsImage) {
                    val dir = File(cacheDir, "camera").apply { mkdirs() }
                    val photo = File.createTempFile("mindgpt_", ".jpg", dir)
                    cameraUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", photo)
                    val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    return runCatching { cameraLauncher.launch(camera); true }.getOrElse {
                        fileCallback = null
                        cameraUri = null
                        false
                    }
                }
                val intent = runCatching { fileChooserParams?.createIntent() }.getOrNull()
                    ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                return runCatching { filePicker.launch(intent); true }.getOrElse {
                    fileCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needed = mutableListOf<String>()
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    ) needed += Manifest.permission.RECORD_AUDIO
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ) needed += Manifest.permission.CAMERA
                    if (needed.isEmpty()) request.grant(request.resources) else {
                        pendingWebPermission = request
                        permissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.__handleBack ? window.__handleBack() : false") { value ->
                    if (value != "true") finish()
                }
            }
        })
        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = js("window.__ttsState && window.__ttsState('playing')")
                override fun onDone(utteranceId: String?) = js("window.__ttsState && window.__ttsState('done')")
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = js("window.__ttsState && window.__ttsState('error')")
            })
        }
    }

    private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
    }

    private fun beginSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("window.__speechError && window.__speechError('unavailable')")
            return
        }
        keepListening = true
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = js("window.__speechState && window.__speechState('listening')")
                    override fun onBeginningOfSpeech() = js("window.__speechState && window.__speechState('speaking')")
                    override fun onRmsChanged(rmsdB: Float) {
                        val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        js("window.__speechLevel && window.__speechLevel($level)")
                    }
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = js("window.__speechState && window.__speechState('processing')")
                    override fun onError(error: Int) {
                        if (!keepListening) return
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            keepListening = false
                            js("window.__speechError && window.__speechError('permission')")
                            return
                        }
                        if (error == SpeechRecognizer.ERROR_CLIENT) return
                        mainHandler.postDelayed({ if (keepListening) restartSpeech() }, 350)
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) js("window.__speechResult && window.__speechResult(${JSONObject.quote(text)})")
                        if (keepListening) mainHandler.postDelayed({ restartSpeech() }, 300)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) js("window.__speechPartial && window.__speechPartial(${JSONObject.quote(text)})")
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        restartSpeech()
    }

    private fun restartSpeech() {
        if (!keepListening) return
        runCatching { recognizer?.cancel(); recognizer?.startListening(speechIntent()) }
            .onFailure { js("window.__speechError && window.__speechError('start')") }
    }

    private fun stopSpeechInternal() {
        keepListening = false
        runCatching { recognizer?.stopListening() }
        mainHandler.postDelayed({ runCatching { recognizer?.cancel() } }, 150)
        js("window.__speechState && window.__speechState('stopped')")
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(updateReceiver) }
        stopSpeechInternal()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    private fun js(code: String) = runOnUiThread { if (::webView.isInitialized) webView.evaluateJavascript(code, null) }

    private object Secrets {
        private const val ALIAS = "mindgpt.local.aes"
        private const val PREF = "mindgpt.secrets"
        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return generator.generateKey()
        }
        fun put(context: Context, name: String, value: String) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        }
        fun get(context: Context, name: String): String {
            val encoded = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(name, null) ?: return ""
            return runCatching {
                val packed = Base64.decode(encoded, Base64.NO_WRAP)
                val iv = packed.copyOfRange(0, 12)
                val data = packed.copyOfRange(12, packed.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(data), Charsets.UTF_8)
            }.getOrDefault("")
        }
        fun clear(context: Context, name: String) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(name).apply()
    }

    inner class NativeBridge {
        private val client = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        @JavascriptInterface
        fun request(id: String, method: String, url: String, headersJson: String, body: String) {
            Thread {
                try {
                    val builder = Request.Builder().url(url)
                    val headers = JSONObject(headersJson)
                    headers.keys().forEach { key -> builder.header(key, headers.optString(key)) }
                    val reqBody = if (body.isNotEmpty()) body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()) else null
                    when (method.uppercase()) {
                        "GET" -> builder.get()
                        "POST" -> builder.post(reqBody ?: "".toRequestBody())
                        "PUT" -> builder.put(reqBody ?: "".toRequestBody())
                        "DELETE" -> if (reqBody != null) builder.delete(reqBody) else builder.delete()
                        else -> builder.method(method.uppercase(), reqBody)
                    }
                    client.newCall(builder.build()).execute().use { r ->
                        js("window.__nativeResponse && window.__nativeResponse(${JSONObject.quote(id)},${r.code},${JSONObject.quote(r.body?.string().orEmpty())})")
                    }
                } catch (t: Throwable) {
                    js("window.__nativeResponse && window.__nativeResponse(${JSONObject.quote(id)},0,${JSONObject.quote(t.message ?: "Network error")})")
                }
            }.start()
        }

        @JavascriptInterface fun startSpeech(language: String) = runOnUiThread {
            speechLanguage = language
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginSpeech()
            else speechPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        @JavascriptInterface fun stopSpeech() = runOnUiThread { stopSpeechInternal() }

        @JavascriptInterface fun speak(text: String, language: String) = runOnUiThread {
            val engine = tts ?: return@runOnUiThread
            val wanted = if (language.startsWith("fa")) Locale.forLanguageTag("fa-IR") else Locale.ENGLISH
            var availability = engine.isLanguageAvailable(wanted)
            if (language.startsWith("fa") && availability < TextToSpeech.LANG_AVAILABLE) {
                val fallback = Locale("fa")
                val fallbackAvailability = engine.isLanguageAvailable(fallback)
                if (fallbackAvailability >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = fallback
                    availability = fallbackAvailability
                }
            } else engine.language = wanted
            if (availability < TextToSpeech.LANG_AVAILABLE) {
                js("window.__mindgptToast && window.__mindgptToast('صدای فارسی در موتور TTS گوشی نصب نیست')")
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                return@runOnUiThread
            }
            engine.setSpeechRate(0.98f)
            engine.setPitch(1.0f)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mindgpt-${System.currentTimeMillis()}")
        }
        @JavascriptInterface fun stopSpeak() = runOnUiThread { tts?.stop(); js("window.__ttsState && window.__ttsState('stopped')") }

        @JavascriptInterface fun share(text: String) = runOnUiThread {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share"))
        }
        @JavascriptInterface fun copy(text: String) = runOnUiThread {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("MindGPT", text))
        }
        @JavascriptInterface fun openAppSettings() = runOnUiThread {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        @JavascriptInterface fun getBundledApiKey(): String = BuildConfig.CODECRAFT_API_KEY

        @JavascriptInterface fun checkForUpdate(force: Boolean = false) {
            Thread {
                try {
                    val req = Request.Builder().url("https://raw.githubusercontent.com/efotball2024hdvhb-oss/gpt/main/update.json").header("Cache-Control", "no-cache").build()
                    client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@use
                        val obj = JSONObject(r.body?.string().orEmpty())
                        val remote = obj.optInt("versionCode", 0)
                        if (remote > BuildConfig.VERSION_CODE) {
                            js("window.__updateAvailable && window.__updateAvailable(${JSONObject.quote(obj.toString())})")
                        } else if (force) {
                            js("window.__mindgptToast && window.__mindgptToast('آخرین نسخه نصب است')")
                        }
                    }
                } catch (_: Throwable) { }
            }.start()
        }

        @JavascriptInterface fun installDownloadedUpdateNow() = runOnUiThread { installDownloadedUpdate() }

        @JavascriptInterface fun downloadAndInstall(url: String) = runOnUiThread {
            if (url.isBlank()) return@runOnUiThread
            runCatching { if (updateApkFile.exists()) updateApkFile.delete() }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("MindGPT update")
                .setDescription("Downloading the latest MindGPT APK")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "MindGPT-update.apk")
            updateDownloadId = dm.enqueue(request)
            js("window.__updateProgress && window.__updateProgress(0)")
            js("window.__updateStatus && window.__updateStatus('downloading')")
            startUpdateProgress()
        }

        @JavascriptInterface fun setSecret(name: String, value: String) { if (name == "apiKey") Secrets.put(this@MainActivity, name, value) }
        @JavascriptInterface fun getSecret(name: String): String = if (name == "apiKey") Secrets.get(this@MainActivity, name) else ""
        @JavascriptInterface fun clearSecret(name: String) { if (name == "apiKey") Secrets.clear(this@MainActivity, name) }
    }
}
