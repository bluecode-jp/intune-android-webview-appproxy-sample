<!-- omit in toc -->
# Intune App SDK導入

- [概要](#概要)
- [SDKファイルの取得と配置](#sdkファイルの取得と配置)
- [依存関係の追加](#依存関係の追加)
- [ビルドプラグインの設定](#ビルドプラグインの設定)
- [MAMApplicationの設定](#mamapplicationの設定)
- [プログラム実装](#プログラム実装)
- [Intuneアプリ保護ポリシー実装](#intuneアプリ保護ポリシー実装)
  - [実装の特徴](#実装の特徴)
  - [主な機能](#主な機能)
  - [認証フローでの役割](#認証フローでの役割)
  - [エラーハンドリング](#エラーハンドリング)
  - [使用上の注意](#使用上の注意)


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

## Intuneアプリ保護ポリシー実装

Intuneアプリ保護ポリシー機能を実装します。

本サンプルアプリでは、`IntuneAppProtection`クラスを`auth/internal/IntuneAppProtection.kt`に実装しています。以下、`IntuneAppProtection`クラスの説明です。

### 実装の特徴

**MAM（Mobile Application Management）の統合**
- Microsoft Intune SDKの`MAMEnrollmentManager`を使用してアプリ保護ポリシーを適用します

**認証コールバックの実装**
- `MAMServiceAuthenticationCallback`を実装し、MAMがアクセストークンを必要とする際に自動的に提供します
- `AuthCacheManager`と連携してトークン取得を効率化しています

**アカウント連携**
- `MsAuthenticator`で認証されたアカウント情報を使用してMAM登録を実行します
- Microsoft認証とIntune保護機能をシームレスに統合しています

### 主な機能

**`initialize()`**
- MAMEnrollmentManagerを初期化し、認証コールバックを登録
- 初期化成功時は`true`、失敗時は`false`を返却

**`registerMam()`**
- 現在認証されているアカウントをMAMに登録
- アカウント情報（ユーザー名、ID、テナントID、権限）をMAMEnrollmentManagerに設定
- 登録完了後、Intuneアプリ保護ポリシーが適用されるようになります

**`unregisterMam(account: IAccount)`**
- 指定されたアカウントのMAM登録を解除
- サインアウト時やアカウント切り替え時に使用

**`getMamStatus()`**
- 現在のMAM登録状態を取得
- `MAMEnrollmentManager.Result`型で状態を返却（例：`ENROLLMENT_SUCCEEDED`など）
- App Proxyアクセスの可否判定に使用

### 認証フローでの役割

**App Proxyアクセストークン取得の前提条件**
- App Proxyのアクセストークンを取得するには、事前にMAM登録が完了している必要があります
- `registerMam()`完了後、`AuthCacheManager`がApp Proxyスコープのトークンを取得できるようになります

**自動トークン提供**
- MAMが内部的にアクセストークンを必要とする際、`acquireToken`コールバックが自動実行されます
- コールバック内で適切なリソースID（スコープ）に基づいてトークンを取得し、MAMに提供します

### エラーハンドリング

MAMEnrollmentManagerの初期化失敗やアカウント情報の取得失敗など、各段階でのエラーを適切にキャッチし、ログ出力後に適切な戻り値（`false`または`null`）を返却する設計にしています。

### 使用上の注意

- MAM登録は`MsAuthenticator.signIn()`完了後に実行する必要があります
- MAM登録状態が`ENROLLMENT_SUCCEEDED`になるまで、App Proxyスコープのトークン取得はできません
- アカウント切り替えやサインアウト時は、必ず`unregisterMam()`を実行してリソースを適切に解放してください
