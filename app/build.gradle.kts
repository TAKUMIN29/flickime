plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.flickime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.flickime"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Mozc の libmozc.so は ABI ごとに約13〜16MBあるため、全ABI同梱の
    // ユニバーサルAPKだけでなく ABI 別の軽量APKも生成する（サイドロード用）。
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // Play Store 経由（App Bundle）で配布する場合も、端末のABIに合ったものだけが
    // 配信されるようにしておく（AGPの既定値だが明示しておく）。
    bundle {
        abi.enableSplit = true
        density.enableSplit = true
        language.enableSplit = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Mozc かな漢字変換エンジン用。各 .proto から生成した Java lite クラスを含む
    // (org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands ほか)。
    implementation(files(
        "libs/mozc-commands-proto-lite.jar",
        "libs/mozc-candidate-window-proto-lite.jar",
        "libs/mozc-config-proto-lite.jar",
        "libs/mozc-engine-builder-proto-lite.jar",
        "libs/mozc-user-dictionary-storage-proto-lite.jar",
    ))
    implementation("com.google.protobuf:protobuf-javalite:4.34.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
