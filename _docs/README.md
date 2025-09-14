# 説明資料

## Azure側の設定

- [AppProxy条件付きアクセス許可設定](./proxy_ca_grant.md)
- [Intuneアプリ保護ポリシーの適用](./intune-app-protection-policy.md)
- [EntraIDアプリケーション設定](./entra-id-app.md)

## 実装手順

- [MSAL導入](./msal.md)

## AppProxy認証の流れ

（本アプリを使いながら説明）

Authorizationヘッダーは他の用途に使えない

様々なリクエストにしらみ潰しで対応が必要

- JavaScriptのリクエスト
  - xhr
  - fetch
- （サブ）リソースリクエスト
- フォームのPOST（未対応）
- リダイレクト（未対応）
- ダウンロード
- etc

トークン付与せずページ遷移したらMSサインインページにリダイレクトされる
