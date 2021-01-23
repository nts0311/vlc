package app.tek4tv.digitalsignage.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import app.tek4tv.digitalsignage.CrashHandler
import app.tek4tv.digitalsignage.HubManager
import app.tek4tv.digitalsignage.R
import app.tek4tv.digitalsignage.SerialPort
import app.tek4tv.digitalsignage.media.PlayerManager
import app.tek4tv.digitalsignage.model.*
import app.tek4tv.digitalsignage.utils.*
import app.tek4tv.digitalsignage.viewmodels.MainViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.videolan.libvlc.*
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.*
import java.text.SimpleDateFormat
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
    )

    private lateinit var mVideoLayout: VLCVideoLayout

    private val viewModel by viewModels<MainViewModel>()

    @Inject
    lateinit var moshi: Moshi

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayerManager
    private lateinit var audioManager: AudioManager

    private lateinit var preference: SharedPreferences

    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var volume = "100"
    private var version = "3.0.1"

    private var receivedConnectionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        preference = getPreferences(MODE_PRIVATE)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE


        if (needGrantPermission())
            requestAppPermission()

        mVideoLayout = findViewById(R.id.video_layout)
        playerManager = PlayerManager(applicationContext, lifecycleScope, viewModel, mVideoLayout)
        val orientation = preference.getInt(PREF_ORIENTATION, 1)
        playerManager.mode = orientation

        NetworkUtils.instance.startNetworkListener(this)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        hubManager = HubManager(lifecycleScope, this, viewModel, moshi) { command, message ->
            handleFromCommandServer(command, message)
        }
        initHubConnect()
        registerObservers()



        try {
            initSerialPort()
            readDevice()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val a = NetworkUtils.getCellSignalStrength(applicationContext)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            UPDATE_PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                } else {
                }
            }
        }
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

    private fun requestAppPermission() {
        requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)
    }


    private fun registerObservers() {
        viewModel.broadcastList.observe(this)
        {
            playerManager.setPlaylistContent(it, viewModel.getAudioList(applicationContext))

            playerManager.checkScheduledMedia()

            viewModel.downloadMedias(applicationContext)
        }
    }

    private fun startPlayingMedia() {
        viewModel.getPlaylist(applicationContext, false)
        viewModel.checkPlaylist(applicationContext)
    }


    private fun initHubConnect() {
        NetworkUtils.instance.mNetworkLive.observe(this)
        { isConnected ->
            if (isConnected) {

                if (!viewModel.isPlaying)
                    startPlayingMedia()

                if (hubManager.hubConnection == null) {
                    hubManager.createNewHubConnection()
                    Log.d("Connected", "Connected")
                }

                getLocation()
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
            //count_ping_hub = 0
            var responseHub = ReponseHub()
            if (message.startsWith("{")) {
                val jsonAdapter = moshi.adapter(ReponseHub::class.java)
                responseHub = jsonAdapter.fromJson(message)!!
            }

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
                        writeToDevice(buildReadMessage(Define.FUNC_WRITE_READ_STATUS_PARAM, ""))
                        receivedConnectionId = responseHub.message!!
                    }
                    Status.GET_LOCATION -> {
                        if (mlocation != null) {
                            val connectionId = responseHub.message
                            val result = "${mlocation!!.latitude},${mlocation!!.longitude}"
                            Log.d("location", connectionId)

                            val receiveMessage =
                                DirectMessage(Utils.getDeviceId(applicationContext)!!, result)


                            hubManager.sendDirectMessage(
                                connectionId!!,
                                Utils.DEVICE_LOCATION,
                                Utils.toJsonString(
                                    moshi,
                                    DirectMessage::class.java,
                                    receiveMessage
                                )
                            )
                        } else
                            Log.d("location", "location not found")
                    }
                    Status.SET_VOLUME -> {
                    }
                    Status.STOP -> {
                    }
                    Status.PAUSE -> {
                    }
                    Status.START -> {
                    }
                    Status.RESTART -> {
                        writeToDevice(buildReadMessage(Define.FUNC_WRITE_RESTART_DEVICE, ""))
                    }
                    Status.RELOAD -> {

                    }
                    Status.SWITCH_MODE_FM -> {
                    }
                    Status.SET_MUTE_DEVICE -> {
                        writeToDevice(
                            buildWriteMessage(
                                Define.FUNC_WRITE_FORCE_SET_MUTE,
                                responseHub.message ?: ""
                            )
                        )
                    }
                    Status.SET_VOLUME_DEVICE -> {
                        writeToDevice(
                            buildWriteMessage(
                                Define.FUNC_WRITE_FORCE_SET_VOLUME,
                                responseHub.message ?: ""
                            )
                        )
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
                                applicationContext,
                                responseHub.message!!
                            )

                            playerManager.audioList = viewModel.getAudioList(applicationContext)
                        }
                    }
                    Status.ROTATION -> {


                        if (responseHub.message != null) {
                            Log.d("rotation", responseHub.message.toString())
                            try {
                                val requireOrientation = responseHub.message!!.toInt()
                                preference.edit {
                                    putInt(PREF_ORIENTATION, requireOrientation)
                                }
                                playerManager.switchViewOrientation(requireOrientation)
                            } catch (e: Exception) {
                                Log.e("orientation", "Error parsing orientation")
                            }
                        }
                    }

                    Status.SET_TIME_OVER -> {
                        writeToDevice(buildReadMessage(Define.FUNC_WRITE_PLAY_NO_SOURCE, ""));
                    }

                    Status.SET_TIME_ON -> {
                        writeToDevice(buildReadMessage(Define.FUNC_WRITE_PLAY_VOD_LIVE, volume))
                    }

                    Status.GET_APP_VERSION -> {
                        hubManager.sendMessage(version, Utils.APP_VERSION)
                    }


                    Status.DTMF_STATUS -> {
                        //0: ON, 1:
                        writeToDevice(
                            buildWriteMessage(
                                Define.FUNC_WRITE_DTMF,
                                responseHub.message ?: ""
                            )
                        )
                    }

                    Status.NETWORK_INFO -> {

                        val connectionId = responseHub.message

                        val networkClass = NetworkUtils.getNetworkClass(applicationContext)
                        val dataUsage = NetworkUtils.networkUsage(applicationContext)

                        val result = "$networkClass,$dataUsage"

                        val receiveMessage =
                            DirectMessage(Utils.getDeviceId(applicationContext)!!, result)


                        hubManager.sendDirectMessage(
                            connectionId!!,
                            Utils.NETWORK_INFO,
                            Utils.toJsonString(moshi, DirectMessage::class.java, receiveMessage)
                        )

                        Log.d(
                            "directmess",
                            Utils.toJsonString(moshi, DirectMessage::class.java, receiveMessage)
                        )
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun setMute(mute: String) {
        val direction = if (mute == "1") AudioManager.ADJUST_MUTE
        else AudioManager.ADJUST_UNMUTE

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
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

    private var mlocation: Location? = null

    private fun getLocation() {
        try {
            val locationManager =
                this.getSystemService(LOCATION_SERVICE) as LocationManager
            val locationListener: LocationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    mlocation = location

                    if (mlocation != null)
                        Log.d(
                            "location",
                            "lat: ${mlocation!!.latitude}, long: ${mlocation!!.longitude}"
                        )
                }

                override fun onStatusChanged(s: String, i: Int, bundle: Bundle) {}
                override fun onProviderEnabled(s: String) {}
                override fun onProviderDisabled(s: String) {}
            }
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("location", "location permissions not granted")
                return
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                100,
                100f,
                locationListener
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                100,
                100f,
                locationListener
            )
        } catch (ex: java.lang.Exception) {
        }
    }


    private val UART_NAME = "/dev/ttyS4"

    @Throws(IOException::class)
    private fun initSerialPort() {
        serialPort = SerialPort(File(UART_NAME), 9600, 0)
        inputStream = serialPort!!.getInputStream()
        outputStream = serialPort!!.getOutputStream()
        if (serialPort != null && inputStream != null && outputStream != null) {
            lifecycleScope.launch {
                while (true) {
                    onWatchDog()
                    delay(3000)
                }
            }
        }
    }

    private fun onWatchDog() {


        val looseHubConnection = if (hubManager.lastPing != "") {
            val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            val now = Calendar.getInstance()
            val lastPingTime = Calendar.getInstance().apply {
                time = dateformat.parse(hubManager.lastPing)
            }
            now.timeInMillis - lastPingTime.timeInMillis > 45000
        } else false


        if (outputStream != null) {

            //$$,1,8,1,910,675,0,0,0,1,0,25
            //0: not internet, 1: ok , 2: internet, disconnected
            //$$,1,2,0: normal(1: be restarted neet to jump to realtime)
            //$$,1,8,1,910,675,5,0,0,1,0,28: 1: fm/am, fm freq, am freq, vol, audio source, pa, mute/unmute, external mic, tempareture
            //$$,1,9,software version, device versionnải
            try {

                val onAir: Boolean = NetworkUtils.isNetworkConnected(this)
                var param = if (onAir) "1" else "0"
                Log.d("hub api", looseHubConnection.toString())
                if (onAir) {
                    if (looseHubConnection) {
                        param = "2"
                    }
                }
                param = "$param,${Utils.getMacAddr()}"
                val mes: String = buildWriteMessage(Define.FUNC_WRITE_WATCH_DOG, param)

                outputStream!!.write(mes.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private var isRead = false
    private fun buildWriteMessage(funtionId: String, data: String): String {
        val s = "$$,$funtionId,$data,\r\n"
        Log.d("requestdevice", s)
        return s
    }

    private fun buildReadMessage(functionId: String, message: String): String {
        isRead = true
        val s = "$$,$functionId,$message,\r\n"
        Log.d("requestevice", s)
        return s
    }


    var dataFinal = StringBuffer()


    private fun readDevice() {
        lifecycleScope.launch {

            withContext(Dispatchers.IO)
            {
                while (!Thread.currentThread().isInterrupted) {
                    var size: Int
                    try {
                        val buffer = ByteArray(64)
                        if (inputStream == null) return@withContext
                        val a = inputStream!!.available()
                        Log.d("aaa", a.toString())

                        size = inputStream!!.read(buffer)

                        if (size > 0) {
                            val data = String(buffer, 0, size)
                            dataFinal.append(data)
                            if (dataFinal.toString().endsWith("\r\n")) {
                                val s: String = dataFinal.toString()
                                onDataReceived(s)
                                dataFinal = StringBuffer()
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        return@withContext
                    }
                }
            }


        }
    }


    private fun onDataReceived(data: String?) {
        // read data
        Log.d("OnDataReceived", "dataFinal: $data")
        if (data!!.endsWith("\r\n")) {
            if (data != null && !data.isEmpty() && data.startsWith("$$,")) {
                if (isRead) {
                    //Log.d("OnDataReceived", data)
                    val receiveMessage =
                        DirectMessage(Utils.getDeviceId(applicationContext)!!, data)
                    hubManager.sendDirectMessage(
                        receivedConnectionId,
                        Utils.DEVICE_INFO,
                        Utils.toJsonString(moshi, DirectMessage::class.java, receiveMessage)
                    )
                    isRead = false
                }
            }
        }
    }

    private fun writeToDevice(message: String) {
        if (outputStream != null) {
            try {
                outputStream!!.write(message.toByteArray())
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }
}

