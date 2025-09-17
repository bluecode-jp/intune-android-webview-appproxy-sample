<!-- omit in toc -->
# Intune App SDK導入

- [概要](#概要)
- [SDKファイルの取得と配置](#sdkファイルの取得と配置)
- [依存関係の追加](#依存関係の追加)
- [ビルドプラグインの設定](#ビルドプラグインの設定)
- [MAMApplicationの設定](#mamapplicationの設定)
- [プログラム実装](#プログラム実装)


## 概要

AndroidアプリケーションにIntune App SDKを導入し、Intuneのアプリ保護ポリシーを適用する機能を実装する手順を説明します。

**参考資料**

- [Microsoft Learn - Intune App SDK for Android (Phase 3)](https://learn.microsoft.com/ja-jp/intune/intune-service/developer/app-sdk-android-phase3)
- [Microsoft Learn - Intune App SDK for Android (Phase 4)](https://learn.microsoft.com/ja-jp/intune/intune-service/developer/app-sdk-android-phase4)
- [Intune App SDK for Android GitHub リポジトリ](https://github.com/microsoftconnect/ms-intune-app-sdk-android)

## SDKファイルの取得と配置

[Intune App SDK for Android GitHub リポジトリ](https://github.com/microsoftconnect/ms-intune-app-sdk-android)から以下のファイルをダウンロードします：

- `Microsoft.Intune.MAM.SDK.aar`
- `com.microsoft.intune.mam.build.jar`

ダウンロードしたファイルを以下のディレクトリに配置します：

```
プロジェクトルート/
├── app/
│   └── libs/
│       ├── Microsoft.Intune.MAM.SDK.aar
│       └── com.microsoft.intune.mam.build.jar
└── ...
```

**注意：** `app/libs` ディレクトリが存在しない場合は、新規作成してください。

## 依存関係の追加

`app/build.gradle.kts` ファイルを開き、dependencies セクションに以下を追加します：

```kotlin:app/build.gradle.kts
dependencies {
    implementation(files("libs/Microsoft.Intune.MAM.SDK.aar"))
}
```

## ビルドプラグインの設定

プロジェクトルートの `build.gradle.kts` ファイルを開き、buildscript セクションに以下を追加します：

```kotlin:build.gradle.kts
buildscript {
    dependencies {
        classpath("org.javassist:javassist:3.29.2-GA")
        classpath(files("app/libs/com.microsoft.intune.mam.build.jar"))
    }
}
```

`app/build.gradle.kts` ファイルのpluginsセクションに以下を追加します：

```kotlin:app/build.gradle.kts
plugins {
    id("com.microsoft.intune.mam")
}
```

## MAMApplicationの設定

アプリケーションクラスの設定方法は、既存の実装によって異なります。

**既存のApplicationクラスがある場合**

既に `android.app.Application` のサブクラスを作成している場合は、Intune MAM ビルドプラグインが自動的にアプリケーションクラスを変換するため、追加の設定は不要です。

**Applicationクラスを作成していない場合**

`AndroidManifest.xml` ファイルを開き、`<application>` タグに以下の属性を追加します：

```xml
<application
    ...
    android:name="com.microsoft.intune.mam.client.app.MAMApplication"
    >
    ...
</application>
```

## プログラム実装

実装例は `auth/internal/IntuneAppProtection.kt` をご参照ください。
