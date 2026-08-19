package com.example.myaac.hybrid.plugins

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Capacitor wrapper around android.speech.tts.TextToSpeech.
 *
 * Ported 1:1 from the behavior in the existing native app's MainActivity.kt
 * (TextToSpeech.OnInitListener, en/iw locale switching, setSpeechRate, QUEUE_FLUSH),
 * see MainActivity.onInit()/speak() in the original app for reference.
 *
 * This is a from-scratch wrapper (not @capacitor-community/text-to-speech) because
 * that community plugin doesn't cleanly expose QUEUE_FLUSH semantics or fine
 * rate/locale control, and TTS is safety-critical enough in this app to own directly.
 */
@CapacitorPlugin(name = "FluentlyTts")
class FluentlyTtsPlugin : Plugin() {

    companion object {
        private const val TAG = "FluentlyTtsPlugin"
    }

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)

    /**
     * Resolves the language code used throughout the rest of the app (matching
     * SettingsRepository's languageCode values, e.g. "en" / "iw") to a java.util.Locale,
     * mirroring MainActivity.onInit()'s `when (languageCode) { "iw" -> Locale("iw", "IL");
     * else -> Locale.US }`.
     */
    @Suppress("DEPRECATION")
    private fun localeFor(languageCode: String): Locale {
        return when (languageCode) {
            "iw", "he" -> Locale("iw", "IL")
            "en" -> Locale.US
            else -> Locale.forLanguageTag(languageCode)
        }
    }

    @PluginMethod
    fun initialize(call: PluginCall) {
        // Tear down any previous instance so repeated calls from the harness are idempotent.
        tts?.shutdown()
        ttsReady.set(false)

        tts = TextToSpeech(context) { status ->
            val available = status == TextToSpeech.SUCCESS
            ttsReady.set(available)
            if (available) {
                attachUtteranceListener()
            } else {
                Log.e(TAG, "TextToSpeech initialization failed, status=$status")
            }
            val ret = JSObject()
            ret.put("available", available)
            call.resolve(ret)
        }
    }

    private fun attachUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // no-op; JS side only needs completion/error signals per the plugin API.
            }

            override fun onDone(utteranceId: String?) {
                val ret = JSObject()
                ret.put("utteranceId", utteranceId ?: "")
                notifyListeners("utteranceComplete", ret)
            }

            @Deprecated("Deprecated in Java")
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                emitError(utteranceId, null, "Unknown TTS error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                emitError(utteranceId, errorCode, "TTS error code $errorCode")
            }
        })
    }

    private fun emitError(utteranceId: String?, errorCode: Int?, message: String) {
        val ret = JSObject()
        ret.put("utteranceId", utteranceId ?: "")
        if (errorCode != null) ret.put("errorCode", errorCode)
        ret.put("message", message)
        notifyListeners("ttsError", ret)
    }

    @PluginMethod
    fun setLanguage(call: PluginCall) {
        val languageCode = call.getString("languageCode")
        if (languageCode == null) {
            call.reject("languageCode is required")
            return
        }
        val engine = tts
        if (engine == null) {
            call.reject("TTS engine not initialized; call initialize() first")
            return
        }
        val result = engine.setLanguage(localeFor(languageCode))
        val success = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!success) {
            Log.e(TAG, "setLanguage($languageCode) unsupported, result=$result")
        }
        val ret = JSObject()
        ret.put("success", success)
        call.resolve(ret)
    }

    @PluginMethod
    fun setRate(call: PluginCall) {
        val rate = call.getFloat("rate", 1.0f) ?: 1.0f
        tts?.setSpeechRate(rate)
        call.resolve()
    }

    @PluginMethod
    fun speak(call: PluginCall) {
        val text = call.getString("text")
        if (text == null) {
            call.reject("text is required")
            return
        }
        val queueMode = call.getString("queueMode", "flush")
        val engine = tts
        if (engine == null) {
            call.reject("TTS engine not initialized; call initialize() first")
            return
        }

        val androidQueueMode = if (queueMode == "add") TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        val result = engine.speak(text, androidQueueMode, params, utteranceId)

        if (result == TextToSpeech.ERROR) {
            call.reject("TextToSpeech.speak() returned ERROR")
            return
        }
        call.resolve()
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        tts?.stop()
        call.resolve()
    }

    @PluginMethod
    fun isSpeaking(call: PluginCall) {
        val ret = JSObject()
        ret.put("speaking", tts?.isSpeaking ?: false)
        call.resolve(ret)
    }

    override fun handleOnDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.handleOnDestroy()
    }
}
