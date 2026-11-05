package com.example.saferecorder

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class VoiceListener(
    private val context: Context,
    private val startWord: String,
    private val stopWord: String,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener {

    private var model: Model? = null
    private var speech: SpeechService? = null
    private var lastFire = 0L

    fun begin() {
        StorageService.unpack(context, "model-en-us", "model",
            { m ->
                model = m
                val grammar = "[\"$startWord\", \"$stopWord\", \"[unk]\"]"
                val rec = Recognizer(m, 16000f, grammar)
                speech = SpeechService(rec, 16000f).also { it.startListening(this) }
            },
            { e -> onError("Model load failed: ${e.message}") })
    }

    fun end() {
        speech?.stop(); speech?.shutdown(); speech = null
        model?.close(); model = null
    }

    private fun handle(json: String, key: String) {
        val text = JSONObject(json).optString(key).trim().lowercase()
        val now = System.currentTimeMillis()
        if (now - lastFire < 2500) return
        when {
            text.contains(startWord) -> { lastFire = now; onStart() }
            text.contains(stopWord) -> { lastFire = now; onStop() }
        }
    }

    override fun onPartialResult(h: String?) { h?.let { handle(it, "partial") } }
    override fun onResult(h: String?) { h?.let { handle(it, "text") } }
    override fun onFinalResult(h: String?) {}
    override fun onError(e: Exception?) { onError(e?.message ?: "Voice error") }
    override fun onTimeout() { speech?.startListening(this) }
}
