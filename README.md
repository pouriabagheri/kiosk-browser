# Kiosk Browser for Android
This app creates a KIOSK mode and loads a specifc url inside of a WebView.
User can use a secret pattern to prompt a dialog box asking for a password to exit the kiosk mode.

to put the device in full Kiosk mode you need to give the application owner access. (such as disabling physical button and etc.
one of the ways this can be done is through ADB shell using following command
```
adb shell dpm set-device-owner com.bagherifaez.kioskbrowser/.DevAdminReceiver
```

To load your own url, edit the `MY_URL` variable.

To white list your domains or Urls add them to `allowedHosts` or `allowedUrls`.

To change the secret pattern edit `pattern` array.

To change the password edit `m_password`.