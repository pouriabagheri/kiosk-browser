package com.bagherifaez.kioskbrowser

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.*
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import android.provider.Settings
import android.view.View
import java.lang.reflect.InvocationTargetException
import android.os.Bundle
import android.view.MotionEvent

import android.support.v7.app.AlertDialog
import android.text.InputType
import android.widget.EditText


class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.java.simpleName
    private lateinit var mAdminComponentName: ComponentName
    private lateinit var mDevicePolicyManager: DevicePolicyManager
    public var isInKioskMode = true


    companion object {
        const val LOCK_ACTIVITY_KEY = "com.bagherifaez.kioskbrowser.MainActivity"
        // This is the page that will be loaded by kiosk app
        private const val MY_URL = "https://www.bagherifaez.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mAdminComponentName = DevAdminReceiver.getComponentName(this)
        mDevicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val b = intent.extras

        if (b != null) {
            isInKioskMode = b.getBoolean(LOCK_ACTIVITY_KEY)
        }

        if (isInKioskMode) {
            checkIfDeviceOwner()
        }
        initWebView()
    }

//    override fun onResume() {
//        super.onResume()
//        tryToStartLockTask()
//    }

    private fun checkIfDeviceOwner() {

        var isAdmin = isAdmin()
        if (isInKioskMode) {
            setKioskPolicies(true, isAdmin)
        }
    }

    private fun isAdmin(): Boolean {
        var isAdmin = false
        if (mDevicePolicyManager.isDeviceOwnerApp(packageName)) {
            Toast.makeText(applicationContext, R.string.device_owner, Toast.LENGTH_SHORT).show()
            isAdmin = true
        } else {
            Toast.makeText(applicationContext, R.string.not_device_owner, Toast.LENGTH_SHORT).show()
        }
        return isAdmin
    }

    private fun setKioskPolicies(enable: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(enable, isAdmin)
        setImmersiveMode(enable)
    }

    // region restrictions
    private fun setRestrictions(disallow: Boolean) {
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disallow)
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disallow)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, disallow)
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, disallow)
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, disallow)
    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) = if (disallow) {
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, restriction)
    } else {
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, restriction)
    }
    // endregion

    private fun enableStayOnWhilePluggedIn(active: Boolean) = if (active) {
        mDevicePolicyManager.setGlobalSetting(
            mAdminComponentName,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            Integer.toString(
                BatteryManager.BATTERY_PLUGGED_AC
                        or BatteryManager.BATTERY_PLUGGED_USB
                        or BatteryManager.BATTERY_PLUGGED_WIRELESS
            )
        )
    } else {
        mDevicePolicyManager.setGlobalSetting(
            mAdminComponentName,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            "0"
        )
    }

    private fun setLockTask(start: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            mDevicePolicyManager.setLockTaskPackages(
                mAdminComponentName,
                if (start) arrayOf(packageName) else arrayOf()
            )
        }
        if (start) {
            startLockTask()
            isInKioskMode = true
        } else {
            stopLockTask()
            isInKioskMode = false
        }
    }

    private fun setUpdatePolicy(enable: Boolean) {
        if (enable) {
            mDevicePolicyManager.setSystemUpdatePolicy(
                mAdminComponentName,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )
        } else {
            mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, null)
        }
    }

    private fun setAsHomeApp(enable: Boolean) {
        if (enable) {
            val intentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            mDevicePolicyManager.addPersistentPreferredActivity(
                mAdminComponentName,
                intentFilter,
                ComponentName(packageName, MainActivity::class.java.name)
            )
        } else {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(
                mAdminComponentName, packageName
            )
        }
    }

    private fun setKeyGuardEnabled(enable: Boolean) {
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, !enable)
    }

    private fun setImmersiveMode(enable: Boolean) {
        if (enable) {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION /** uncomment to hide the navigation bar */

                    )
            window.decorView.systemUiVisibility = flags
        } else {
            val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            window.decorView.systemUiVisibility = flags
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {

        webView.webViewClient = KioskWebViewClient()

        val ws = webView.getSettings()

        ws.setJavaScriptEnabled(true)
        ws.setAllowFileAccess(true)

        setDesktopMode(webView, true)
        webView.settings.javaScriptEnabled = true
        ws.javaScriptEnabled = true
        ws.allowFileAccess = true
        ws.builtInZoomControls = true
        ws.displayZoomControls = false
        ws.domStorageEnabled = true
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.setSupportZoom(true)
        ws.defaultTextEncodingName = "utf-8"
        ws.pluginState = WebSettings.PluginState.ON

        if (Build.VERSION.SDK_INT >= 21) {
            ws.setMixedContentMode(0)
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else if (Build.VERSION.SDK_INT >= 19) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            try {
                Log.d(TAG, "Enabling HTML5-Features")
                val m1 = WebSettings::class.java.getMethod(
                    "setDomStorageEnabled",
                    *arrayOf<Class<*>>(java.lang.Boolean.TYPE)
                )
                m1.invoke(ws, java.lang.Boolean.TRUE)

                val m2 = WebSettings::class.java.getMethod(
                    "setDatabaseEnabled",
                    *arrayOf<Class<*>>(java.lang.Boolean.TYPE)
                )
                m2.invoke(ws, java.lang.Boolean.TRUE)

                val m3 = WebSettings::class.java.getMethod(
                    "setDatabasePath",
                    *arrayOf<Class<*>>(String::class.java)
                )
                m3.invoke(ws, "/data/data/$packageName/databases/")

                val m4 = WebSettings::class.java.getMethod(
                    "setAppCacheMaxSize",
                    *arrayOf<Class<*>>(java.lang.Long.TYPE)
                )
                m4.invoke(ws, 1024 * 1024 * 8)

                val m5 = WebSettings::class.java.getMethod(
                    "setAppCachePath",
                    *arrayOf<Class<*>>(String::class.java)
                )
                m5.invoke(ws, "/data/data/$packageName/cache/")

                val m6 = WebSettings::class.java.getMethod(
                    "setAppCacheEnabled",
                    *arrayOf<Class<*>>(java.lang.Boolean.TYPE)
                )
                m6.invoke(ws, java.lang.Boolean.TRUE)

                Log.d(TAG, "Enabled HTML5-Features")
            } catch (e: NoSuchMethodException) {
                Log.e(TAG, "Reflection fail", e)
            } catch (e: InvocationTargetException) {
                Log.e(TAG, "Reflection fail", e)
            } catch (e: IllegalAccessException) {
                Log.e(TAG, "Reflection fail", e)
            }

        }

        webView.loadUrl(MY_URL)
    }

    private fun tryToStartLockTask() {
        try {

            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(packageName)) {
                // Allow locktask for my app if not already allowed
                if (!dpm.isLockTaskPermitted(packageName)) {
                    val cn = ComponentName(this, DevAdminReceiver::class.java!!)
                    dpm?.setLockTaskPackages(cn, arrayOf(packageName))
                }

                startLockTask()

            } else {
                Toast.makeText(
                    this,
                    getString(R.string.err_locktask_not_permitted),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }

        } catch (e: Exception) {

            // Cannot start locktask... try a bit later
            Handler(mainLooper).postDelayed({

                tryToStartLockTask()
            }, 2000)
        }
    }

    // WebViewClient will handle the blacklisting/whitelisting of URLs
    internal class KioskWebViewClient : WebViewClient() {

        // Whitelisted hosts
        private val allowedHosts = arrayOf(
            "bagherifaez.com",
            "www.bagherifaez.com"
        )

        /** if you need to white list a website partialy based on url **/
        // Whitelisted Urls
        private val allowedURL = arrayOf(
            "twitter.com/puriabagheri"
        )


        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {


            val match = allowedURL.filter {
                cleanURL(request?.url.toString()).contains(
                    it,
                    ignoreCase = true
                )
            }

            if (allowedHosts.contains(request?.url?.host) || match.isNotEmpty())
                return false

            return true
        }

        private fun cleanURL(url: String): String {
            return url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)".toRegex(), "")
        }
    }

    fun setDesktopMode(webView: WebView, enabled: Boolean) {
        var newUserAgent: String? = webView.settings.userAgentString
        if (enabled) {
            try {
                val ua = webView.settings.userAgentString
                val androidOSString =
                    webView.settings.userAgentString.substring(ua.indexOf("("), ua.indexOf(")") + 1)
                newUserAgent =
                    webView.settings.userAgentString.replace(androidOSString, "(X11; Linux x86_64)")
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            newUserAgent = null
        }

        webView.settings.userAgentString = newUserAgent
        webView.settings.useWideViewPort = enabled
        webView.settings.loadWithOverviewMode = enabled
        webView.reload()
    }

    override fun onBackPressed() {
        if (isInKioskMode) {
            webView.loadUrl(MY_URL)
        } else {
            super.onBackPressed()
        }
    }

    fun exitKioskMode() {
        var isAdmin = isAdmin()
        setKioskPolicies(false, isAdmin)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        intent.putExtra(LOCK_ACTIVITY_KEY, false)
        startActivity(intent)
    }


    /**
     * secret pattern to exit kiosk mode
     * source:  https://stackoverflow.com/a/48445638/2131176
     **/
    // This pattern means :
    // 1, 2, 3, 4 : touch with 4 fingers (adding one at a time)
    // 3, 2 : removes 2 any touches (again one at a time)
    // 3, 2 : add, then remove one touch
    val pattern = listOf(1, 2, 3, 4, 3, 2, 3, 2)

    // current number of touches
    val pointers = mutableSetOf<Int>()

    // current position in pattern
    var patternIndex = 0

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // new gesture, reset
                pointers.clear()
                patternIndex = 0
                pointers.add(ev.getPointerId(ev.actionIndex))
                checkPattern()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // new touch
                pointers.add(ev.getPointerId(ev.actionIndex))
                checkPattern()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // touch released
                pointers.remove(ev.getPointerId(ev.actionIndex))
                checkPattern()
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    fun checkPattern() {
        if (pattern[patternIndex] == pointers.size) {
            // progressing

            patternIndex++

            if (patternIndex == pattern.size) {
                // triggered debug mode
                patternIndex = 0
//                showDebugDialog()
                showPasswordDialong()
            }
        } else {
            // failed, reset
            patternIndex = 0
        }
    }

    fun showDebugDialog() {
        AlertDialog.Builder(this)
            .setTitle("Debug mode")
            .setItems(arrayOf<String>("exit kiosk", "cancel")) { dialog, which ->
                when (which) {
                    0 -> exitKioskMode()
                    1 -> setImmersiveMode(true)
                }
            }.setCancelable(false)
            .show()
    }

    fun showPasswordDialong() {
        /**
         *  password to exit the kiosk mode
         * */
        var m_password = "password"

        val builder = AlertDialog.Builder(this)

        builder.setTitle("Enter Password")
        builder.setCancelable(false)

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)
        builder.setPositiveButton("OK") { _, _ ->
           if(m_password == input.text.toString()){
               exitKioskMode()
           }
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
            setImmersiveMode(true)
        }
        builder.show()
    }

}
