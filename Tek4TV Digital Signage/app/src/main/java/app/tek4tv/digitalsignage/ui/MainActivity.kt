package app.tek4tv.digitalsignage.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import app.tek4tv.digitalsignage.*
import app.tek4tv.digitalsignage.media.PlayerManager
import app.tek4tv.digitalsignage.model.*
import app.tek4tv.digitalsignage.utils.*
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.videolan.libvlc.*
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.*
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val UPDATE_PERMISSION_REQUEST_CODE = 1
    private val PREF_ORIENTATION = "pref_orientation"

    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_SMS,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
    )

    private lateinit var mVideoLayout: VLCVideoLayout

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayerManager
    private lateinit var audioManager: AudioManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var preference: SharedPreferences

    private var volume = "100"
    private var version = "3.0.1"

    private var locationTracker = LocationTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        preference = getPreferences(MODE_PRIVATE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        hubManager = HubManager(lifecycleScope, this, viewModel, moshi) { command, message ->
            handleFromCommandServer(command, message)
        }

        serialPortController =
            SerialPortController(applicationContext, lifecycleScope, hubManager, moshi)
        serialPortController.connectToSerialPort()

        mVideoLayout = findViewById(R.id.video_layout)
        playerManager = PlayerManager(applicationContext, lifecycleScope, viewModel, mVideoLayout)
        val orientation = preference.getInt(PREF_ORIENTATION, 0)
        playerManager.rotationMode = orientation

        NetworkUtils.instance.startNetworkListener(this)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.onActivityDestroy()
    }

    override fun onStart() {
        super.onStart()
        playerManager.attachVisualPlayerView()
        startPlayingMedia()
    }

    private fun needGrantPermission(): Boolean {
        var needGrantPermission = false

        permissions.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
                needGrantPermission = true
                return@forEach
            }
        }

        return needGrantPermission
    }

    private fun registerObservers() {
        viewModel.broadcastList.observe(this) {
            playerManager.audioList = viewModel.getAudioList(applicationContext)
            playerManager.onNewBroadcastList()
            playerManager.checkScheduledMedia()
            viewModel.downloadMedias(applicationContext)
        }
    }

    private fun startPlayingMedia() {
        viewModel.getPlaylist(applicationContext, false)
        viewModel.checkPlaylist(applicationContext)
    }


    private fun initHubConnect() {
        NetworkUtils.instance.mNetworkLive.observe(this) { isConnected ->
            if (isConnected) {

                if (!viewModel.isPlaying) startPlayingMedia()

                if (hubManager.hubConnection == null) {
                    hubManager.createNewHubConnection()
                    Log.d("Connected", "Connected")
                }

                locationTracker.getLocation(this@MainActivity)
            } else {
                hubManager.hubConnection = null
            }
        }
    }

    private fun playMediaItemByIndex(index: Int) {
        viewModel.playlistIndex = index
        playerManager.playMediaByIndex(viewModel.playlistIndex)
    }

    private fun handleFromCommandServer(command: String?, message: String?) {
        try {
            if (command == null || command.isEmpty()) {
                return
            }
            if (message == null || message.isEmpty()) {
                return
            }

            val responseHub = hubManager.responseHub

            val isImei = Utils.getDeviceId(applicationContext) == responseHub.imei
            if (isImei) {
                when (command) {
                    Status.GET_LIST -> {
                    }
                    Status.UPDATE_LIST -> {
                        viewModel.playlistIndex = 0
                        viewModel.getPlaylist(applicationContext, true)
                    }
                    Status.NEXT -> {
                    }
                    Status.PREVIEW -> {
                    }
                    Status.JUMP -> {
                        Log.d(command, message)
                        val id = responseHub.message!!.trim().toInt()
                        playMediaItemByIndex(id)
                    }
                    Status.LIVE -> {

                    }
                    Status.UPDATE_STATUS -> {
                        serialPortController.apply {
                            writeToDevice(
                                buildReadMessage(
                                    Define.FUNC_WRITE_READ_STATUS_PARAM, ""))
                        }
                    }
                    Status.GET_LOCATION -> {
                        val connectionId = responseHub.message
                        if (locationTracker.mlocation != null && connectionId != null) {

                            val result =
                                "${locationTracker.mlocation!!.latitude},${locationTracker.mlocation!!.longitude}"

                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.DEVICE_LOCATION, result)
                        } else Log.d("location", "location not found")
                    }
                    Status.SET_VOLUME -> {
                        if (responseHub.message != null) {
                            setVolume(responseHub.message!!)
                        }
                    }
                    Status.STOP -> {
                    }
                    Status.PAUSE -> {
                    }
                    Status.START -> {
                    }
                    Status.RESTART -> {
                        serialPortController.apply {
                            writeToDevice(
                                buildReadMessage(
                                    Define.FUNC_WRITE_RESTART_DEVICE, ""))
                        }
                    }
                    Status.RELOAD -> {

                    }
                    Status.SWITCH_MODE_FM -> {
                    }
                    Status.SET_MUTE_DEVICE -> {
                        if (responseHub.message != null) {
                            serialPortController.apply {
                                writeToDevice(
                                    buildWriteMessage(
                                        Define.FUNC_WRITE_FORCE_SET_MUTE, responseHub.message!!))
                            }
                        }
                    }
                    Status.SET_VOLUME_DEVICE -> {
                        if (responseHub.message != null) {
                            serialPortController.apply {
                                writeToDevice(
                                    buildWriteMessage(
                                        Define.FUNC_WRITE_FORCE_SET_VOLUME, responseHub.message!!))
                            }
                        }
                    }

                    Status.UPDATE_VERSION -> {
                        if (responseHub.message != null) {
                            Log.d("update", responseHub.message!!)
                            downloadUpdateApk(responseHub.message!!, this)
                        }
                    }

                    Status.UPDATE_MUSIC -> {
                        if (responseHub.message != null) {
                            Log.d("Update Music", responseHub.message!!)
                            viewModel.getAudioListFromNetwork(
                                applicationContext, responseHub.message!!)

                            playerManager.audioList = viewModel.getAudioList(applicationContext)
                        }
                    }
                    Status.ROTATION -> {
                        if (responseHub.message != null) {
                            Log.d("rotation", responseHub.message.toString())
                            try {
                                val requireOrientation = responseHub.message!!.toInt()
                                preference.edit(commit = true) {
                                    putInt(PREF_ORIENTATION, requireOrientation)
                                }

                                CrashHandler.restartApp(MyApp.instance!!, this)
                            } catch (e: Exception) {
                                Log.e("orientation", "Error parsing orientation")
                            }
                        }
                    }

                    Status.SET_TIME_OVER -> {
                        serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_PLAY_NO_SOURCE, ""))
                        }
                    }

                    Status.SET_TIME_ON -> {
                        serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_PLAY_VOD_LIVE, volume))
                        }
                    }

                    Status.GET_APP_VERSION -> {
                        hubManager.sendMessage(version, Utils.APP_VERSION)
                    }


                    Status.DTMF_STATUS -> {
                        //0: ON, 1:
                        serialPortController.apply {
                            writeToDevice(
                                buildWriteMessage(
                                    Define.FUNC_WRITE_DTMF, responseHub.message ?: ""))
                        }
                    }

                    Status.NETWORK_INFO -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            val networkClass = NetworkUtils.getNetworkClass(applicationContext)
                            val dataUsage = NetworkUtils.networkUsage(applicationContext)

                            val level = when (networkClass) {
                                "WIFI" -> NetworkUtils.getWifiSignalStrength(
                                    applicationContext)
                                "?" -> 0
                                else -> NetworkUtils.getCellSignalStrength(applicationContext)
                            }

                            val result = "$networkClass,$dataUsage,$level"

                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.NETWORK_INFO, result)
                        }
                    }

                    Status.STORAGE_INFO -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            val info = appStorageManager.run {
                                "${getTotalRam()},${getUsedRam()},${getTotalRomStorage()},${getUsedRomStorage()}"
                            }

                            hubManager.sendHubDirectMessage(connectionId, Utils.STORAGE_INFO, info)
                        }
                    }

                    Status.GET_AUDIO_PATH -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            val audioList = toJsonList(appStorageManager.getAllMusicPath())
                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.GET_AUDIO_PATH, audioList)
                        }
                    }

                    Status.GET_MEDIA_PATH -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            Log.d("connectionId", connectionId)
                            val mediaList = toJsonList(appStorageManager.getAllMediaPath())
                            hubManager.sendHubDirectMessage(
                                connectionId, Utils.GET_MEDIA_PATH, mediaList)
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


    private fun toJsonList(list: List<String>): String {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(listType)
        return adapter.toJson(list)
    }

    private fun setMute(mute: String) {
        val direction = if (mute == "1") AudioManager.ADJUST_MUTE
        else AudioManager.ADJUST_UNMUTE

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun setVolume(message: String) {
        var volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        try {
            volume = ((maxVolume.toFloat() / 100) * message.toInt()).toInt()
        } catch (e: NumberFormatException) {

        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
    }
}

