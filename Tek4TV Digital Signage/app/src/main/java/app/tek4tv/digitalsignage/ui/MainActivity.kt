package app.tek4tv.digitalsignage.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import app.tek4tv.digitalsignage.*
import app.tek4tv.digitalsignage.media.PlayerManager
import app.tek4tv.digitalsignage.model.*
import app.tek4tv.digitalsignage.network.PlaylistService
import app.tek4tv.digitalsignage.utils.*
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import com.github.rongi.rotate_layout.layout.RotateLayout
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

    private val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.REQUEST_INSTALL_PACKAGES,
        Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_SMS, Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.RECORD_AUDIO)

    private lateinit var mVideoLayout: VLCVideoLayout

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var playlistService: PlaylistService

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayerManager
    private lateinit var audioManager: AudioManager

    private lateinit var serialPortController: SerialPortController

    private lateinit var appStorageManager: AppStorageManager

    private lateinit var preference: SharedPreferences

    private var volume = "100"
    private var version = "3.0.1"

    private var locationTracker = LocationTracker()

    private var orientation: Int = 0

    private lateinit var mediaCapture: MediaCapture

    lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        preference = getPreferences(MODE_PRIVATE)

        orientation = preference.getInt(PREF_ORIENTATION, 0)
        setViewOrientation()

        if (needGrantPermission()) requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)

        hubManager = HubManager(lifecycleScope, this, viewModel, moshi) { command, message ->
            handleFromCommandServer(command, message)
        }

        /*serialPortController =
            SerialPortController(applicationContext, lifecycleScope, hubManager)
        serialPortController.connectToSerialPort()*/

        mVideoLayout = findViewById(R.id.vlc_video_layout)
        playerManager = PlayerManager(applicationContext, lifecycleScope, viewModel, mVideoLayout)
        playerManager.rotationMode = orientation


        NetworkUtils.instance.startNetworkListener(this)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        initHubConnect()
        registerObservers()

        appStorageManager = AppStorageManager(applicationContext)

        mediaCapture = MediaCapture(applicationContext, lifecycleScope)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager


        /*lifecycleScope.launch {
            delay(10000)
            mediaCapture.captureImageMega(,resources.displayMetrics.densityDpi)
        }*/


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 69) {
            Toast.makeText(this, "capture", Toast.LENGTH_LONG).show()
            lifecycleScope.launch {
                val isPortrait = orientation == 2 || orientation == 3
                mediaCapture.captureImageMega(playlistService,
                    mediaProjectionManager.getMediaProjection(resultCode, data),
                    resources.displayMetrics.densityDpi, isPortrait)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.onActivityDestroy()
        if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
    }

    override fun onStart() {
        super.onStart()
        playerManager.attachVisualPlayerView()
        startPlayingMedia()
    }

    private fun setViewOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val rotateLayout: RotateLayout = findViewById(R.id.root_layout)

        /*rotateLayout.angle = when (orientation) {
            1 -> 180
            2 -> 90
            3 -> -90
            else -> 0
        }*/
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
            playerManager.audioList = viewModel.audioRepo.audioFileUri
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
                        /* Log.d(command, message)
                        val id = responseHub.message!!.trim().toInt()
                        playMediaItemByIndex(id)*/
                    }
                    Status.LIVE -> {

                    }
                    Status.UPDATE_STATUS -> {
                        /*serialPortController.apply {
                            writeToDevice(
                                buildReadMessage(
                                    Define.FUNC_WRITE_READ_STATUS_PARAM, ""))
                        }*/
                    }
                    Status.GET_LOCATION -> {
                        val connectionId = responseHub.message
                        if (locationTracker.mlocation != null && connectionId != null) {

                            val result =
                                "${locationTracker.mlocation!!.latitude},${locationTracker.mlocation!!.longitude}"

                            hubManager.sendHubDirectMessage(connectionId, Utils.DEVICE_LOCATION,
                                result)
                        } else Log.d("location", "location not found")
                    }
                    Status.SET_VOLUME -> {
                        /*if (responseHub.message != null) {
                            setVolume(responseHub.message!!)
                        }*/
                    }
                    Status.STOP -> {
                    }
                    Status.PAUSE -> {
                    }
                    Status.START -> {
                    }
                    Status.RESTART -> {
                        serialPortController.apply {
                            writeToDevice(buildReadMessage(Define.FUNC_WRITE_RESTART_DEVICE, ""))
                        }
                    }
                    Status.RELOAD -> {

                    }
                    Status.SWITCH_MODE_FM -> {
                    }
                    Status.SET_MUTE_DEVICE -> {
                        if (responseHub.message != null) {
                            serialPortController.apply {
                                writeToDevice(buildWriteMessage(Define.FUNC_WRITE_FORCE_SET_MUTE,
                                    responseHub.message!!))
                            }
                        }
                    }
                    Status.SET_VOLUME_DEVICE -> {
                        if (responseHub.message != null) {
                            serialPortController.apply {
                                writeToDevice(buildWriteMessage(Define.FUNC_WRITE_FORCE_SET_VOLUME,
                                    responseHub.message!!))
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
                            viewModel.getAudioListFromNetwork(applicationContext,
                                responseHub.message!!)

                            //playerManager.audioList = viewModel.getAudioList(applicationContext)
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
                            writeToDevice(buildWriteMessage(Define.FUNC_WRITE_DTMF,
                                responseHub.message ?: ""))
                        }
                    }

                    Status.NETWORK_INFO -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            val networkClass = NetworkUtils.getNetworkClass(applicationContext)
                            val dataUsage = NetworkUtils.networkUsage(applicationContext)

                            val level = when (networkClass) {
                                "WIFI" -> NetworkUtils.getWifiSignalStrength(applicationContext)
                                "?" -> 0
                                else -> NetworkUtils.getCellSignalStrength(applicationContext)
                            }

                            val result = "$networkClass,$dataUsage,$level"

                            hubManager.sendHubDirectMessage(connectionId, Utils.NETWORK_INFO,
                                result)
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
                            hubManager.sendHubDirectMessage(connectionId, Utils.GET_AUDIO_PATH,
                                audioList)
                        }
                    }

                    Status.GET_MEDIA_PATH -> {
                        val connectionId = responseHub.message
                        if (connectionId != null) {
                            Log.d("connectionId", connectionId)
                            val mediaList = toJsonList(appStorageManager.getAllMediaPath())
                            hubManager.sendHubDirectMessage(connectionId, Utils.GET_MEDIA_PATH,
                                mediaList)
                        }
                    }

                    Status.DELETE_ALL_AUDIO -> {
                        appStorageManager.deleteAllMusic()
                    }

                    Status.DELETE_ALL_MEDIA -> {
                        appStorageManager.deleteAllMedia()
                    }

                    Status.SET_MUTE_PLAYER -> {
                        val isMute = responseHub.message
                        if (isMute != null) {
                            if (isMute == "1") setVolume(0)
                            else if (isMute == "0") setVolume(100)
                        }
                    }

                    Status.CAPTURE_SCREEN -> {
                        val isPortrait = orientation == 2 || orientation == 3
                        mediaCapture.captureSurfaceView(playlistService, isPortrait, mVideoLayout)
                        //mediaCapture.captureScreen(playlistService, isPortrait, mVideoLayout)
                        /*  startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                              69)*/
                    }

                    Status.RECORD -> {
                        val shouldStart = responseHub.start!! == "1"

                        if (shouldStart) {
                            if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()

                            mediaCapture.startCaptureAudio()
                        } else {
                            if (mediaCapture.isRecordingAudio) mediaCapture.stopAudioCapture()
                        }
                    }

                    Status.GET_RECORD -> {
                        val connectionId = responseHub.message!!
                        val recordedAudioList =
                            toJsonList(appStorageManager.getAllRecordedAudioPath())
                        hubManager.sendHubDirectMessage(connectionId, Utils.GET_RECORD,
                            recordedAudioList)
                    }

                    Status.UPLOAD_RECORDED -> {
                        val filePath = responseHub.message!!
                        mediaCapture.updateRecordedAudio(filePath)
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

    private fun setVolume(volumeToSet: Int) {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volume = ((maxVolume.toFloat() / 100) * volumeToSet).toInt()

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume,
                AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {

        }
    }
}

