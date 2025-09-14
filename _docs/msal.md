<!-- omit in toc -->
# MSAL導入

- [概要](#概要)
- [依存関係を追加](#依存関係を追加)
- [署名ハッシュの取得](#署名ハッシュの取得)
- [ローカル設定値の管理](#ローカル設定値の管理)
  - [local.properties の設定](#localproperties-の設定)
  - [設定値取得関数の実装](#設定値取得関数の実装)
- [MSAL設定ファイルの生成](#msal設定ファイルの生成)
  - [設定テンプレートファイルの作成](#設定テンプレートファイルの作成)
  - [ビルド時設定ファイル生成の実装](#ビルド時設定ファイル生成の実装)
  - [.gitignore の設定](#gitignore-の設定)
- [AndroidManifest.xml の設定](#androidmanifestxml-の設定)
  - [パーミッションの追加](#パーミッションの追加)
  - [BrowserTabActivity の追加](#browsertabactivity-の追加)
  - [ローカル設定値反映の実装](#ローカル設定値反映の実装)
- [プログラム実装](#プログラム実装)
- [注意事項](#注意事項)
  - [セキュリティ](#セキュリティ)
  - [デバッグとリリース](#デバッグとリリース)

## 概要

MSAL（Microsoft Authentication Library）for Androidを導入して、Microsoft アカウント認証機能をAndroidアプリに実装する手順を説明します。

**参考資料**
- [MSAL for Android 公式リポジトリ](https://github.com/AzureAD/microsoft-authentication-library-for-android)


## 依存関係を追加

`app/build.gradle.kts` に MSAL の依存関係を追加します。

```kotlin:app/build.gradle.kts
dependencies {
    implementation("com.microsoft.identity.client:msal:x.x.x") // 最新バージョンを確認してください
}
```

`settings.gradle.kts` に Microsoft のリポジトリを追加します。

```kotlin:settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        // ...
        maven("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
    }
}
```

## 署名ハッシュの取得

MSALの設定では、APK署名に使用するキーストアの署名ハッシュが必要です。デバッグビルドとリリースビルドで異なるキーストアを使用する場合は、それぞれの署名ハッシュを取得してください。

以下のコマンドでキーストアから署名ハッシュを取得します。

```bash
keytool -exportcert -alias [署名エイリアス] -keystore [キーストアファイルのパス] | openssl sha1 -binary | openssl base64
```

**例（デバッグキーストアの場合）:**
```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64
```

上記で取得した署名ハッシュをURLエンコードします。

```bash
# Node.jsを使用した例（他の方法もあります）
# 署名ハッシュが「WWKsWNmxHAoZi6dIqRlv0EEY+8s=」の場合
node -p 'encodeURIComponent("WWKsWNmxHAoZi6dIqRlv0EEY+8s=")'
```

**出力例:** `WWKsWNmxHAoZi6dIqRlv0EEY%2B8s%3D`

## ローカル設定値の管理

機密情報（クライアントID、テナントIDなど）はGitリポジトリに含めないよう、ローカル設定として管理します。

### local.properties の設定

プロジェクトルートの `local.properties` ファイルに以下の設定を追加します。

```properties:local.properties
# MSAL設定（Git管理対象外）
msal.tenant.id=[EntraIDアプリケーションのテナントID]
msal.client.id=[EntraIDアプリケーションのクライアントID]
msal.redirect.uri=msauth://[アプリケーションID]/[署名ハッシュ（URLエンコード形式）]
msal.redirect.path=/[署名ハッシュ]
```

**設定例:**
```properties:local.properties
msal.tenant.id=12345678-1234-1234-1234-123456789012
msal.client.id=87654321-4321-4321-4321-210987654321
msal.redirect.uri=msauth://com.example.myapp/WWKsWNmxHAoZi6dIqRlv0EEY%2B8s%3D
msal.redirect.path=/WWKsWNmxHAoZi6dIqRlv0EEY+8s=
```

### 設定値取得関数の実装

`app/build.gradle.kts` に以下のコードを追加します。

```kotlin:app/build.gradle.kts
/**
 * local.propertiesファイルを読み込む
 */
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

/**
 * ローカル設定値を取得（優先順位は下記）
 * - 環境変数
 * - local.propertiesファイル
 * - Gradleプロジェクトプロパティ
 * - defaultValue
 */
fun getConfigValue(key: String, defaultValue: String = ""): String {
    System.getenv(key.uppercase().replace(".", "_"))?.let { return it }
    localProperties.getProperty(key)?.let { return it }
    project.findProperty(key)?.toString()?.let { return it }
    return defaultValue
}
```

## MSAL設定ファイルの生成

ビルド時にテンプレートファイル`msal_config_template.json`からローカル設定値を使って`msal_config.json`を生成します。

### 設定テンプレートファイルの作成

`app/src/main/res/raw/msal_config_template.json` を作成します。

```json:app/src/main/res/raw/msal_config_template.json
{
  "client_id" : "{{CLIENT_ID}}",
  "authorization_user_agent" : "DEFAULT",
  "redirect_uri" : "{{REDIRECT_URI}}",
  "authorities" : [
    {
      "type": "AAD",
      "audience": {
        "type": "AzureADMyOrg",
        "tenant_id": "{{TENANT_ID}}"
      }
    }
  ],
  "account_mode": "SINGLE",
  "broker_redirect_uri_registered": true
}
```

### ビルド時設定ファイル生成の実装

`app/build.gradle.kts` の `android` ブロック内に以下を追加します。

```kotlin:app/build.gradle.kts
android {
    project.afterEvaluate {
        /**
         * ビルド時にmsal_config.jsonを生成
         */
        val generateMsalConfig by tasks.registering {
            doLast {
                val templateFile = File("$projectDir/src/main/res/raw/msal_config_template.json")
                val outputFile = File("$projectDir/src/main/res/raw/msal_config.json")

                val clientId = getConfigValue("msal.client.id")
                val redirectUri = getConfigValue("msal.redirect.uri")
                val tenantId = getConfigValue("msal.tenant.id")

                if (templateFile.exists()) {
                    val content = templateFile.readText()
                        .replace("{{CLIENT_ID}}", clientId)
                        .replace("{{REDIRECT_URI}}", redirectUri)
                        .replace("{{TENANT_ID}}", tenantId)

                    outputFile.writeText(content)
                    println("✅ Generated msal_config.json from template")
                } else {
                    throw GradleException("❌ Template file missing: msal_config_template.json")
                }
            }
        }

        tasks.matching { it.name.startsWith("preBuild") }.configureEach {
            dependsOn(generateMsalConfig)
        }
    }

    // ...
}
```

### .gitignore の設定

`app/.gitignore` を編集して、生成される設定ファイル `msal_config.json` をGit管理対象外にします。

```gitignore:app/.gitignore
src/main/res/raw/msal_config.json
```

## AndroidManifest.xml の設定

### パーミッションの追加

`app/src/main/AndroidManifest.xml` にネットワークアクセス用のパーミッションを追加します。

```xml:app/src/main/AndroidManifest.xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### BrowserTabActivity の追加

`application` タグ内にMSAL用のActivityを追加します。

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
            android:host="[アプリケーションID]"
            android:path="${msAuthPath}" />
    </intent-filter>
</activity>
```

### ローカル設定値反映の実装

`app/build.gradle.kts` の `android.defaultConfig` ブロック内に以下を追加します。

```kotlin:app/build.gradle.kts
android {
    defaultConfig {
        manifestPlaceholders["msAuthPath"] = getConfigValue("msal.redirect.path")
    }
}
```

## プログラム実装

実装例は `auth/internal/MsAuthenticator.kt` をご参照ください。

## 注意事項

### セキュリティ

- **機密情報の管理**: 機密情報は `local.properties` で管理し、Git にコミットしないでください
- **署名の一致**: EntraIDアプリケーションに登録した署名ハッシュと、APK署名に使用するキーストアが一致している必要があります
- **リダイレクトURI**: EntraIDアプリケーション設定のリダイレクトURIと完全に一致させてください

### デバッグとリリース

- デバッグビルドとリリースビルドで異なるキーストアを使用する場合、それぞれの署名ハッシュをEntraIDアプリケーションに登録する必要があります
