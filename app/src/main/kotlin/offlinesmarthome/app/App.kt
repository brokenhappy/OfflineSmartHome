package offlinesmarthome.app

import ai.picovoice.picovoice.Picovoice
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

data class Environment(
    val kontextFile: File,
    val yamlContextFile: File,
    val picovoiceKeyWordFile: File,
    val rawRhinoContextFile: File,
)

data class Secrets(
    val picoVoiceAccessKey: String,
)

fun main() {
    GenerateRhinoCodeTask(environment).generateCodeFromRhinoContext()
    val picovoice = buildPicovoice { intent ->
        print(intent)
        when (intent) {
            Intent.NotUnderstood -> {}
            is Intent.ChangeLightState -> {
                println(intent.state)
            }
        }
    }

    getDefaultMicrophone().readAudio(frameLength = picovoice.frameLength) { frame ->
        picovoice.process(frame)
    }
}

private inline fun TargetDataLine.readAudio(frameLength: Int, onEachFrame: (frame: ShortArray) -> Unit): Nothing {
    val captureBuffer = ByteBuffer.allocate(frameLength * 2)
        .also { it.order(ByteOrder.LITTLE_ENDIAN) }
    val picovoiceBuffer = ShortArray(frameLength)

    while (true) {
        if (read(captureBuffer.array(), 0, captureBuffer.capacity()) != frameLength * 2)
            continue
        captureBuffer.asShortBuffer().get(picovoiceBuffer)
        onEachFrame(picovoiceBuffer)
    }
}

private fun getDefaultMicrophone(): TargetDataLine {
    val format = AudioFormat(16000f, 16, 1, true, false)
    return (AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine).apply {
        open(format)
        start()
    }
}

private inline fun buildPicovoice(crossinline callback: (Intent) -> Unit): Picovoice =
    Picovoice.Builder()
        .setAccessKey(secret.picoVoiceAccessKey)
        .setKeywordPath(environment.picovoiceKeyWordFile.path)
        .setWakeWordCallback { println("Wake word called") }
        .setContextPath(environment.rawRhinoContextFile.path)
        .setInferenceCallback { callback(Intent.from(it)) }
        .build()