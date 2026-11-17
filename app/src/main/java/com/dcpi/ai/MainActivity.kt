package com.dcpi.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var speech: SpeechRecognizer? = null
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "DCPI")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 77)
        }
    }

    inner class Bridge {
        @JavascriptInterface fun speak(text: String, voice: String) {
            runOnUiThread {
                tts?.language = Locale("es", "ES")
                val candidates = tts?.voices?.filter { it.locale.language == "es" }.orEmpty()
                val preferred = candidates.firstOrNull {
                    if (voice == "female") it.name.contains("female", true) || it.name.contains("woman", true)
                    else it.name.contains("male", true) || it.name.contains("man", true)
                }
                if (preferred != null) tts?.voice = preferred
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dcpi_reply")
            }
        }

        @JavascriptInterface fun listen() { runOnUiThread { startListening() } }

        @JavascriptInterface fun askGemini(prompt: String, key: String) {
            io.execute {
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + key
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 20000
                        readTimeout = 30000
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                    }
                    val body = JSONObject().put("contents",
                        org.json.JSONArray().put(JSONObject().put("parts",
                            org.json.JSONArray().put(JSONObject().put("text", prompt)))))
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                    val response = stream.bufferedReader().use { it.readText() }
                    if (conn.responseCode !in 200..299) throw IllegalStateException("Gemini HTTP " + conn.responseCode)
                    val answer = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    runOnUiThread { web.evaluateJavascript("window.onAIReply(${JSONObject.quote(answer)})", null) }
                    conn.disconnect()
                } catch (e: Exception) {
                    val msg = "No pude conectar con Gemini: " + (e.message ?: "error")
                    runOnUiThread { web.evaluateJavascript("window.onAIReply(${JSONObject.quote(msg)})", null) }
                }
            }
        }

        @JavascriptInterface fun search(query: String) {
            runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode(query)))) }
        }

        @JavascriptInterface fun videos(query: String) {
            runOnUiThread { startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(query)))) }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 77)
            return
        }
        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                web.evaluateJavascript("window.onSpeechResult(${JSONObject.quote(text)})", null)
            }
            override fun onError(error: Int) { web.evaluateJavascript("window.onSpeechResult('')", null) }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-EC")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con DCPI AI")
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("es", "ES")
    }

    override fun onDestroy() {
        speech?.destroy()
        tts?.shutdown()
        io.shutdownNow()
        web.destroy()
        super.onDestroy()
    }
}