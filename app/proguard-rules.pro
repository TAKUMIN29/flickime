# IME サービスと設定画面はマニフェストから参照されるため保持
-keep class com.example.flickime.FlickImeService { *; }
-keep class com.example.flickime.SettingsActivity { *; }
-keep class com.example.flickime.keyboard.FlickKeyboardView { *; }
-keep class com.example.flickime.keyboard.FlickGuideView { *; }
