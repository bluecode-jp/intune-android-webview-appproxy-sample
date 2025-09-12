(function () {
  if (window.AndroidInterceptRequest) {
    return;
  }

  const isProxyOrigin = function (url) {
    try {
      const urlObj = new URL(url, window.location.href);
      const proxyObj = new URL(Android.getProxyOrigin());

      return urlObj.origin === proxyObj.origin;
    } catch (error) {
      return false;
    }
  };

  const getProxyToken = function (url) {
    if (!isProxyOrigin(url)) {
      return null;
    }

    if (!Android.acquireToken || !Android.getProxyScope) {
      return null;
    }

    return Android.acquireToken([Android.getProxyScope()]);
  };

  const tokenAddedHeaderKey = "X-Token-Added";

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

  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (
    method,
    url,
    async,
    user,
    password
  ) {
    this._logInfo = { method, url };
    return originalOpen.call(this, method, url, async, user, password);
  };

  XMLHttpRequest.prototype.send = function (data) {
    const token = getProxyToken(this._logInfo.url);
    if (token) {
      this.setRequestHeader("Authorization", `Bearer ${token}`);
      this.setRequestHeader(tokenAddedHeaderKey, "true");
    }

    return originalSend.call(this, data);
  };

  window.AndroidInterceptRequest = {
    isProxyOrigin,
    getProxyToken,
    tokenAddedHeaderKey,
  };
})();
