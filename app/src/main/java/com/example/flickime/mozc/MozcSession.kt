package com.example.flickime.mozc

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand

/**
 * 1回の変換セッション（かな入力の開始〜確定）を表す。
 *
 * ローマ字変換は行わず、フリックキーボードが確定させた「かな1文字」をそのまま
 * [KeyEvent.InputStyle.AS_IS] で送り込む。Mozc 側で読み（未確定文字列）として蓄積され、
 * 変換候補が生成される。
 */
class MozcSession {
    private var sessionId: Long = 0L

    fun create(): Output {
        val output = MozcEngine.eval(
            Input.newBuilder().setType(Input.CommandType.CREATE_SESSION).build(),
        )
        sessionId = output.id
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

    /** かな1文字を未確定文字列として送る。 */
    fun sendKanaCharacter(kana: String): Output {
        val key = KeyEvent.newBuilder()
            .setKeyString(kana)
            .setInputStyle(KeyEvent.InputStyle.AS_IS)
            .build()
        return sendKey(key)
    }

    fun sendSpecialKey(specialKey: KeyEvent.SpecialKey): Output {
        val key = KeyEvent.newBuilder().setSpecialKey(specialKey).build()
        return sendKey(key)
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
