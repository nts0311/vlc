package app.tek4tv.digitalsignage.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.net.*
import android.net.ConnectivityManager.NetworkCallback
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.*


class NetworkUtils private constructor() {
    private val TAG = "NetworkUtils"
    var isNetworkConnected = false
        private set
    private val mCallbacks: MutableList<ConnectionCallback> = LinkedList()
    var mNetworkLive = MutableLiveData<Boolean>()
    private val mNetRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    private val mNetCallback: NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "available")
            mNetworkLive.postValue(true)
            isNetworkConnected = true
            for (callback in mCallbacks) {
                callback?.onChange(true)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "lost")
            mNetworkLive.postValue(false)
            isNetworkConnected = false
            for (callback in mCallbacks) {
                callback?.onChange(false)
            }
        }
    }

    fun startNetworkListener(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(mNetCallback)
            } else {
                manager.registerNetworkCallback(mNetRequest, mNetCallback)
            }
            Log.d(TAG, "#startNetworkListener() success")
        } else {
            Log.d(TAG, "#startNetworkListener() failed")
        }
    }

    fun stopNetworkListener(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (manager != null) {
            manager.unregisterNetworkCallback(mNetCallback)
            Log.d(TAG, "#stopNetworkListener() success")
        } else {
            Log.d(TAG, "#stopNetworkListener() failed")
        }
    }

    fun addObserver(owner: LifecycleOwner?, observer: Observer<Boolean>?) {
        mNetworkLive.observe(owner!!, observer!!)
    }

    fun removeObserver(observer: Observer<Boolean>?) {
        mNetworkLive.removeObserver(observer!!)
    }

    fun addCallback(callback: ConnectionCallback?) {
        if (callback != null) {
            mCallbacks.add(callback)
        }
    }

    fun removeCallback(callback: ConnectionCallback?) {
        if (callback != null && !mCallbacks.isEmpty()) {
            mCallbacks.remove(callback)
        }
    }

    fun clearCallbacks() {
        mCallbacks.clear()
    }

    interface ConnectionCallback {
        fun onChange(netWorkState: Boolean)
    }

    companion object {
        const val BASE_URL = "https://dev.device.tek4tv.vn/"
        const val URL_HUB = "https://hub.iot.tek4tv.vn/iothub"
        private var INSTANCE: NetworkUtils? = null
        val instance: NetworkUtils
            get() {
                if (INSTANCE == null) {
                    INSTANCE = NetworkUtils()
                }
                return INSTANCE!!
            }

        fun isNetworkConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetworkInfo
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting
            }
            return false
        }

        fun getNetworkClass(context: Context): String {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = cm.activeNetworkInfo
            if (info == null || !info.isConnected) return "-" // not connected
            if (info.type == ConnectivityManager.TYPE_WIFI) return "WIFI"
            if (info.type == ConnectivityManager.TYPE_MOBILE) {
                return when (info.subtype) {
                    TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
                    TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN, 19 -> "4G"
                    else -> "?"
                }
            }
            return "?"
        }

        fun networkUsage(context: Context): String {
            val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val runningApps = manager.runningAppProcesses
            for (runningApp in runningApps) {
                val received = TrafficStats.getUidRxBytes(runningApp.uid)
                val sent = TrafficStats.getUidTxBytes(runningApp.uid)
                Log.d(
                    "ok", String.format(
                        Locale.getDefault(),
                        "uid: %1d - name: %s: Sent = %1d, Rcvd = %1d",
                        runningApp.uid,
                        runningApp.processName,
                        sent,
                        received
                    )
                )
                return "$received,$sent"
            }
            return ""
        }

        @SuppressLint("MissingPermission")
        fun getCellSignalStrength(context: Context): Int {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val cellInfo = tm.allCellInfo[0]

            if (cellInfo != null) {
                return when (cellInfo) {
                    is CellInfoLte -> cellInfo.cellSignalStrength.level
                    is CellInfoCdma -> cellInfo.cellSignalStrength.level
                    is CellInfoGsm -> cellInfo.cellSignalStrength.level
                    is CellInfoWcdma -> cellInfo.cellSignalStrength.level
                    else -> CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
                }
            }

            return CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
        }

        fun getWifiSignalStrength(appContext: Context): Int {
            val wifiManager =
                appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rssi = wifiManager.connectionInfo.rssi
            return WifiManager.calculateSignalLevel(rssi, 5)
        }
    }
}