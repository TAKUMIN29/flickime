package com.example.flickime.mozc

import android.content.Context
import android.util.Log
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
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

    private val initStarted = AtomicBoolean(false)

    /**
     * 辞書の読み込みを開始する。すでに読み込み済み、または読み込み中なら何もしない。
     *
     * 初回起動時は assets の辞書（数十MB）を内部ストレージへ展開する必要があり、
     * メインスレッドで行うとキーボードが出るまで固まってしまう。読み込みが終わるまでは
     * [isReady] が false のままなので、呼び出し側は変換を使わない直接入力に切り替えて
     * 打鍵を取りこぼさないようにする。
     */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        if (!initStarted.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread({ initialize(app) }, "mozc-init").start()
    }

    private fun initialize(context: Context) {
        try {
            val dataFile = File(context.filesDir, "mozc.data")
            if (!dataFile.exists() || dataFile.length() == 0L) {
                copyDataFile(context, dataFile)
            }
            val profileDir = File(context.filesDir, "mozc_profile").apply { mkdirs() }

            // ネイティブ側の呼び出しは eval と直列化する（スレッドセーフではないため）
            synchronized(this) {
                if (!MozcJNI.initialize()) {
                    Log.e(TAG, "MozcJNI.initialize() に失敗しました")
                    initStarted.set(false)
                    return
                }
                initialized = MozcJNI.onPostLoad(profileDir.absolutePath, dataFile.absolutePath)
            }
            if (!initialized) {
                Log.e(TAG, "MozcJNI.onPostLoad() に失敗しました")
                initStarted.set(false)
            }
        } catch (e: Exception) {
            // 辞書の展開に失敗しても IME 自体は動かし続ける（変換なしの直接入力になる）。
            // 次の打鍵でもう一度やり直せるようにしておく。
            Log.e(TAG, "Mozc の初期化に失敗しました", e)
            initStarted.set(false)
        }
    }

    /**
     * assets の辞書データを内部ストレージへ書き出す。
     *
     * 途中で中断されても壊れたファイルが残らないよう、一時ファイルへ書ききってから
     * 置き換える。長さだけ見て「展開済み」と判断しているので、半端なファイルが残ると
     * 以降ずっと壊れた辞書を読み込み続けてしまう。
     */
    private fun copyDataFile(context: Context, dataFile: File) {
        val tmp = File(dataFile.parentFile, "mozc.data.tmp")
        tmp.delete()
        context.assets.open("mozc.data").use { input ->
            tmp.outputStream().use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (!tmp.renameTo(dataFile)) {
            tmp.delete()
            throw java.io.IOException("辞書データを配置できませんでした")
        }
    }

    val isReady: Boolean get() = initialized

    /**
     * [input] を評価し、対応する [Output] を返す。
     *
     * ネイティブ側のセッションハンドラはスレッドセーフではないため、
     * 入力中の変換と校正候補のバックグラウンド問い合わせが同時に走らないよう直列化する。
     */
    @Synchronized
    fun eval(input: Input): Output {
        // 初期化できていない状態でネイティブ側を呼ぶと戻り値が無く、そのまま解析すると
        // 例外で IME ごと落ちる。空の応答を返し、呼び出し側の直接入力に任せる。
        if (!initialized) return Output.getDefaultInstance()
        return try {
            val command = Command.newBuilder().setInput(input).build()
            val outBytes = MozcJNI.evalCommand(command.toByteArray())
                ?: return Output.getDefaultInstance()
            Command.parseFrom(outBytes).output
        } catch (e: Exception) {
            Log.e(TAG, "変換エンジンの呼び出しに失敗しました", e)
            Output.getDefaultInstance()
        }
    }
}
