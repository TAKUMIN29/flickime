# フリックIME (FlickIME)

Android 用の日本語フリック入力キーボード。Windows の `Win+V`（クリップボード履歴）と
`Ctrl+Z` / `Ctrl+Y`（元に戻す・やり直し）に相当する機能をキーボード上に載せている。

## 機能

| 機能 | 実現方法 |
| --- | --- |
| フリック入力 | ケータイ配列 4×5。指を置いたキーを固定し、移動方向で文字を決定。連打で文字送りも可能 |
| かな / 英字 / 数字 / 記号 | 左上の `あ/A/1` キーと `記号` キーで切り替え |
| 濁点・半濁点・小文字 | `小゛゜` キー。タップで巡回（は→ば→ぱ）、左フリックで゛、右フリックで゜ |
| クリップボード履歴 | ツールバーの「履歴」。コピーを検知して端末内に保存し、タップで貼り付け。★で固定 |
| 元に戻す / やり直し | ツールバーのボタン、および物理キーボードの `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` |
| 履歴パネルのショートカット | 物理キーボードの `Ctrl+Shift+V` |

## ビルド

必要なもの:

- JDK 17
- Android SDK (compileSdk 35 / build-tools)
- Gradle 8.9 以上（または Android Studio）

### Android Studio を使う場合

`C:\claude\android` を開けば、Gradle Wrapper と SDK は自動で用意される。

### コマンドラインの場合

このリポジトリには Gradle Wrapper の jar（バイナリ）を含めていないので、
最初に一度だけ wrapper を生成する。

```powershell
gradle wrapper --gradle-version 8.9
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug   # 端末を USB 接続 or エミュレータ起動中
```

SDK の場所は `local.properties` に書く（Android Studio なら自動生成される）。

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

## 使い方

1. アプリを起動し、「入力方法の設定を開く」から **フリックIME** を有効化
2. 「キーボードを選択」で フリックIME に切り替え
3. 画面下部のテスト欄で入力を確認

## 設計メモ・制限事項

Android は Windows と違い、**OS 全体で効くクリップボード履歴や undo は提供していない**。
そのため以下の方針で実装している。

- **クリップボード履歴**: Android 10 (API 29) 以降、バックグラウンドのアプリは
  クリップボードを読めない。ただし**既定の IME は例外**なので、IME として実装することで
  どのアプリでコピーしても履歴を蓄積できる。フリックIME が選択されていない間は記録されない。
  履歴は端末内（アプリ専用ディレクトリの `clips.json`）にのみ保存し、外部には送信しない。

- **元に戻す / やり直し**: OS 横断の undo は存在しないため、
  「この IME が `InputConnection` 経由で行った編集」だけを `EditHistory` に積んで打ち消す。
  他アプリ側の操作でテキストが変わっていた場合は、undo 実行時の照合で検知して履歴を破棄する
  （誤った文字を消さないため）。アプリ自身の undo（Google ドキュメント等）とは独立。

- **かな漢字変換は未実装**。直接入力方式。変換を入れる場合は Mozc などの
  変換エンジンを組み込み、`setComposingText` による未確定文字列の管理を追加する必要がある。

## 主なファイル

```
app/src/main/java/com/example/flickime/
├── FlickImeService.kt          IME 本体。キー処理・履歴・undo の統合
├── SettingsActivity.kt         セットアップ手順と動作設定
├── Prefs.kt                    設定値
├── keyboard/
│   ├── KeySpec.kt              キー1つ分の定義とフリック方向
│   ├── KeyLayouts.kt           かな/英字/数字/記号の配列表
│   ├── KanaModifier.kt         濁点・半濁点・小文字の変換表
│   ├── FlickKeyboardView.kt    キーボードの描画とフリック判定
│   └── FlickGuideView.kt       フリック中の候補吹き出し
├── edit/EditHistory.kt         元に戻す / やり直しのスタック
└── clip/
    ├── ClipboardStore.kt       履歴の保存（JSON）
    └── ClipAdapter.kt          履歴一覧
```
