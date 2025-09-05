<!-- omit in toc -->
# Intune Test App

- [インターネットアクセス許可](#インターネットアクセス許可)
- [MSAL導入](#msal導入)
  - [依存関係を追加](#依存関係を追加)
  - [署名ハッシュ取得](#署名ハッシュ取得)
  - [MSAL設定ファイル](#msal設定ファイル)
  - [BrowserTabActivityを追加](#browsertabactivityを追加)
  - [実装](#実装)
- [Intune App SDK導入](#intune-app-sdk導入)
  - [SDKファイル取得](#sdkファイル取得)
  - [依存関係を追加](#依存関係を追加-1)
  - [ビルドプラグインを追加](#ビルドプラグインを追加)
  - [MAMApplication設定](#mamapplication設定)
- [MAM統合](#mam統合)
  - [実装](#実装-1)
  - [Entra ID APIアクセス許可設定](#entra-id-apiアクセス許可設定)

## インターネットアクセス許可

AndroidManifest.xmlに下記を追加

```xml:app/src/main/AndroidManifest.xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## MSAL導入

[参考URL](https://github.com/AzureAD/microsoft-authentication-library-for-android)

### 依存関係を追加

依存関係 `com.microsoft.identity.client:msal` を追加 （ `app/build.gradle.kts` を編集）

```kotlin:app/build.gradle.kts
implementation("com.microsoft.identity.client:msal:7.0.3")
```

リポジトリ `https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1` を追加（ `settings.gradle.kts` を編集）

```kotlin:settings.gradle.kts
maven("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
```

### 署名ハッシュ取得

APK署名に使うキーストアから、署名ハッシュを下記コマンドで取得します。

```sh
keytool -exportcert -alias （署名エイリアス） -keystore （キーストアファイルのパス） | openssl sha1 -binary | openssl base64
```

さらに署名ハッシュをURLエンコードした文字列を、下記コマンドで取得します。

```sh
# 例：署名ハッシュが「WWKsWNmxHAoZi6dIqRlv0EEY+8s=」の場合
node -p 'encodeURIComponent("WWKsWNmxHAoZi6dIqRlv0EEY+8s=")'
```

### MSAL設定ファイル

msal_config.jsonを追加

```json:app/src/main/res/raw/msal_config.json
{
  "client_id" : "87aed927-e42a-4130-ac22-88837c37c6fb",
  "authorization_user_agent" : "DEFAULT",
  "redirect_uri" : "msauth://com.yaso202508appproxy.intunetestapp/IXVrbW6heb0JrM%2FscrADiKf7Jmk%3D",
  "authorities" : [
    {
      "type": "AAD",
      "audience": {
        "type": "AzureADMyOrg",
        "tenant_id": "f95c2a9b-deec-48a4-849c-d7c81a9c221d"
      }
    }
  ],
  "account_mode": "SINGLE",
  "broker_redirect_uri_registered": true
}
```

### BrowserTabActivityを追加

AndroidManifest.xmlを編集

```xml:app/src/main/AndroidManifest.xml
<activity
    android:name="com.microsoft.identity.client.BrowserTabActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="msauth"
            android:host="com.yaso202508appproxy.intunetestapp"
            android:path="/IXVrbW6heb0JrM/scrADiKf7Jmk=" />
    </intent-filter>
</activity>
```

### 実装

MainActivityに下記を実装

- initMsal()
- sso()
- signIn()
- acquireTokenSilent()

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

AppProxyAuthManager.ktに、Intune+AppProxy認証機能を実装

### Entra ID APIアクセス許可設定

Microsoft Mobile Application Management を追加して管理者合意
