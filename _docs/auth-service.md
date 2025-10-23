<!-- omit in toc -->
# 統合認証サービスの実装

- [概要](#概要)
- [設計方針](#設計方針)
- [主な機能](#主な機能)
  - [初期化機能](#初期化機能)
  - [アカウント管理機能](#アカウント管理機能)
  - [App Proxy準備機能](#app-proxy準備機能)
  - [トークン取得機能](#トークン取得機能)
  - [ユーティリティ機能](#ユーティリティ機能)
- [注意事項](#注意事項)

## 概要

認証機能を統合したサービスクラス`AuthService`を`auth/AuthService.kt`に実装しています。

このクラスは、`auth.internal`パッケージにある各クラス（MsAuthenticator、AuthCacheManager、IntuneAppProtection）を統合し、他のコンポーネントが`auth.internal`のクラスを直接使用せずに済むよう設計しています。

## 設計方針

**統一アクセスポイント**
- 認証関連の機能は`AuthService`を通じてのみアクセス
- `auth.internal`パッケージのクラスを他のコードから直接使用することを禁止

**シンプルなインターフェース**
- 複雑な認証フローを簡潔なメソッドで提供
- 内部実装の詳細を隠蔽し、使いやすいAPIを提供

## 主な機能

### 初期化機能

**`initialize(context: Context)`**
- Microsoft認証とIntune保護機能の初期化を実行
- `applicationContext`を渡すことでアプリケーション全体での認証機能を有効化
- 初期化成功時は`true`、失敗時は`false`を返却

### アカウント管理機能

**`setAccount(signInScopes: List<String>, activity: Activity)`**
- Microsoftアカウントでのサインインを実行
- Intuneアプリ保護ポリシーのMAM（Mobile Application Management）登録を自動実行
- `signInScopes`には`User.Read`など認証前でもアクセス可能なスコープを指定（App Proxyスコープは不可）
- サインイン成功時は`IAccount`を返却、失敗時は`null`を返却

**`clearAccount()`**
- サインアウト処理を実行
- キャッシュクリアとMAM登録解除を自動実行
- 処理成功時は`true`、失敗時は`false`を返却

**`getAccount()`**
- 現在サインインしているアカウント情報を取得

### App Proxy準備機能

**`checkPermission(appProxyScopes, timeOutMillis, intervalMillis)`**
- App Proxyへのアクセス権限を確認する機能
- MAM登録の完了とApp Proxyトークンの取得完了を確認
- `appProxyScopes`にはApp Proxyスコープを指定
- タイムアウト設定により無限待機を防止
- 定期的なポーリングでステータスを確認

### トークン取得機能

**`acquireAuth(scopes: List<String>)`**
- 指定されたスコープのアクセストークンを含む認証情報を取得
- キャッシュ機能により高速レスポンスを実現
- `IAuthenticationResult`形式で詳細な認証情報を返却

**`acquireToken(scopes: List<String>)`**
- 指定されたスコープのアクセストークン文字列のみを取得
- `acquireAuth()`のラッパー関数として実装
- シンプルにトークン文字列だけが必要な場合に使用

### ユーティリティ機能

**`close()`**
- 認証関連リソースの解放
- アプリケーション終了時に必須の処理

## 注意事項

**初期化の順序**
- `initialize()`は他の認証メソッドを呼び出す前に必ず実行してください
- 初期化失敗時は他の認証機能を使用できません

**スコープの使い分け**
- サインイン時（`setAccount()`）にはApp Proxyスコープを使用できません
- App Proxyスコープは、MAM登録完了後に`waitForAppProxyAccessReady()`および`acquireAuth()`、`acquireToken()`で使用します

**スレッドに関する注意**
- `acquireAuth()`と`acquireToken()`は`@WorkerThread`で実行する必要があります
- UIスレッドから直接呼び出すとANR（Application Not Responding）の原因となります

**リソース管理**
- アプリケーション終了時に必ず`close()`を呼び出してリソースを解放してください
- メモリリークの防止に重要です
