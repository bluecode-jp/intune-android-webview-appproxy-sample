<!-- omit in toc -->
# Intune Test App

- [手順](#手順)
- [Intune App SDK導入](#intune-app-sdk導入)
  - [SDKファイル取得](#sdkファイル取得)
  - [依存関係を追加](#依存関係を追加)
  - [ビルドプラグインを追加](#ビルドプラグインを追加)
  - [MAMApplication設定](#mamapplication設定)
- [MAM統合](#mam統合)
  - [実装](#実装)

## 手順

Cloneしたのち、local.propertiessに環境変数を設定してビルドする。

> ビルドして初めて、一部警告がなくなる。

## Intune App SDK導入

[参考URL](https://learn.microsoft.com/ja-jp/intune/intune-service/developer/app-sdk-android-phase3)

### SDKファイル取得

SDKファイルをダウンロードして app/libsに配置

- Microsoft.Intune.MAM.SDK.aar
- com.microsoft.intune.mam.build.jar

### 依存関係を追加

依存関係 `Microsoft.Intune.MAM.SDK.aar` を追加 （ `app/build.gradle.kts` を編集）

```kotlin:app/build.gradle.kts
implementation(files("libs/Microsoft.Intune.MAM.SDK.aar"))
```

### ビルドプラグインを追加

`build.gradle.kts` を編集

```kotlin:build.gradle.kts
buildscript {
    dependencies {
        classpath("org.javassist:javassist:3.29.2-GA")
        classpath(files("app/libs/com.microsoft.intune.mam.build.jar"))
    }
}
```

`app/build.gradle.kts` を編集

```kotlin:app/build.gradle.kts
plugins {
    id("com.microsoft.intune.mam")
}
```

### MAMApplication設定

AndroidManifestを編集して、applicationに下記属性を追加

```xml
android:name="com.microsoft.intune.mam.client.app.MAMApplication"
```

## MAM統合

[参考URL](https://learn.microsoft.com/ja-jp/intune/intune-service/developer/app-sdk-android-phase4)

### 実装

com.yaso202508appproxy.intunetestapp.authパッケージに実装
