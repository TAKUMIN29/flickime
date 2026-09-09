package com.example.flickime

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flickime.clip.ClipAdapter
import com.example.flickime.clip.ClipItem
import com.example.flickime.clip.ClipboardStore
import com.example.flickime.dict.UserDictionary
import com.example.flickime.edit.EditHistory
import com.example.flickime.keyboard.Flick
import com.example.flickime.keyboard.FlickCandidate
import com.example.flickime.keyboard.FlickGuideView
import com.example.flickime.keyboard.FlickKeyboardView
import com.example.flickime.keyboard.KanaConverter
import com.example.flickime.keyboard.KanaModifier
import com.example.flickime.keyboard.KeyLayouts
import com.example.flickime.keyboard.KeySpec
import com.example.flickime.keyboard.KeyType
import com.example.flickime.mozc.CandidateAdapter
import com.example.flickime.mozc.CandidateItem
import com.example.flickime.mozc.MozcEngine
import com.example.flickime.mozc.MozcSession
import java.util.concurrent.Executors
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent as MozcKeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output as MozcOutput

/**
 * フリック入力 IME 本体。
 *
 * 提供する機能:
 *  - ケータイ配列のフリック入力（かな / 英字 / 数字 / 記号）
 *  - クリップボード履歴（Windows の Win+V 相当）
 *  - 元に戻す / やり直し（この IME が行った編集に対して）
 *
 * 注意: かな漢字変換は行わない直接入力方式。
 */
class FlickImeService : InputMethodService(), FlickKeyboardView.Listener {

    companion object {
        private const val TAG = "FlickImeService"

        /** 校正候補を作るために試す「押し間違いだったかもしれない読み」の最大数。 */
        private const val MAX_CORRECTION_READINGS = 12

        /** 実際に候補ストリップへ並べる校正候補の最大数。 */
        private const val MAX_CORRECTIONS_SHOWN = 3

        /** 連続入力中に毎回問い合わせないための待ち時間。 */
        private const val CORRECTION_DELAY_MS = 160L

        /** 候補一覧を展開したときのグリッドの列数。 */
        private const val CANDIDATE_GRID_COLUMNS = 3

        /** 単語削除で遡って調べる最大文字数。 */
        private const val WORD_DELETE_LOOKBEHIND = 64

        /** 変換候補の先頭に足すユーザー辞書の語の最大数。 */
        private const val MAX_USER_DICT_CANDIDATES = 3
    }

    /** 未確定文字列を構成するかな1文字と、その打鍵時に考えられた押し間違い候補。 */
    private data class ComposedChar(val text: String, val alternates: List<FlickCandidate>)

    private lateinit var prefs: Prefs
    private lateinit var clipStore: ClipboardStore
    private lateinit var userDict: UserDictionary
    private var clipboardManager: ClipboardManager? = null

    private val history = EditHistory()

    private var keyboardView: FlickKeyboardView? = null
    private var guideView: FlickGuideView? = null
    private var clipPanel: View? = null
    private var clipEmptyView: TextView? = null
    private var btnUndo: TextView? = null
    private var btnRedo: TextView? = null
    private var clipAdapter: ClipAdapter? = null

    private var mode: KeyLayouts.Mode = KeyLayouts.Mode.KANA

    /** 直前にキー入力で確定した文字。文字送りで差し替える対象。 */
    private var lastCommitted: String? = null

    // カーソル位置の追跡。自分の編集かどうかの判定に使う。
    private var selStart = 0
    private var selEnd = 0
    private var expectedCursor = -1

    // 変換キー（空白キーの上フリック）用。直前に途切れずに入力したかなの語を追跡する。
    private var wordOriginal: String? = null
    private var wordDisplayed: String? = null
    private var convertStage = 0

    /** パスワード入力欄かどうか。変換や校正候補を通さないための判定に使う。 */
    private var passwordField = false

    // かな漢字変換(Mozc)関連。
    private var kanjiConversionEnabled = false
    private var mozcSession: MozcSession? = null

    /** 変換セッション開始時点のカーソル位置。確定/破棄時にここへ戻す。 */
    private var composingBase = -1

    /** 画面に出している未確定文字列。削除が空振りしていないかの判定に使う。 */
    private var currentPreedit = ""

    /** 未確定文字列を1文字ずつ記録したもの。校正候補の読みを組み立てるのに使う。 */
    private val composedChars = mutableListOf<ComposedChar>()

    /** 非同期で作った校正候補が、まだ現在の未確定文字列のものかを判定する世代番号。 */
    private var correctionGeneration = 0
    private val correctionExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val correctionRunnable = Runnable { runCorrections() }

    private var candidateStrip: LinearLayout? = null
    private var candidateScroll: View? = null
    private var candidateRow: View? = null
    private var candidatePanel: View? = null
    private var candidateAdapter: CandidateAdapter? = null

    /** 現在表示中の変換候補。ストリップと展開グリッドの両方がこれを描画する。 */
    private var candidateItems: List<CandidateItem> = emptyList()

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!prefs.clipboardEnabled) return@OnPrimaryClipChangedListener
        try {
            val clip = clipboardManager?.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            ) {
                // パスワードマネージャ等が機微情報として明示したコピーは履歴に残さない
                return@OnPrimaryClipChangedListener
            }
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            if (text.isNotEmpty()) {
                clipStore.add(text)
                refreshClipList()
            }
        } catch (e: SecurityException) {
            // 既定の IME でない間はクリップボードを読めない（Android 10 以降の制限）
            Log.d(TAG, "クリップボードを読み取れませんでした: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        clipStore = ClipboardStore.get(this)
        userDict = UserDictionary.get(this)
        history.onChanged = { updateToolbarState() }

        clipboardManager = (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.addPrimaryClipChangedListener(clipListener)
        }

        MozcEngine.ensureInitialized(applicationContext)
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        mainHandler.removeCallbacks(correctionRunnable)
        correctionExecutor.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 入力ビュー
    // ------------------------------------------------------------------

    override fun onCreateInputView(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.input_view, null)

        val keyboard = root.findViewById<FlickKeyboardView>(R.id.keyboard)
        val guide = root.findViewById<FlickGuideView>(R.id.guide)
        keyboard.guideView = guide
        keyboard.listener = this
        keyboardView = keyboard
        guideView = guide

        // guide は match_parent にできない（ルートの wrap_content 計算に巻き込まれて
        // ウィンドウが画面ほぼ全高に膨らんでしまうため）。実際に見えているキーボード
        // 領域(content_root)と常に同じ高さになるよう、レイアウトのたびに追従させる。
        val contentRoot = root.findViewById<View>(R.id.content_root)
        contentRoot.viewTreeObserver.addOnGlobalLayoutListener {
            val params = guide.layoutParams
            if (params.height != contentRoot.height) {
                params.height = contentRoot.height
                guide.layoutParams = params
            }
        }

        btnUndo = root.findViewById<TextView>(R.id.btn_undo).also {
            it.setOnClickListener { performUndo() }
        }
        btnRedo = root.findViewById<TextView>(R.id.btn_redo).also {
            it.setOnClickListener { performRedo() }
        }
        root.findViewById<TextView>(R.id.btn_clip).setOnClickListener { toggleClipPanel() }
        root.findViewById<TextView>(R.id.btn_settings).setOnClickListener { openSettings() }
        setupResizeHandle(root.findViewById(R.id.resize_handle))
        candidateScroll = root.findViewById<HorizontalScrollView>(R.id.candidate_scroll)
        candidateStrip = root.findViewById(R.id.candidate_strip)
        candidateRow = root.findViewById(R.id.candidate_row)
        candidatePanel = root.findViewById(R.id.candidate_panel)
        root.findViewById<TextView>(R.id.btn_candidate_expand).setOnClickListener { toggleCandidatePanel() }
        root.findViewById<TextView>(R.id.btn_candidate_close).setOnClickListener { hideCandidatePanel() }

        val candidates = CandidateAdapter { selectCandidate(it) }
        candidateAdapter = candidates
        root.findViewById<RecyclerView>(R.id.candidate_list).apply {
            layoutManager = GridLayoutManager(this@FlickImeService, CANDIDATE_GRID_COLUMNS)
            this.adapter = candidates
        }

        clipPanel = root.findViewById(R.id.clip_panel)
        clipEmptyView = root.findViewById(R.id.clip_empty)
        root.findViewById<TextView>(R.id.btn_clip_close).setOnClickListener { hideClipPanel() }
        root.findViewById<TextView>(R.id.btn_clip_clear).setOnClickListener {
            clipStore.clearUnpinned()
            refreshClipList()
        }

        val adapter = ClipAdapter(
            onPaste = { pasteClip(it) },
            onTogglePin = {
                clipStore.togglePin(it.text)
                refreshClipList()
            },
            onDelete = {
                clipStore.remove(it.text)
                refreshClipList()
            },
        )
        clipAdapter = adapter
        root.findViewById<RecyclerView>(R.id.clip_list).apply {
            layoutManager = LinearLayoutManager(this@FlickImeService)
            this.adapter = adapter
        }

        applyPrefs()
        updateLayout()
        updateToolbarState()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) {
            history.clear()
            lastCommitted = null
            expectedCursor = -1
            resetWord()
            mozcSession?.destroy()
            mozcSession = null
            composingBase = -1
            updateCandidateStrip(null)
        }
        mode = defaultModeFor(attribute)
        passwordField = isPasswordField(attribute)
    }

    private fun isPasswordField(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER) {
            return inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            -> true

            else -> false
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyPrefs()
        hideClipPanel()
        updateLayout()
        updateToolbarState()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lastCommitted = null
        hideClipPanel()
        mozcSession?.destroy()
        mozcSession = null
        composingBase = -1
        updateCandidateStrip(null)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd

        val selfEdit = expectedCursor >= 0 && newSelStart == expectedCursor && newSelEnd == expectedCursor
        if (!selfEdit) {
            // カーソル移動や他所からの編集。ここで入力のまとまりを区切る。
            history.breakMerge()
            lastCommitted = null
            expectedCursor = -1
            resetWord()
            if (mozcSession != null) finalizeComposition()
        }
    }

    /** 横画面でも入力欄を全画面に置き換えない。 */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun defaultModeFor(info: EditorInfo?): KeyLayouts.Mode {
        val inputType = info?.inputType ?: return KeyLayouts.Mode.KANA
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME ->
                KeyLayouts.Mode.NUMBER

            InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                -> KeyLayouts.Mode.ALPHABET

                else -> KeyLayouts.Mode.KANA
            }

            else -> KeyLayouts.Mode.KANA
        }
    }

    /** ハンドルを上下ドラッグしてキーボードの高さをその場で調整する。 */
    private fun setupResizeHandle(handle: View) {
        var dragStartRawY = 0f
        var dragStartHeightPx = 0f

        handle.setOnTouchListener { _, event ->
            val density = resources.displayMetrics.density
            val minPx = 40 * density
            val maxPx = 76 * density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawY = event.rawY
                    dragStartHeightPx = keyboardView?.rowHeightPx ?: (prefs.keyHeightDp * density)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // ハンドルを上へドラッグするほどキーボードを高くする
                    val movedUp = dragStartRawY - event.rawY
                    keyboardView?.rowHeightPx = (dragStartHeightPx + movedUp).coerceIn(minPx, maxPx)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val px = keyboardView?.rowHeightPx ?: dragStartHeightPx
                    prefs.keyHeightDp = (px / density).toInt()
                    true
                }

                else -> false
            }
        }
    }

    private fun applyPrefs() {
        val density = resources.displayMetrics.density
        keyboardView?.let {
            it.rowHeightPx = prefs.keyHeightDp * density
            it.flickThresholdPx = prefs.flickThresholdDp * density
            it.hapticEnabled = prefs.hapticEnabled
            it.soundEnabled = prefs.keySoundEnabled
            it.oneHandedMode = oneHandedModeFromPrefs(prefs.oneHandedMode)
        }
        kanjiConversionEnabled = prefs.kanjiConversionEnabled
    }

    private fun updateLayout() {
        katakanaKeyShown = canConvertToKatakana()
        keyboardView?.keyRows = decoratedLayout(mode)
    }

    /** 左列の動的キーが今「カナ」を表示しているか。無駄なレイアウト組み直しを避けるために持つ。 */
    private var katakanaKeyShown = false

    /** 全角で入力するモードか。空白キーが全角スペースを入れるかの判定に使う。 */
    private fun usesFullWidth(): Boolean = mode == KeyLayouts.Mode.KANA ||
        mode == KeyLayouts.Mode.ALPHABET_FULL ||
        mode == KeyLayouts.Mode.NUMBER_FULL ||
        mode == KeyLayouts.Mode.SYMBOLS

    /** カナ変換キーを出せる状態か（かなモードで、変換対象の文字列がある）。 */
    private fun canConvertToKatakana(): Boolean =
        mode == KeyLayouts.Mode.KANA && (mozcSession != null || wordOriginal != null)

    /**
     * 状況に応じてキーを差し替えたレイアウトを返す。
     *
     *  - 変換中は空白キー（改行の1つ上）を「変換」キーにする（Gboard等と同じ配置）
     *  - 左列の下から2番目は「123」「１２３」「カナ」を状況で入れ替える（Simeji と同じ挙動）
     */
    private fun decoratedLayout(mode: KeyLayouts.Mode): List<List<KeySpec>> {
        val base = KeyLayouts.of(mode)
        val converting = mozcSession != null
        val convertRow = base.size - 2
        val dynamicKey = when {
            canConvertToKatakana() -> KeyLayouts.KATAKANA_KEY
            mode == KeyLayouts.Mode.NUMBER -> KeyLayouts.NUMBER_FULL_KEY
            else -> KeyLayouts.NUMBER_KEY
        }
        return base.mapIndexed { rowIndex, row ->
            row.map { key ->
                when {
                    key.type == KeyType.NUM_OR_KANA -> dynamicKey
                    converting && rowIndex == convertRow && key.type == KeyType.SPACE -> KeyLayouts.CONVERT
                    else -> key
                }
            }
        }
    }

    private fun oneHandedModeFromPrefs(value: Int): FlickKeyboardView.OneHandedMode = when (value) {
        1 -> FlickKeyboardView.OneHandedMode.LEFT
        2 -> FlickKeyboardView.OneHandedMode.RIGHT
        else -> FlickKeyboardView.OneHandedMode.OFF
    }

    /** 設定キーの上フリック。オフ→左寄せ→右寄せ→オフ の順に切り替える。 */
    private fun cycleOneHandedMode() {
        prefs.oneHandedMode = (prefs.oneHandedMode + 1) % 3
        keyboardView?.oneHandedMode = oneHandedModeFromPrefs(prefs.oneHandedMode)
    }

    private fun updateToolbarState() {
        btnUndo?.let {
            it.isEnabled = history.canUndo
            it.alpha = if (history.canUndo) 1f else 0.35f
        }
        btnRedo?.let {
            it.isEnabled = history.canRedo
            it.alpha = if (history.canRedo) 1f else 0.35f
        }
    }

    // ------------------------------------------------------------------
    // キー入力
    // ------------------------------------------------------------------

    override fun onKey(key: KeySpec, flick: Flick, tapCount: Int, alternates: List<FlickCandidate>) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyType.CHAR -> handleChar(ic, key, flick, tapCount, alternates)
            KeyType.MODIFIER -> handleModifier(ic, flick)
            KeyType.BACKSPACE -> if (flick == Flick.LEFT) handleDeleteWord(ic) else handleBackspace(ic)
            KeyType.SPACE -> when {
                mozcSession != null -> applyMozcOutput(mozcSession!!.sendSpecialKey(MozcKeyEvent.SpecialKey.SPACE))
                flick == Flick.UP -> handleConvert(ic)
                else -> {
                    commit(ic, if (usesFullWidth()) "　" else " ")
                    resetWord()
                }
            }

            KeyType.ENTER -> handleEnter(ic)
            KeyType.CURSOR_LEFT -> moveCursor(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyType.CURSOR_RIGHT -> moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT)

            KeyType.MODE -> when (flick) {
                Flick.UP -> toggleKanjiConversion()
                else -> {
                    finalizeComposition()
                    mode = KeyLayouts.nextMode(mode)
                    updateLayout()
                    resetWord()
                }
            }

            KeyType.SYMBOL -> when (flick) {
                Flick.UP -> cycleOneHandedMode()
                else -> {
                    finalizeComposition()
                    mode = if (mode == KeyLayouts.Mode.SYMBOLS) {
                        KeyLayouts.Mode.KANA
                    } else {
                        KeyLayouts.Mode.SYMBOLS
                    }
                    updateLayout()
                    resetWord()
                }
            }

            KeyType.CASE -> {
                handleCase(ic)
                resetWord()
            }
            KeyType.NUM_OR_KANA -> when {
                flick == Flick.UP -> {
                    finalizeComposition()
                    switchInputMethod()
                    resetWord()
                }

                // F7 が「未確定文字列をカタカナに変換」。SpecialKey.KATAKANA は
                // ハードウェアのカタカナキー相当で、入力モードを切り替えるだけなので使わない。
                mozcSession != null ->
                    applyMozcOutput(mozcSession!!.sendSpecialKey(MozcKeyEvent.SpecialKey.F7))

                // かな漢字変換オフでも、直前に打った語はカタカナに変換できる
                canConvertToKatakana() -> handleConvert(ic)

                else -> {
                    finalizeComposition()
                    mode = KeyLayouts.nextNumberMode(mode)
                    updateLayout()
                    resetWord()
                }
            }
            // 左右で文節を移動、上下で文節の区切りを伸縮する（Mozc 本来のキー操作に合わせている）
            KeyType.CONVERT -> mozcSession?.let { session ->
                val output = when (flick) {
                    Flick.LEFT -> session.sendSpecialKey(MozcKeyEvent.SpecialKey.LEFT)
                    Flick.RIGHT -> session.sendSpecialKey(MozcKeyEvent.SpecialKey.RIGHT)
                    Flick.UP -> session.sendSpecialKey(
                        MozcKeyEvent.SpecialKey.RIGHT,
                        MozcKeyEvent.ModifierKey.SHIFT,
                    )

                    Flick.DOWN -> session.sendSpecialKey(
                        MozcKeyEvent.SpecialKey.LEFT,
                        MozcKeyEvent.ModifierKey.SHIFT,
                    )

                    Flick.CENTER -> session.sendSpecialKey(MozcKeyEvent.SpecialKey.SPACE)
                }
                applyMozcOutput(output)
            }
        }
    }

    override fun onKeyRepeat(key: KeySpec) {
        val ic = currentInputConnection ?: return
        when (key.type) {
            KeyType.BACKSPACE -> handleBackspace(ic)
            KeyType.CURSOR_LEFT -> moveCursor(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyType.CURSOR_RIGHT -> moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT)
            else -> Unit
        }
    }

    private fun handleChar(
        ic: InputConnection,
        key: KeySpec,
        flick: Flick,
        tapCount: Int,
        alternates: List<FlickCandidate> = emptyList(),
    ) {
        // 同じキーの連打はフリックの候補を順に送る（ケータイ打ち）
        val previous = lastCommitted
        if (flick == Flick.CENTER && tapCount > 0 && previous != null && key.hasFlickVariants()) {
            val next = key.cycled(tapCount)
            if (next != null) {
                if (mozcSession != null) {
                    replaceLastMozcChar(next)
                    lastCommitted = next
                    return
                }
                if (replaceBeforeCursor(ic, previous, next)) {
                    lastCommitted = next
                    onWordCharReplaced(next)
                    return
                }
            }
        }

        val text = key.output(flick) ?: key.output(Flick.CENTER) ?: return
        // パスワード欄では変換エンジンに文字を渡さない（学習・予測に残さないため）
        if (kanjiConversionEnabled && mode == KeyLayouts.Mode.KANA && !passwordField) {
            sendMozcKana(text, alternates)
            lastCommitted = text
            return
        }
        commit(ic, text)
        lastCommitted = text
        onWordCharCommitted(text)
    }

    /** 「小゛゜」キー。カーソル直前の文字を変換する。 */
    private fun handleModifier(ic: InputConnection, flick: Flick) {
        val before = ic.getTextBeforeCursor(1, 0)?.toString()
        if (before.isNullOrEmpty()) return
        val c = before[0]
        val converted = KanaModifier.apply(c, flick)
            ?: KanaModifier.cycle(c)
            ?: KanaModifier.toggleCase(c)
            ?: return
        val newText = converted.toString()
        if (mozcSession != null) {
            replaceLastMozcChar(newText)
            lastCommitted = newText
            return
        }
        if (replaceBeforeCursor(ic, before, newText)) {
            lastCommitted = newText
            onWordCharReplaced(newText)
        }
    }

    /** 英字モードの a/A キー。直前の文字の大文字小文字を切り替える。 */
    private fun handleCase(ic: InputConnection) {
        val before = ic.getTextBeforeCursor(1, 0)?.toString()
        if (before.isNullOrEmpty()) return
        val converted = KanaModifier.toggleCase(before[0]) ?: return
        val newText = converted.toString()
        if (replaceBeforeCursor(ic, before, newText)) {
            lastCommitted = newText
        }
    }

    /**
     * 削除キーの左フリック。変換中なら未確定文字列ごと、そうでなければ単語単位で消す。
     *
     * 日本語には単語の区切りが無いので、カーソル直前の文字と同じ種別
     * （ひらがな/カタカナ/漢字/英数/それ以外）が続く範囲をひとまとまりとみなす。
     */
    private fun handleDeleteWord(ic: InputConnection) {
        lastCommitted = null
        resetWord()

        if (mozcSession != null) {
            clearComposition(ic)
            return
        }

        val before = ic.getTextBeforeCursor(WORD_DELETE_LOOKBEHIND, 0)?.toString()
        if (before.isNullOrEmpty()) return
        val kind = charKind(before.last())
        val deleteLength = before.reversed().takeWhile { charKind(it) == kind }.length
        val deleted = before.takeLast(deleteLength)

        ic.beginBatchEdit()
        ic.deleteSurroundingText(deleteLength, 0)
        ic.endBatchEdit()
        history.recordDelete(deleted)
        expectedCursor = (minOf(selStart, selEnd) - deleteLength).coerceAtLeast(0)
        selStart = expectedCursor
        selEnd = expectedCursor
    }

    private fun charKind(c: Char): Int = when {
        c in 'ぁ'..'ゖ' || c == 'ー' -> 0
        c in 'ァ'..'ヺ' -> 1
        c in '一'..'龥' || c == '々' -> 2
        c.isLetterOrDigit() -> 3
        c.isWhitespace() -> 4
        else -> 5
    }

    private fun handleBackspace(ic: InputConnection) {
        lastCommitted = null
        resetWord()

        mozcSession?.let {
            val before = currentPreedit
            composedChars.removeLastOrNull()
            val output = it.sendSpecialKey(MozcKeyEvent.SpecialKey.BACKSPACE)
            if (!output.hasResult() && !output.hasPreedit()) {
                // 最後の1文字を消すと Mozc は preedit を返さない。これは「状態が変わらなかった」
                // 応答（変換キー連打など）と見分けがつかないので、バックスペースのときだけ
                // 「未確定文字列が空になった」と解釈してここで閉じる。
                clearComposition(ic)
                return
            }
            applyMozcOutput(output)
            // 何を返されても削除は必ず前に進める。未確定文字列が縮まなかった場合は、
            // 変換セッションごと畳んで「削除キーが効かない」状態に陥らないようにする。
            if (mozcSession != null && currentPreedit == before) clearComposition(ic)
            return
        }

        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.beginBatchEdit()
            ic.commitText("", 1)
            ic.endBatchEdit()
            history.recordDelete(selected.toString())
            expectedCursor = minOf(selStart, selEnd)
            selStart = expectedCursor
            selEnd = expectedCursor
            return
        }

        val before = ic.getTextBeforeCursor(2, 0)?.toString()
        if (before.isNullOrEmpty()) {
            // テキストを読み取れない相手（一部のゲームなど）にはキーイベントで送る
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            history.breakMerge()
            return
        }

        val deleteLength = if (
            before.length >= 2 && Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])
        ) 2 else 1
        val deleted = before.takeLast(deleteLength)

        ic.beginBatchEdit()
        ic.deleteSurroundingText(deleteLength, 0)
        ic.endBatchEdit()
        history.recordDelete(deleted)

        expectedCursor = (selStart - deleteLength).coerceAtLeast(0)
        selStart = expectedCursor
        selEnd = expectedCursor
    }

    private fun handleEnter(ic: InputConnection) {
        mozcSession?.let {
            applyMozcOutput(it.sendSpecialKey(MozcKeyEvent.SpecialKey.ENTER))
            return
        }

        val info = currentInputEditorInfo
        val imeOptions = info?.imeOptions ?: 0
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
            lastCommitted = null
            history.breakMerge()
        } else {
            commit(ic, "\n")
        }
        resetWord()
    }

    private fun moveCursor(keyCode: Int) {
        finalizeComposition()
        sendDownUpKeyEvents(keyCode)
        lastCommitted = null
        history.breakMerge()
        expectedCursor = -1
        resetWord()
    }

    private fun commit(ic: InputConnection, text: String) {
        ic.beginBatchEdit()
        ic.commitText(text, 1)
        ic.endBatchEdit()
        history.recordInsert(text)

        val base = minOf(selStart, selEnd)
        expectedCursor = base + text.length
        selStart = expectedCursor
        selEnd = expectedCursor
        lastCommitted = null
    }

    /** カーソル直前が [old] と一致していれば [new] に置き換える。 */
    private fun replaceBeforeCursor(ic: InputConnection, old: String, new: String): Boolean {
        val actual = ic.getTextBeforeCursor(old.length, 0)?.toString()
        if (actual != old) return false

        ic.beginBatchEdit()
        ic.deleteSurroundingText(old.length, 0)
        ic.commitText(new, 1)
        ic.endBatchEdit()
        history.recordModifyLast(old, new)

        expectedCursor = (minOf(selStart, selEnd) - old.length + new.length).coerceAtLeast(0)
        selStart = expectedCursor
        selEnd = expectedCursor
        return true
    }

    // ------------------------------------------------------------------
    // 元に戻す / やり直し
    // ------------------------------------------------------------------

    private fun performUndo() {
        val ic = currentInputConnection ?: return
        finalizeComposition()
        lastCommitted = null
        expectedCursor = -1
        resetWord()
        history.undo(ic)
        updateToolbarState()
    }

    private fun performRedo() {
        val ic = currentInputConnection ?: return
        finalizeComposition()
        lastCommitted = null
        expectedCursor = -1
        resetWord()
        history.redo(ic)
        updateToolbarState()
    }

    // ------------------------------------------------------------------
    // カタカナ / 半角カタカナ変換
    // ------------------------------------------------------------------

    /** 直前に入力したかなの語を記録する（変換キー用）。 */
    private fun onWordCharCommitted(text: String) {
        if (mode != KeyLayouts.Mode.KANA) {
            resetWord()
            return
        }
        if (convertStage != 0) resetWord()
        val next = wordOriginal.orEmpty() + text
        wordOriginal = next
        wordDisplayed = next
        refreshDynamicKey()
    }

    /** 文字送り・濁点付与など、直前の1文字を差し替えたときに追跡を更新する。 */
    private fun onWordCharReplaced(newLastChar: String) {
        if (mode != KeyLayouts.Mode.KANA || convertStage != 0) {
            resetWord()
            return
        }
        val orig = wordOriginal
        if (orig.isNullOrEmpty()) return
        val next = orig.dropLast(1) + newLastChar
        wordOriginal = next
        wordDisplayed = next
    }

    private fun resetWord() {
        wordOriginal = null
        wordDisplayed = null
        convertStage = 0
        refreshDynamicKey()
    }

    /**
     * 左列の動的キーの表示が変わるときだけレイアウトを組み直す。
     * 打鍵のたびに組み直すと、入力中のキーのタッチ追跡が切れてしまうため。
     */
    private fun refreshDynamicKey() {
        val show = canConvertToKatakana()
        if (show != katakanaKeyShown) updateLayout()
    }

    /** 空白キーの上フリック。直前の語を ひらがな → カタカナ → 半角カタカナ の順に巡回させる。 */
    private fun handleConvert(ic: InputConnection) {
        val original = wordOriginal
        val displayed = wordDisplayed
        if (original.isNullOrEmpty() || displayed.isNullOrEmpty()) return

        val katakana = KanaConverter.toKatakana(original)
        val stages = listOf(original, katakana, KanaConverter.toHalfWidthKatakana(katakana))
        val next = stages[(convertStage + 1) % stages.size]
        if (next == displayed) return

        if (replaceBeforeCursor(ic, displayed, next)) {
            convertStage = (convertStage + 1) % stages.size
            wordDisplayed = next
            lastCommitted = null
        }
    }

    // ------------------------------------------------------------------
    // かな漢字変換 (Mozc)
    // ------------------------------------------------------------------

    /** 設定キーではなく MODE キーの上フリック。かな漢字変換のオン/オフを切り替える。 */
    private fun toggleKanjiConversion() {
        finalizeComposition()
        kanjiConversionEnabled = !kanjiConversionEnabled
        prefs.kanjiConversionEnabled = kanjiConversionEnabled
    }

    /** かな1文字を Mozc セッションへ送る。セッションが無ければここで開始する。 */
    private fun sendMozcKana(text: String, alternates: List<FlickCandidate> = emptyList()) {
        var session = mozcSession
        if (session == null) {
            composingBase = minOf(selStart, selEnd)
            session = MozcSession()
            session.create(mobile = true)
            mozcSession = session
            updateLayout() // CURSORキーを「変換」キーに差し替える
        }
        composedChars += ComposedChar(text, alternates)
        applyMozcOutput(session.sendKanaCharacter(text, alternates))
    }

    /** 直前の1文字を [next] に差し替える（濁点付与・文字送り用）。Mozc にはバックスペース＋再送で伝える。 */
    private fun replaceLastMozcChar(next: String) {
        val session = mozcSession ?: return
        session.sendSpecialKey(MozcKeyEvent.SpecialKey.BACKSPACE)
        // 濁点付与や文字送りで意図的に変えた文字なので、押し間違い候補は引き継がない
        if (composedChars.isNotEmpty()) {
            composedChars[composedChars.lastIndex] = ComposedChar(next, emptyList())
        }
        applyMozcOutput(session.sendKanaCharacter(next))
    }

    /** 現在の未確定文字列/フォーカス中候補をそのまま確定してセッションを終える。 */
    private fun finalizeComposition() {
        val session = mozcSession ?: return
        applyMozcOutput(session.submit())
    }

    private fun selectCandidate(item: CandidateItem) {
        hideCandidatePanel()
        if (item.isLocal) {
            commitCorrection(item.text)
            return
        }
        val session = mozcSession ?: return
        applyMozcOutput(session.submitCandidate(item.id))
    }

    /** Mozc からの応答を InputConnection と候補ストリップへ反映する。 */
    private fun applyMozcOutput(output: MozcOutput) {
        val ic = currentInputConnection
        if (ic == null) {
            endComposition()
            return
        }

        if (output.hasResult()) {
            val resultText = output.result.value
            val base = if (composingBase >= 0) composingBase else minOf(selStart, selEnd)
            ic.beginBatchEdit()
            // 空文字でも commitText で composing 領域を明示的に消す（finishComposingText は
            // 表示中の composing テキストをそのまま確定してしまい、消せないため使わない）。
            ic.commitText(resultText, 1)
            ic.endBatchEdit()
            if (resultText.isNotEmpty()) history.recordInsert(resultText)
            expectedCursor = base + resultText.length
            selStart = expectedCursor
            selEnd = expectedCursor
            endComposition()
            return
        }

        if (!output.hasPreedit()) {
            // result も preedit も無い応答（例: これ以上変換候補が無い読みでの
            // 変換キー/スペース連打など）。Mozc 側で状態が変わっていないので何もしない。
            return
        }

        val preeditText = buildPreeditText(output)
        if (preeditText.isEmpty()) {
            clearComposition(ic)
            return
        }

        currentPreedit = preeditText
        ic.setComposingText(preeditText, 1)
        if (composingBase < 0) composingBase = minOf(selStart, selEnd)
        expectedCursor = composingBase + preeditText.length
        selStart = expectedCursor
        selEnd = expectedCursor
        updateCandidateStrip(output)
        scheduleCorrections()
    }

    /**
     * 未確定文字列を消してセッションを終える。
     *
     * `commitText("")` で composing 領域ごと消す。`finishComposingText` は表示中のテキストを
     * そのまま確定してしまい、消せないため使わない。
     */
    private fun clearComposition(ic: InputConnection) {
        ic.commitText("", 1)
        val base = if (composingBase >= 0) composingBase else minOf(selStart, selEnd)
        expectedCursor = base
        selStart = expectedCursor
        selEnd = expectedCursor
        endComposition()
    }

    private fun buildPreeditText(output: MozcOutput): String {
        if (!output.hasPreedit()) return ""
        return output.preedit.segmentList.joinToString("") { it.value }
    }

    /** [output] が null、または候補が無ければ候補欄を隠す。 */
    private fun updateCandidateStrip(output: MozcOutput?) {
        val window = output?.takeIf { it.hasCandidateWindow() }?.candidateWindow
        val fromMozc = window?.candidateList.orEmpty().map { CandidateItem(it.value, it.id) }
        showCandidates(userDictCandidates() + fromMozc)
    }

    /**
     * 現在の読みに前方一致するユーザー辞書の語。
     * Mozc の候補より前に出して、登録した語が埋もれないようにする。
     */
    private fun userDictCandidates(): List<CandidateItem> {
        if (composedChars.isEmpty()) return emptyList()
        val reading = composedChars.joinToString("") { it.text }
        return userDict.lookup(reading, MAX_USER_DICT_CANDIDATES)
            .map { CandidateItem(it, CandidateItem.LOCAL_ID) }
    }

    /** 候補リストを差し替えて、ストリップと展開グリッドの両方を描き直す。 */
    private fun showCandidates(items: List<CandidateItem>) {
        candidateItems = items
        val strip = candidateStrip ?: return
        strip.removeAllViews()
        if (items.isEmpty()) {
            candidateRow?.visibility = View.GONE
            hideCandidatePanel()
            return
        }

        val density = resources.displayMetrics.density
        for (item in items) {
            val tv = TextView(this).apply {
                text = item.text
                textSize = 16f
                setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                gravity = android.view.Gravity.CENTER
                val color = if (item.isLocal) R.color.ime_accent else R.color.ime_text
                setTextColor(ContextCompat.getColor(this@FlickImeService, color))
                setOnClickListener { selectCandidate(item) }
            }
            strip.addView(
                tv,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT),
            )
        }
        candidateRow?.visibility = View.VISIBLE
        candidateAdapter?.submit(items)
    }

    private fun toggleCandidatePanel() {
        if (candidatePanel?.visibility == View.VISIBLE) hideCandidatePanel() else showCandidatePanel()
    }

    private fun showCandidatePanel() {
        if (candidateItems.isEmpty()) return
        hideClipPanel()
        candidateAdapter?.submit(candidateItems)
        candidatePanel?.visibility = View.VISIBLE
        // GONE ではなく INVISIBLE。キーボードの高さを保ってパネルを重ねる。
        keyboardView?.visibility = View.INVISIBLE
    }

    private fun hideCandidatePanel() {
        if (candidatePanel?.visibility != View.VISIBLE) return
        candidatePanel?.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // 校正候補（フリックの押し間違いを想定した読みの変換）
    // ------------------------------------------------------------------

    /**
     * 打鍵ごとに呼ばれる。連続入力中に毎回問い合わせると重いので、少し待ってからまとめて実行する。
     */
    private fun scheduleCorrections() {
        correctionGeneration++
        mainHandler.removeCallbacks(correctionRunnable)
        mainHandler.postDelayed(correctionRunnable, CORRECTION_DELAY_MS)
    }

    private fun runCorrections() {
        val chars = composedChars.toList()
        if (chars.size < 2 || chars.none { it.alternates.isNotEmpty() }) return

        val readings = correctionReadings(chars)
        if (readings.isEmpty()) return

        val literalReading = chars.joinToString("") { it.text }
        val generation = correctionGeneration
        correctionExecutor.execute {
            val corrections = readings
                .mapNotNull { MozcSession.predict(it) }
                .filter { it.value != literalReading }
                // 少ない文節にまとまって変換できた読みほど、狙っていた語である可能性が高い
                .sortedBy { it.segmentCount }
                .map { it.value }
                .distinct()
                .take(MAX_CORRECTIONS_SHOWN)
            if (corrections.isEmpty()) return@execute
            mainHandler.post {
                if (generation == correctionGeneration) showCorrections(corrections)
            }
        }
    }

    /**
     * 1文字だけ「押し間違いだったかもしれない文字」に差し替えた読みを、確率の高い順に作る。
     *
     * 例: 「てすのした」の「の」(な の下フリック)を、隣の た の下フリック「と」に差し替えて
     * 「てすとした」を得る。これを Mozc に変換させると「テストした」が校正候補として出せる。
     */
    private fun correctionReadings(chars: List<ComposedChar>): List<List<String>> {
        val base = chars.map { it.text }
        return chars.indices
            .flatMap { index -> chars[index].alternates.map { index to it } }
            .sortedByDescending { (_, alternate) -> alternate.probability }
            .take(MAX_CORRECTION_READINGS)
            .map { (index, alternate) ->
                base.toMutableList().also { it[index] = alternate.text }
            }
    }

    /** 校正候補を、通常の変換候補の後ろにアクセント色で並べる。 */
    private fun showCorrections(corrections: List<String>) {
        if (mozcSession == null) return
        val shown = candidateItems.map { it.text }.toSet()
        val added = corrections
            .filter { it !in shown }
            .map { CandidateItem(it, CandidateItem.LOCAL_ID) }
        if (added.isEmpty()) return
        showCandidates(candidateItems + added)
    }

    /**
     * 校正候補を確定する。Mozc の候補 id ではなく別の読みから作った文字列なので、
     * セッションの候補選択ではなく未確定領域を直接置き換える。
     */
    private fun commitCorrection(text: String) {
        val ic = currentInputConnection ?: return
        val base = if (composingBase >= 0) composingBase else minOf(selStart, selEnd)
        ic.beginBatchEdit()
        ic.commitText(text, 1)
        ic.endBatchEdit()
        history.recordInsert(text)
        expectedCursor = base + text.length
        selStart = expectedCursor
        selEnd = expectedCursor
        endComposition()
    }

    private fun endComposition() {
        val wasComposing = mozcSession != null
        mozcSession?.destroy()
        mozcSession = null
        composingBase = -1
        currentPreedit = ""
        composedChars.clear()
        hideCandidatePanel()
        // 進行中の校正候補の問い合わせ結果を捨てる
        correctionGeneration++
        mainHandler.removeCallbacks(correctionRunnable)
        updateCandidateStrip(null)
        if (wasComposing) updateLayout() // 「変換」キーをCURSORキーに戻す
    }

    /** 物理キーボードの Ctrl+Z / Ctrl+Y / Ctrl+Shift+V を拾う。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) performRedo() else performUndo()
                    return true
                }

                KeyEvent.KEYCODE_Y -> {
                    performRedo()
                    return true
                }

                KeyEvent.KEYCODE_V -> if (event.isShiftPressed) {
                    showClipPanel()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------
    // クリップボード履歴
    // ------------------------------------------------------------------

    private fun toggleClipPanel() {
        if (clipPanel?.visibility == View.VISIBLE) hideClipPanel() else showClipPanel()
    }

    private fun showClipPanel() {
        finalizeComposition()
        refreshClipList()
        clipPanel?.visibility = View.VISIBLE
        // GONE ではなく INVISIBLE。キーボードの高さを保ってパネルを重ねる。
        keyboardView?.visibility = View.INVISIBLE
    }

    private fun hideClipPanel() {
        clipPanel?.visibility = View.GONE
        keyboardView?.visibility = View.VISIBLE
    }

    private fun refreshClipList() {
        val items = clipStore.all()
        clipAdapter?.submit(items)
        clipEmptyView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pasteClip(item: ClipItem) {
        val ic = currentInputConnection ?: return
        finalizeComposition()
        commit(ic, item.text)
        resetWord()
        hideClipPanel()
    }

    // ------------------------------------------------------------------
    // その他
    // ------------------------------------------------------------------

    private fun switchInputMethod() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToNextInputMethod(false)) return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}
