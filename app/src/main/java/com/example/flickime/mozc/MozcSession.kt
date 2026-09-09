package com.example.flickime.mozc

import com.example.flickime.keyboard.FlickCandidate
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand

/**
 * 変換候補1件。[segmentCount] は Mozc がその読みを何文節に分けたか。
 * 少ないほど一続きの語として自然に解釈できたことを示すので、校正候補の並び順に使う。
 */
data class Prediction(val value: String, val segmentCount: Int)

/**
 * セッションが失われていて、送ったキーが受け付けられなかった応答かどうか。
 *
 * 変換エンジンはセッションを内部で整理することがあり、消えたセッションへ送ったキーは
 * 「結果も未確定文字列も無い」応答として返ってくる。これを「状態が変わらなかった」応答と
 * 取り違えると、打っても何も出ない状態のまま抜け出せなくなる。
 */
val Output.isSessionLost: Boolean
    get() = hasErrorCode() && errorCode == Output.ErrorCode.SESSION_FAILURE

/**
 * 1回の変換セッション（かな入力の開始〜確定）を表す。
 *
 * ローマ字変換は行わず、フリックキーボードが確定させた「かな1文字」をそのまま
 * [KeyEvent.InputStyle.AS_IS] で送り込む。Mozc 側で読み（未確定文字列）として蓄積され、
 * 変換候補が生成される。
 */
class MozcSession {
    private var sessionId: Long = 0L

    companion object {
        /** 1ページに載せる変換候補の数。候補一覧の展開表示で選べる数になる。 */
        private const val CANDIDATE_PAGE_SIZE = 32

        /** 次点候補に割り当てる確率の合計の上限。本命が埋もれないようにする。 */
        private const val MAX_TOTAL_ALTERNATE_PROBABILITY = 0.4

        /**
         * [readingChars]（かな1文字ずつ）を使い捨てのセッションへ流し込み、
         * 変換候補の上位 [limit] 件を返す。
         *
         * 「押し間違いだったかもしれない読み」を試しに変換させて校正候補を作るために使う。
         * 入力中のセッションとは独立しているので、現在の未確定文字列には影響しない。
         */
        fun predict(session: MozcSession, readingChars: List<String>): Prediction? {
            if (readingChars.isEmpty()) return null
            return try {
                for (ch in readingChars) {
                    session.sendKanaCharacter(ch)
                }
                // 実際に変換させる。未変換のままでは文節が常に1つで、
                // 読みとしての自然さを比べる材料にならないため。
                val converted = session.sendSpecialKey(KeyEvent.SpecialKey.SPACE)
                if (!converted.hasPreedit()) return null
                val value = converted.preedit.segmentList.joinToString("") { it.value }
                if (value.isEmpty()) return null
                Prediction(value, converted.preedit.segmentCount)
            } finally {
                // 次の読みのために空にする。セッションを読みごとに作り直すと
                // 変換エンジンが古いセッションを整理してしまい、入力中のセッションを
                // 巻き添えにすることがあるため、1つを使い回す。
                session.revert()
            }
        }
    }

    /**
     * セッションを開始する。
     *
     * [mobile] を立てると、モバイル IME 向けの挙動（変換キーを押さなくても候補を出す、
     * 候補を多めに返す）を Mozc に要求する。候補一覧の展開表示で選べる候補を増やすため、
     * 画面に出す入力セッションでのみ使う。校正候補を作る使い捨てセッションでは、
     * 変換結果が変わって並び順の判断がぶれないよう既定のままにしておく。
     */
    fun create(mobile: Boolean = false): Output {
        val output = MozcEngine.eval(
            Input.newBuilder().setType(Input.CommandType.CREATE_SESSION).build(),
        )
        sessionId = output.id
        if (mobile) {
            MozcEngine.eval(
                Input.newBuilder()
                    .setType(Input.CommandType.SET_REQUEST)
                    .setId(sessionId)
                    .setRequest(
                        Request.newBuilder()
                            .setMixedConversion(true)
                            .setCandidatePageSize(CANDIDATE_PAGE_SIZE),
                    )
                    .build(),
            )
        }
        return output
    }

    fun destroy() {
        MozcEngine.eval(
            Input.newBuilder()
                .setType(Input.CommandType.DELETE_SESSION)
                .setId(sessionId)
                .build(),
        )
    }

    /**
     * かな1文字を未確定文字列として送る。
     *
     * [alternates] はフリック判定が際どかった場合の次点候補（誤フリックの許容用）。
     * `probable_key_event` として確率つきで一緒に送ることで、Mozc の変換候補に
     * 「本当はこちらを狙っていたかもしれない」という可能性を反映してもらう。
     * 1文字（サロゲートペアを除く単一コードポイント）の候補のみ対象。
     */
    fun sendKanaCharacter(kana: String, alternates: List<FlickCandidate> = emptyList()): Output {
        val keyBuilder = KeyEvent.newBuilder()
            .setKeyString(kana)
            .setInputStyle(KeyEvent.InputStyle.AS_IS)

        val singleCodePointAlternates = alternates.filter { it.text.codePointCount(0, it.text.length) == 1 }
        if (singleCodePointAlternates.isNotEmpty() && kana.codePointCount(0, kana.length) == 1) {
            val rawTotal = singleCodePointAlternates.sumOf { it.probability.toDouble() }
            // 次点が増えても本命の確率が潰れないよう、合計が上限を超えたら按分して縮める
            val scale = if (rawTotal > MAX_TOTAL_ALTERNATE_PROBABILITY) {
                MAX_TOTAL_ALTERNATE_PROBABILITY / rawTotal
            } else {
                1.0
            }
            val alternateTotal = rawTotal * scale
            val primaryProbability = (1.0 - alternateTotal).coerceIn(0.05, 1.0)
            keyBuilder.addProbableKeyEvent(
                KeyEvent.ProbableKeyEvent.newBuilder()
                    .setKeyCode(kana.codePointAt(0))
                    .setProbability(primaryProbability),
            )
            for (alternate in singleCodePointAlternates) {
                keyBuilder.addProbableKeyEvent(
                    KeyEvent.ProbableKeyEvent.newBuilder()
                        .setKeyCode(alternate.text.codePointAt(0))
                        .setProbability(alternate.probability.toDouble() * scale),
                )
            }
        }
        return sendKey(keyBuilder.build())
    }

    fun sendSpecialKey(specialKey: KeyEvent.SpecialKey, vararg modifiers: KeyEvent.ModifierKey): Output {
        val builder = KeyEvent.newBuilder().setSpecialKey(specialKey)
        for (modifier in modifiers) builder.addModifierKeys(modifier)
        return sendKey(builder.build())
    }

    private fun sendKey(key: KeyEvent): Output = MozcEngine.eval(
        Input.newBuilder()
            .setType(Input.CommandType.SEND_KEY)
            .setId(sessionId)
            .setKey(key)
            .build(),
    )

    /** 指定した候補(id)を確定する。 */
    fun submitCandidate(candidateId: Int): Output = sendSessionCommand(
        SessionCommand.newBuilder()
            .setType(SessionCommand.CommandType.SUBMIT_CANDIDATE)
            .setId(candidateId)
            .build(),
    )

    /** 現在の未確定文字列/フォーカス中候補をそのまま確定する。 */
    fun submit(): Output = sendSessionCommand(
        SessionCommand.newBuilder().setType(SessionCommand.CommandType.SUBMIT).build(),
    )

    /** 変換候補の次ページ／前ページ（スペースキーでの巡回に相当）。 */
    fun convertNextPage(): Output = sendSessionCommand(
        SessionCommand.newBuilder().setType(SessionCommand.CommandType.CONVERT_NEXT_PAGE).build(),
    )

    fun convertPrevPage(): Output = sendSessionCommand(
        SessionCommand.newBuilder().setType(SessionCommand.CommandType.CONVERT_PREV_PAGE).build(),
    )

    /** 変換前の状態に戻す（ESCキー相当）。 */
    fun revert(): Output = sendSessionCommand(
        SessionCommand.newBuilder().setType(SessionCommand.CommandType.REVERT).build(),
    )

    private fun sendSessionCommand(command: SessionCommand): Output = MozcEngine.eval(
        Input.newBuilder()
            .setType(Input.CommandType.SEND_COMMAND)
            .setId(sessionId)
            .setCommand(command)
            .build(),
    )
}
