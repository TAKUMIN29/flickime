package com.example.flickime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flickime.clip.ClipboardStore

/** セットアップ手順と動作設定。IME のツールバーの「設定」からも開く。 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        findViewById<Button>(R.id.btn_pick).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
        }

        findViewById<CheckBox>(R.id.chk_clipboard).apply {
            isChecked = prefs.clipboardEnabled
            setOnCheckedChangeListener { _, checked -> prefs.clipboardEnabled = checked }
        }

        findViewById<CheckBox>(R.id.chk_haptic).apply {
            isChecked = prefs.hapticEnabled
            setOnCheckedChangeListener { _, checked -> prefs.hapticEnabled = checked }
        }

        findViewById<CheckBox>(R.id.chk_key_sound).apply {
            isChecked = prefs.keySoundEnabled
            setOnCheckedChangeListener { _, checked -> prefs.keySoundEnabled = checked }
        }

        val keyHeightLabel = findViewById<TextView>(R.id.label_key_height)
        findViewById<SeekBar>(R.id.seek_key_height).apply {
            // progress 0..36 を 40..76dp に対応させる
            progress = prefs.keyHeightDp - 40
            keyHeightLabel.text = getString(R.string.pref_key_height) + "：${prefs.keyHeightDp}dp"
            setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val dp = progress + 40
                    prefs.keyHeightDp = dp
                    keyHeightLabel.text = getString(R.string.pref_key_height) + "：${dp}dp"
                }
            })
        }

        val flickLabel = findViewById<TextView>(R.id.label_flick)
        findViewById<SeekBar>(R.id.seek_flick).apply {
            // progress 0..32 を 10..42dp に対応させる
            progress = prefs.flickThresholdDp - 10
            flickLabel.text = getString(R.string.pref_flick_sensitivity) + "：${prefs.flickThresholdDp}dp"
            setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val dp = progress + 10
                    prefs.flickThresholdDp = dp
                    flickLabel.text = getString(R.string.pref_flick_sensitivity) + "：${dp}dp"
                }
            })
        }

        findViewById<Button>(R.id.btn_user_dict).setOnClickListener {
            startActivity(Intent(this, com.example.flickime.dict.UserDictActivity::class.java))
        }

        findViewById<Button>(R.id.btn_clear_clips).setOnClickListener {
            ClipboardStore.get(this).clearAll()
            Toast.makeText(this, R.string.clip_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private abstract class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }
}
