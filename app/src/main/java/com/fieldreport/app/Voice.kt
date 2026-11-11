package com.fieldreport.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")

/** Returns a function that opens the phone's speech prompt and hands back what was said. */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { latest(it) }
    }
    return {
        runCatching { launcher.launch(speechIntent().putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀하세요")) }
            .onFailure { Toast.makeText(ctx, "이 기기에서는 음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show() }
    }
}

/** A text field with a microphone button; speech is appended to what is already typed. */
@Composable
fun VoiceField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null, placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false, minLines: Int = 1, textStyle: TextStyle = LocalTextStyle.current, shape: Shape = RoundedCornerShape(14.dp),
    onDone: (() -> Unit)? = null,
) {
    val speak = rememberVoiceInput { said -> onValueChange(if (value.isBlank()) said else "$value $said") }
    OutlinedTextField(value, onValueChange, modifier, label = label, placeholder = placeholder, singleLine = singleLine, minLines = minLines, textStyle = textStyle, shape = shape,
        // With [onDone], the keyboard's enter key submits instead of doing nothing.
        keyboardOptions = if (onDone != null) KeyboardOptions(imeAction = ImeAction.Done) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = { IconButton(onClick = speak) { Icon(Icons.Filled.Mic, contentDescription = "음성으로 입력", tint = MaterialTheme.colorScheme.primary) } })
}

/**
 * Continuous dictation for meetings. The platform recognizer stops after each phrase, so this
 * restarts it until [stop] is called. Call from the main thread.
 */
class Dictation(private val ctx: Context, private val onFinal: (String) -> Unit, private val onPartial: (String) -> Unit, private val fail: (String) -> Unit) : RecognitionListener {
    private var sr: SpeechRecognizer? = null
    private var errors = 0
    private val handler = Handler(Looper.getMainLooper())
    private val intent = speechIntent().putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    var running = false; private set

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { fail("이 기기에서는 음성 인식을 사용할 수 없습니다"); return }
        running = true; errors = 0
        sr = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(this); it.startListening(intent) }
    }

    fun stop() { running = false; handler.removeCallbacksAndMessages(null); sr?.destroy(); sr = null; onPartial("") }

    private fun again() { handler.postDelayed({ if (running) sr?.startListening(intent) }, 250) }
    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    override fun onResults(results: Bundle?) { errors = 0; first(results).takeIf { it.isNotEmpty() }?.let(onFinal); onPartial(""); if (running) again() }
    override fun onPartialResults(partialResults: Bundle?) { onPartial(first(partialResults)) }
    override fun onError(error: Int) {
        if (!running) return
        when {
            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stop(); fail("마이크 권한이 필요합니다") }
            // Silence and "no match" are normal between sentences; anything else that keeps repeating is a real failure.
            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> again()
            ++errors > 8 -> { stop(); fail("음성 인식이 계속 실패해 녹음을 멈췄습니다. 인터넷 연결을 확인해 주세요") }
            else -> again()
        }
    }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
