<!-- omit in toc -->
# アクセストークン取得機能の実装

- [概要](#概要)
- [要件](#要件)
- [実装方針](#実装方針)
- [実装のポイント](#実装のポイント)
  - [スコープベースのキャッシュ管理](#スコープベースのキャッシュ管理)
  - [期限管理による効率的な更新](#期限管理による効率的な更新)
  - [同期関数としての高速レスポンス](#同期関数としての高速レスポンス)
  - [重複実行の防止](#重複実行の防止)
  - [リソース管理とライフサイクル対応](#リソース管理とライフサイクル対応)
- [パフォーマンスの特徴](#パフォーマンスの特徴)

## 概要

高性能なアクセストークン取得機能を実装します。この機能は以下の用途で利用されます：

- Intuneアプリ保護ポリシーの適用機能
- HTTPリクエストを捕捉してAuthorizationヘッダーにAppProxyアクセストークンを付与する機能

## 要件

アクセストークン取得機能には以下の要件があります：

**同期関数**
- アクセストークン（を含むデータ）を戻り値とする同期関数であること
- コールバック関数やsuspend関数ではないこと

**ハイパフォーマンス**
- 頻繁に呼び出されるため、ボトルネックにならない性能が必要
- レスポンス時間の最小化が重要

## 実装方針

上記の要件を満たすため、**キャッシュ機能付きの同期関数**を実装しました。

- **実装ファイル**: `auth/internal/AuthCacheManager.kt`
- **メイン関数**: `acquireAuth()`
- **戻り値**: `AuthResult`（この中にアクセストークンが含まれます）

## 実装のポイント

### スコープベースのキャッシュ管理

```kotlin
private val cacheMap = ConcurrentHashMap<AuthCacheKey, IAuthenticationResult>()
private fun scopesToKey(scopes: List<String>): AuthCacheKey = scopes.toSet()
```

- スコープ（権限）ごとに異なるアクセストークンをキャッシュ
- `Set<String>`をキーとして使用することで、スコープの順序に依存しない一意性を確保

### 期限管理による効率的な更新

```kotlin
const val EXPIRED_MINUTES = 2
const val SHOULD_REFRESH_MINUTES = 5

// 即時更新対象判定
fun IAuthenticationResult.expired() = System.currentTimeMillis() >= (this.expiresOn.time - EXPIRED_MINUTES * 60 * 1000)
// 事前更新対象判定
fun IAuthenticationResult.shouldRefresh() = System.currentTimeMillis() >= (this.expiresOn.time - SHOULD_REFRESH_MINUTES * 60 * 1000)
```

- **即時更新対象（2分前）**: トークンが無効になる前に即時更新を実行
- **事前更新対象（5分前）**: バックグラウンドで事前更新を実行

### 同期関数としての高速レスポンス

```kotlin
@WorkerThread
fun acquireAuth(scopes: List<String>): AuthResult {
    val key = scopesToKey(scopes)
    val cache = cacheMap[key]

    // 有効なキャッシュがある場合
    if (cache != null && !cache.expired()) {
        // 事前更新対象の場合
        if (cache.shouldRefresh()) {
            // バックグラウンドで更新（呼び出し元は待機しない）
            ensureBackgroundScope().launch {
                fetchCache(scopes)
            }
        }

        // 有効なキャッシュを即座に戻す（高速）
        return AuthResult.Success(cache)
    }

    // 有効なキャッシュがない場合のみ同期的に取得（低速）
    return runBlocking(Dispatchers.IO) {
        fetchCache(scopes)
    }
}
```

- 有効なキャッシュがある場合は即座に戻す（高速レスポンス）
- 事前更新対象の場合はバックグラウンドで更新（次回アクセス時に備える）
- 有効なキャッシュがない場合のみ同期的に新規取得（低速レスポンス）

### 重複実行の防止

```kotlin
private val mutexMap = ConcurrentHashMap<AuthCacheKey, Mutex>()

private suspend fun fetchCache(scopes: List<String>): AuthResult {
    val key = scopesToKey(scopes)
    val mutex = mutexMap.computeIfAbsent(key) { Mutex() }
    
    return mutex.withLock {
        // 重複チェック後に実際の認証処理を実行
    }
}
```

- スコープごとにMutexを管理
- 同じスコープに対する重複する認証リクエストを防止
- ネットワークリソースの無駄遣いを回避

### リソース管理とライフサイクル対応

clear()でリソースを解放し、メモリリークを防止します

## パフォーマンスの特徴

- **初回アクセス**: 認証サーバーとの通信が発生（低速）
- **キャッシュヒット時**: 即座にレスポンス（高速）
- **バックグラウンド更新**: 呼び出し元の待機時間なし
- **重複防止**: 同時リクエストの効率化
