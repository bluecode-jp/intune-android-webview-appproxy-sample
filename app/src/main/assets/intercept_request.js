(function () {
  /**
   * 二重実行を防止
   */
  if (window.AndroidInterceptRequest) {
    return;
  }

  /**
   * AppProxyのURLか判定
   */
  const isProxyOrigin = function (url) {
    try {
      const urlObj = new URL(url, window.location.href);
      const proxyObj = new URL(Android.getProxyOrigin());

      return urlObj.origin === proxyObj.origin;
    } catch (error) {
      return false;
    }
  };

  /**
   * AppProxyのアクセストークンを取得
   * - AppProxy以外のURLへ送信する場合は取得しない
   */
  const getProxyToken = function (url) {
    if (!isProxyOrigin(url)) {
      return null;
    }

    if (!Android.acquireToken || !Android.getProxyScope) {
      return null;
    }

    return Android.acquireToken([Android.getProxyScope()]);
  };

  /**
   * 書き換え済みマークのヘッダー
   * - AndroidのshouldInterceptRequestとの重複実行を回避
   */
  const tokenAddedHeaderKey = "X-Token-Added";

  /*
   * fetch関数を改造
   */

  const originalFetch = window.fetch;
  window.fetch = async function (url, options) {
    const newOptions = options ?? {};

    const token = getProxyToken(url);
    if (token) {
      const newHeaders = new Headers(newOptions.headers);
      newHeaders.set("Authorization", `Bearer ${token}`);
      newHeaders.set(tokenAddedHeaderKey, "true");
      newOptions.headers = newHeaders;
    }

    return originalFetch(url, newOptions);
  };

  /*
   * XMLHttpRequest.send関数を改造
   */

  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (
    method,
    url,
    async,
    user,
    password
  ) {
    this._requestInfo = { method, url };
    return originalOpen.call(this, method, url, async, user, password);
  };

  XMLHttpRequest.prototype.send = function (data) {
    const token = getProxyToken(this._requestInfo.url);
    if (token) {
      this.setRequestHeader("Authorization", `Bearer ${token}`);
      this.setRequestHeader(tokenAddedHeaderKey, "true");
    }

    return originalSend.call(this, data);
  };

  /**
   * 実行済みマーク
   */
  window.AndroidInterceptRequest = {
    isProxyOrigin,
    getProxyToken,
    tokenAddedHeaderKey,
  };
})();
