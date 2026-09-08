package com.google.android.apps.inputmethod.libs.mozc.session

/**
 * libmozc.so への薄いJNIバインディング。
 *
 * クラス名・パッケージ名は libmozc.so にコンパイル時に埋め込まれた
 * JNIエクスポートシンボル (Java_com_google_android_apps_inputmethod_libs_mozc_session_MozcJNI_initialize)
 * と完全に一致させる必要があるため、変更してはならない。
 *
 * ネイティブ側は `static` メソッド（第2引数が jclass）として登録するため、
 * Kotlin の `object`（インスタンスメソッドになる）ではなく
 * `companion object` + `@JvmStatic` で真の static メソッドにする必要がある。
 *
 * [initialize] 呼び出し時に、このクラスに対して evalCommand / onPostLoad / getDataVersion
 * のネイティブメソッドが動的に登録される（JNI の RegisterNatives による）。
 */
class MozcJNI private constructor() {
    companion object {
        init {
            System.loadLibrary("mozc")
        }

        /** ネイティブメソッドを登録する。他のメソッドを呼ぶ前に一度だけ呼び出すこと。 */
        @JvmStatic
        external fun initialize(): Boolean

        /** 変換エンジンを初期化する。[dataFilePath] は展開済みの mozc.data の実ファイルパス。 */
        @JvmStatic
        external fun onPostLoad(userProfileDirectoryPath: String, dataFilePath: String): Boolean

        /** シリアライズされた mozc.protocol.Command を評価し、結果を返す。 */
        @JvmStatic
        external fun evalCommand(inBytes: ByteArray): ByteArray

        @JvmStatic
        external fun getDataVersion(): String
    }
}
