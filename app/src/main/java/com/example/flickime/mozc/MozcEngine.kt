package com.example.flickime.mozc

import android.content.Context
import android.util.Log
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import java.io.File
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output

/**
 * libmozc.so をプロセス内で一度だけ初期化するシングルトン。
 *
 * assets/mozc.data（辞書データ）は端末内のファイルパスとしてネイティブ側へ渡す必要があるため、
 * 初回起動時に内部ストレージへコピーしてから読み込む。
 */
object MozcEngine {
    private const val TAG = "MozcEngine"

    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return

        val dataFile = File(context.filesDir, "mozc.data")
        if (!dataFile.exists() || dataFile.length() == 0L) {
            context.assets.open("mozc.data").use { input ->
                dataFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val profileDir = File(context.filesDir, "mozc_profile").apply { mkdirs() }

        if (!MozcJNI.initialize()) {
            Log.e(TAG, "MozcJNI.initialize() に失敗しました")
            return
        }
        initialized = MozcJNI.onPostLoad(profileDir.absolutePath, dataFile.absolutePath)
        if (!initialized) {
            Log.e(TAG, "MozcJNI.onPostLoad() に失敗しました")
        }
    }

    val isReady: Boolean get() = initialized

    /** [input] を評価し、対応する [Output] を返す。 */
    fun eval(input: Input): Output {
        val command = Command.newBuilder().setInput(input).build()
        val outBytes = MozcJNI.evalCommand(command.toByteArray())
        return Command.parseFrom(outBytes).output
    }
}
