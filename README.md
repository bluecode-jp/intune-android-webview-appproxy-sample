<!-- omit in toc -->
# Intune Test App

- [インターネットアクセス許可](#インターネットアクセス許可)

## インターネットアクセス許可

AndroidManifest.xmlに下記を追加

```xml:app/src/main/AndroidManifest.xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```
