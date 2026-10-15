package com.fenix.mobilemcp.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR,
}

data class VoiceActivationEvent(
    val triggerSource: String, // "VOLUME_BUTTON", "KWS_KEYWORD", "UI_MIC"
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Gestor soberano de activación por voz F-Voz (100% offline, costo $0).
 * Maneja el atajo físico de botón de volumen (550 ms), KWS y el reconocimiento de voz local
 * para alimentar los turnos conversacionales de F-Chat.
 */
@Singleton
class FenixVoiceActivationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _transcribedText = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val transcribedText: SharedFlow<String> = _transcribedText.asSharedFlow()

    private val _activationEvents = MutableSharedFlow<VoiceActivationEvent>(extraBufferCapacity = 5)
    val activationEvents: SharedFlow<VoiceActivationEvent> = _activationEvents.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Llamado por McpAccessibilityService al detectar pulsación sostenida (>550ms)
     * del botón de volumen sin cambiar el volumen del sistema.
     */
    fun onVolumeButtonHoldTriggered() {
        Log.i(TAG, "Atajo de volumen activado (550ms hold). Iniciando F-Voz...")
        startListening(source = "VOLUME_BUTTON")
    }

    /**
     * Inicia la captura y reconocimiento de voz en el hilo principal.
     */
    fun startListening(source: String = "UI_MIC") {
        mainHandler.post {
            if (_voiceState.value == VoiceState.LISTENING) {
                Log.d(TAG, "Ya está escuchando")
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "SpeechRecognizer no disponible en el sistema")
                _voiceState.value = VoiceState.ERROR
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(FenixRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "es-AR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Solicitar procesamiento offline si el motor del dispositivo lo soporta
                    putExtra("android.speech.extra.PREFER_OFFLINE", true)
                }

                _voiceState.value = VoiceState.LISTENING
                scope.launch {
                    _activationEvents.emit(VoiceActivationEvent(triggerSource = source))
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar SpeechRecognizer: ${e.message}")
                _voiceState.value = VoiceState.ERROR
            }
        }
    }

    /**
     * Detiene la escucha activa de voz.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error al detener escucha: ${e.message}")
            }
            _voiceState.value = VoiceState.IDLE
        }
    }

    private inner class FenixRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _voiceState.value = VoiceState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            _voiceState.value = VoiceState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _voiceState.value = VoiceState.PROCESSING
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error: $error")
            _voiceState.value = VoiceState.IDLE
        }

        override fun onResults(results: Bundle?) {
            _voiceState.value = VoiceState.IDLE
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()
            if (!recognizedText.isNullOrBlank()) {
                Log.i(TAG, "Texto reconocido: $recognizedText")
                scope.launch {
                    _transcribedText.emit(recognizedText)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim()
            if (!partial.isNullOrBlank()) {
                Log.d(TAG, "Parcial: $partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        private const val TAG = "FenixVoice"
    }
}

