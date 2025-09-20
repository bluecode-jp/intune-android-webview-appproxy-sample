<!-- omit in toc -->
# Intune Test App

- [概要](#概要)
- [認証フローの概要](#認証フローの概要)
- [技術的制約と注意事項](#技術的制約と注意事項)
  - [Authorizationヘッダーの専用利用](#authorizationヘッダーの専用利用)
  - [複数の捕捉方法の組み合わせが必須](#複数の捕捉方法の組み合わせが必須)
  - [包括的なテストが必須](#包括的なテストが必須)
  - [パフォーマンス対策としてのON/OFF機能](#パフォーマンス対策としてのonoff機能)
- [サンプルアプリのセットアップ手順](#サンプルアプリのセットアップ手順)

## 概要

AppProxyの条件付きアクセスをクリアしてWebサイトにアクセスできるAndroid WebViewアプリのサンプルです。

技術詳細は[こちら](./_docs/)

## 認証フローの概要

**Microsoftサインイン**
- EntraIDアカウントでの認証を実行します

**Intuneアプリ保護ポリシーの適用**
- 条件付きアクセスをクリアするため、Intuneアプリ保護ポリシーを適用します

**App Proxyアクセストークンの取得**
- Webサイトへアクセス可能となるトークンを取得します

**WebViewでのセキュアアクセス**
- すべてのHTTPリクエストを捕捉し、Authorizationヘッダーに取得したApp Proxyアクセストークンを自動付与して送信します

## 技術的制約と注意事項

WebViewからのすべてのHTTPリクエストを捕捉してAuthorizationヘッダーにApp Proxyアクセストークンを付与する仕組み上、以下の制約と課題があります。

### Authorizationヘッダーの専用利用

すべてのHTTPリクエストのAuthorizationヘッダーをApp Proxyトークン用に使用するため、他の用途でAuthorizationヘッダーを利用できません。

### 複数の捕捉方法の組み合わせが必須

Webサイトが発行するすべてのHTTPリクエストに対応する必要がありますが、単一の方法ですべてを網羅することはできません。リクエスト種類別に異なるアプローチを組み合わせる必要があります。

| リクエスト種類 | WebView API | JavaScript API | 備考 |
|---------------|-------------|----------------|------|
| ページロード時 | ✅ ShouldInterceptRequest | ❌ 対応不可 | |
| フォーム送信 | ❌ 対応不可 | ✅ submitイベント捕捉 | 特殊ケースへの個別対応が必要 |
| 非同期通信（Ajax） | ❌ ボディ付きは対応不可 | ✅ XMLHttpRequest/Fetch API改造 | |

**注意**: 上記は代表的なパターンの例であり、Webサイトによっては他にも様々なHTTPリクエストパターンが存在する可能性があります。すべてのパターンを事前に把握することは困難なため、実際の開発では個別対応が必要になる場合があります。

### 包括的なテストが必須

Webサイトで発生するすべてのHTTPリクエストパターンを網羅できる程度のテストを、Androidアプリ上で実施する必要があります。

Webサイトの全機能をテストする必要はありませんが、HTTPリクエストの種類や発生パターンが十分網羅される範囲でテストを行う必要があります。すべてのHTTPリクエストが適切に捕捉され、App Proxyアクセストークンが付与されていることを確認する必要があります。

### パフォーマンス対策としてのON/OFF機能

App Proxy経由でないアクセス時（例：社内ネットワーク経由）に、アクセストークンが空の状態で都度トークン取得を試行するとパフォーマンスが悪化します。App Proxy認証機能のON/OFF切り替え機能の実装を推奨します。

## サンプルアプリのセットアップ手順

Git Cloneしたのち、Android Studioでプロジェクトを開きます。

プロジェクトルートの `local.properties` ファイルに以下のローカル設定値を追加します：

```properties:local.properties
# Androidアプリのパッケージ名（アプリストアでの識別子）
application.id=com.example.intunetestapp
# Androidアプリのバージョンコード
version.code=9
# Androidアプリのバージョン名
version.name=0.0.9

# EntraIDアプリケーションのテナントID
msal.tenant.id=12345678-1234-1234-1234-123456789abc
# EntraIDアプリケーションのクライアントID
msal.client.id=87654321-4321-4321-4321-abcdef123456
# MSALのリダイレクトURL
msal.redirect.uri=msauth://com.example.intunetestapp/AbCdEfGhIjKlMnOpQrStUvWxYz0%3D
# MSALのリダイレクトパス
msal.redirect.path=/AbCdEfGhIjKlMnOpQrStUvWxYz0=

# App Proxy WebサイトURL
proxy.url=https://your-appproxy-website.biz
# App Proxy WebサイトのORIGIN
proxy.origin=https://your-appproxy-website.biz
# App Proxy Webサイトのスコープ
proxy.scope=https://your-appproxy-website.biz/user_impersonation

# デバッグ用キーストアファイルのパス
debug.store.file=/path/to/your/keystore.jks
# キーストアのパスワード
debug.store.password=your-keystore-password
# キーのエイリアス
debug.key.alias=your-key-alias
# キーのパスワード
debug.key.password=your-key-password
```

追加完了後、アプリをビルドします。

**注意事項**: Android Studioで初回プロジェクトを開いた際、一部ファイルが未生成のため警告が表示されますが、ビルドを実行すると必要なファイルが自動生成され、警告も解消されてビルドが成功します。
