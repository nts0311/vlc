package app.tek4tv.digitalsignage

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.tek4tv.digitalsignage.model.*
import app.tek4tv.digitalsignage.utils.*
import app.tek4tv.digitalsignage.viewmodels.MainViewmodel
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.videolan.libvlc.*
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val USE_TEXTURE_VIEW = false
    private val ENABLE_SUBTITLES = true

    private val UPDATE_PERMISSION_REQUEST_CODE = 1

    private var mVideoLayout: VLCVideoLayout? = null

    private lateinit var mLibVLC: LibVLC
    private lateinit var visualPlayer: CustomPlayer
    private lateinit var audioPlayer: CustomPlayer
    var mainPlayer: CustomPlayer? = null

    private val viewModel by viewModels<MainViewmodel>()

    private var hubConnection: HubConnection? = null


    @Inject
    lateinit var moshi: Moshi

    private var presentImageJob: Job? = null
    private var checkScheduledMediaJob: Job? = null
    private var pingHubJob: Job? = null

    private lateinit var audioManager: AudioManager


    private var audioList = listOf<Uri>()

    private var lastPing = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

        //Rotate screen
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        if (needGrantPermission())
            requestUpdatePermission()

        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--aout=opensles")
        args.add("--avcodec-codec=h264")
        args.add("--network-caching=2000")
        args.add("--no-http-reconnect")
        args.add("--file-logging")
        args.add("--logfile=vlc-log.txt")


        mLibVLC = LibVLC(this, args)
        visualPlayer = CustomPlayer(mLibVLC)
        audioPlayer = CustomPlayer(mLibVLC)
        mVideoLayout = findViewById(R.id.video_layout)

        NetworkUtils.instance.startNetworkListener(this)

        NetworkUtils.instance.mNetworkLive.observe(this)
        { isConnected ->
            if (isConnected)
                initHubConnect()
        }

        /*btnUpdate = findViewById(R.id.btn_update)
        btnUpdate.setOnClickListener {

        }*/

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioList = viewModel.getAudioList(applicationContext)

        initHubConnect()
        registerObservers()
    }

    override fun onStop() {
        super.onStop()
        mainPlayer?.stop()
        audioPlayer.stop()
        visualPlayer.stop()
        visualPlayer.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainPlayer?.release()
        if (!audioPlayer.isReleased)
            audioPlayer.release()

        if (!visualPlayer.isReleased)
            visualPlayer.release()

        mLibVLC.release()
    }

    override fun onStart() {
        super.onStart()
        visualPlayer.attachViews(mVideoLayout!!, null, ENABLE_SUBTITLES, USE_TEXTURE_VIEW)
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
                    //Log.d("quyen", grantResults.toList().toString())
                    //downloadUpdateApk("https://mam.tek4tv.vn/download/player_203.apk", this)

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    /*Toast.makeText(
                        this@MainActivity,
                        "Permission denied!!!",
                        Toast.LENGTH_SHORT
                    ).show()*/
                }
                return
            }
        }
    }

    private fun needGrantPermission(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE
        )

        var needGrantPermission = false

        permissions.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED) {
                needGrantPermission = true
                return@forEach
            }
        }

        return needGrantPermission
    }

    private fun requestUpdatePermission() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE
        )
        requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)
    }


    private fun registerObservers() {
        viewModel.playlist.observe(this)
        {
            checkScheduledMedia()

            viewModel.downloadMedias(applicationContext)


            if (viewModel.playlist.value != null && viewModel.playlistIndex >= viewModel.playlist.value!!.size)
                viewModel.playlistIndex = 0
            playMediaByIndex(viewModel.playlistIndex)
        }
    }

    private fun startPlayingMedia() {
        viewModel.getPlaylist(applicationContext, false)
        viewModel.checkPlaylist(applicationContext)
    }


    private fun playMediaByIndex(index: Int) {
        try {
            val playlist = viewModel.playlist.value

            if (playlist == null || playlist.isEmpty()) return
            val mediaItem = viewModel.playlist.value!![index]
            val media = mediaItem.getVlcMedia(mLibVLC)
            presentImageJob?.cancel()
            when (mediaItem.getMediaType()) {
                MediaType.VIDEO -> {
                    audioPlayer.stop()
                    media.addOption(":fullscreen")
                    mainPlayer = visualPlayer
                    visualPlayer.play(media)
                }
                MediaType.IMAGE -> {
                    presentImage(mediaItem)
                    mainPlayer = audioPlayer
                }
                else -> {
                }
            }

            if (mainPlayer != null) {
                mainPlayer!!.eventListener = { event ->
                    when (event) {
                        MediaPlayer.Event.EndReached -> {
                            playNextMedia()
                            //remove callback
                            mainPlayer!!.eventListener = {}
                        }

                        MediaPlayer.Event.EncounteredError -> {
                            presentImageJob?.cancel()
                            presentImageJob = null

                            playNextMedia()
                            mainPlayer!!.eventListener = {}
                        }
                        MediaPlayer.Event.Stopped -> viewModel.isPlaying = false
                        MediaPlayer.Event.Playing -> viewModel.isPlaying = true
                    }
                }
            }


        } catch (e: Exception) {
            Log.e("Main", e.message)
            e.printStackTrace()
        }
    }


    private fun presentImage(mediaItem: MediaItem) {

        val imageList = mutableListOf(mediaItem)

        if (!viewModel.playlist.value.isNullOrEmpty())
            imageList.addAll(viewModel.playlist.value!!.filter { it.getMediaType() == MediaType.IMAGE })

        val delayDuration = 30000L//getDurationInSecond(duration) * 1000 / imageList.size

        if (audioList.isEmpty())
            audioList = viewModel.getAudioList(applicationContext)

        if (audioList.isNotEmpty()) {
            val backgroundAudio = Media(
                mLibVLC,
                audioList.random()
            )
            audioPlayer.play(backgroundAudio)
        }


        presentImageJob = lifecycleScope.launch {
            var i = 0
            while (i < imageList.size && isActive) {
                val media = imageList[i++]
                visualPlayer.play(media.getVlcMedia(mLibVLC))
                delay(delayDuration)

                if (i >= imageList.size)
                    i = 0
            }
        }
    }


    private fun playNextMedia() {
        mVideoLayout!!.post {
            val playlist = viewModel.playlist.value!!
            viewModel.playlistIndex++
            if (viewModel.playlistIndex >= playlist.size)
                viewModel.playlistIndex = 0

            playMediaByIndex(viewModel.playlistIndex)
        }
    }

    private fun checkScheduledMedia() {
        checkScheduledMediaJob?.cancel()
        checkScheduledMediaJob = lifecycleScope.launch(Dispatchers.Default) {

            val playlist = viewModel.playlist.value ?: listOf()
            val scheduledItems: MutableList<MediaItem> = mutableListOf()
            scheduledItems.addAll(playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })

            while (true) {
                if (scheduledItems.isEmpty()) {
                    scheduledItems.addAll(playlist.filter { it.fixTime.isNotEmpty() && it.fixTime != "00:00:00" })
                }


                var index = -1

                scheduledItems.forEachIndexed { i, mediaItem ->
                    try {
                        val time = mediaItem.fixTime.split(":").map { it.toInt() }

                        val now = Calendar.getInstance()

                        val scheduledTime = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, time[0])
                            set(Calendar.MINUTE, time[1])
                            set(Calendar.SECOND, time[2])
                        }

                        val mediaDuration = getDurationInSecond(mediaItem.duration ?: "00:00:00")

                        if (scheduledTime.timeInMillis <= now.timeInMillis
                            && now.timeInMillis <= scheduledTime.timeInMillis + mediaDuration * 1000
                        ) {
                            Log.d(
                                "hengio",
                                "${now.timeInMillis} - ${scheduledTime.timeInMillis} - ${scheduledTime.timeInMillis + mediaDuration * 1000}"
                            )
                            index = i
                        }

                    } catch (e: NumberFormatException) {
                        Log.e("checkScheduledMedia", "error parsing media fixtime")
                    } catch (e: Exception) {
                        Log.e("checkScheduledMedia", "error checking media fixtime")
                    }
                }

                if (index != -1 && index < scheduledItems.size) {
                    withContext(Dispatchers.Main)
                    {
                        val indexToPlay = playlist.indexOf(scheduledItems[index])

                        playMediaByIndex(indexToPlay)
                        Log.d("Scheduled", "play scheduled $indexToPlay")
                        viewModel.playlistIndex = indexToPlay
                        scheduledItems.removeAt(index)
                    }
                }

                delay(1000)
            }
        }
    }

    private fun initHubConnect() {
        NetworkUtils.instance.mNetworkLive.observe(this)
        { isConnected ->
            if (isConnected) {

                if (!viewModel.isPlaying)
                    startPlayingMedia()

                if (hubConnection == null) {

                    connectToHub()

                    Log.d("Connected", "Connected")
                }
            } else {
                //hubConnection = null
            }

        }
    }

    private fun connectToHub() {
        hubConnection = HubConnectionBuilder.create(NetworkUtils.URL_HUB).build()

        HubConnectionTask { connectionId ->
            if (connectionId != null) {
                Log.d("Connected", connectionId)
                pingTimer()
            }
        }.execute(hubConnection)
        onMessage()
    }

    private fun onMessage() {
        hubConnection?.on(
            "ReceiveMessage",
            { command: String?, message: String? ->
                runOnUiThread {
                    Log.d("command", command ?: "")
                    Log.d("message", message ?: "")
                    handleFromCommandServer(command, message)
                }
            },
            String::class.java,
            String::class.java
        )
    }

    // ham gui du lieu
    private fun sendMessage(mess: String, command: String) {
        try {
            hubConnection!!.invoke(command, Utils.getDeviceId(applicationContext), mess)
        } catch (e: Exception) {
            e.printStackTrace()
            //connectToHub()
        }
    }

    private fun playURLVideo(index: Int) {
        viewModel.playlistIndex = index
        playMediaByIndex(viewModel.playlistIndex)
    }

    private fun handleFromCommandServer(commamd: String?, message: String?) {
        try {
            if (commamd == null || commamd.isEmpty()) {
                return
            }
            if (message == null || message.isEmpty()) {
                return
            }
            //count_ping_hub = 0
            var reponseHub = ReponseHub()
            if (message.startsWith("{")) {
                val jsonAdapter = moshi.adapter(ReponseHub::class.java)
                reponseHub = jsonAdapter.fromJson(message)!!
            }

            if (commamd == Status.PONG) {
                lastPing = message
                Log.d("last ping", lastPing)
            }

            val isImei = Utils.getDeviceId(applicationContext) == reponseHub.imei
            if (isImei) {
                when (commamd) {
                    Status.GET_LIST -> {
                    }
                    Status.UPDATE_LIST -> {
                    }
                    Status.NEXT -> {
                    }
                    Status.PREVIEW -> {
                    }
                    Status.JUMP -> {
                        Log.d(commamd, message)
                        val id = reponseHub.message!!.trim().toInt()
                        val volume = reponseHub.volume
                        playURLVideo(id)
                    }
                    Status.LIVE -> {
                        /*volume = reponseHub.getVolume()
                        playURLVideo(reponseHub.getMessage().trim(), false)*/
                    }
                    Status.UPDATE_STATUS -> pingHub(true)
                    Status.GET_LOCATION -> {
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
                    }
                    Status.RELOAD -> {
                        viewModel.playlistIndex = 0
                        viewModel.getPlaylist(applicationContext, true)
                    }
                    Status.SWITCH_MODE_FM -> {
                    }
                    Status.SET_MUTE_DEVICE -> {
                        if (reponseHub.message != null)
                            setMute(reponseHub.message!!)
                    }
                    Status.SET_VOLUME_DEVICE -> {
                        if (reponseHub.message != null)
                            setVolume(reponseHub.message!!)
                    }
                    Status.GET_VOLUME_DEVICE -> {
                    }
                    Status.GET_SOURCE_AUDIO -> {
                    }
                    /*Status.GET_PA -> {
                        deviceAdrress = Define.Power_Amplifier_R
                        writeToDevice(buildReadMessageNew(Define.FUNC_WRITE_READ_DEVICE_INFO, "6"))
                    }*/
                    Status.GET_FM_FQ -> {
                    }
                    Status.GET_AM_FQ -> {
                    }
                    Status.GET_TEMPERATURE -> {
                    }

                    Status.UPDATE_VERSION -> {
                        if (reponseHub.message != null) {
                            Log.d("update", reponseHub.message!!)
                            downloadUpdateApk(reponseHub.message!!, this)
                        }
                    }

                    Status.UPDATE_MUSIC -> {
                        if (reponseHub.message != null) {
                            Log.d("Update Music", reponseHub.message!!)
                            viewModel.getAudioListFromNetwork(
                                applicationContext,
                                reponseHub.message!!
                            )

                            audioList = viewModel.getAudioList(applicationContext)
                        }
                    }


                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun pingHub(isUpdate: Boolean) {
        try {
            /*if (!isUpdate) {
                if (count_ping_hub > 1) {
                    if (hubConnection == null) {
                        connectHub()
                    } else {
                        hubConnection!!.start()
                    }
                }
            }*/
            if (!this@MainActivity.isFinishing && hubConnection != null) {
                // send ping_hub || update_status
                //  val date: String = simpleDateFormat.format(Date())
                Log.d("test", "ping hub")
                var request: PingHubRequest? = null

                val i = viewModel.playlistIndex
                Log.d("player:", java.lang.String.valueOf(i))
                //if (i >= 0 && i < mainViewModel.lstLiveData.getValue().size()) {

                var mode = "-1"

                if (!viewModel.playlist.value.isNullOrEmpty()) {
                    val path = viewModel.playlist.value!![i].path!!
                    mode = if (path.isNotEmpty() && !File(path).exists()) "1"
                    else "0"
                }

                val video = Video("" + i, mode)

                val videoAdapter = moshi.adapter(Video::class.java)

                request = PingHubRequest().apply {
                    imei = Utils.getDeviceId(applicationContext)
                    status = "START"
                    connectionId = (hubConnection!!.connectionId)
                    this.video = videoAdapter.toJson(video)
                    val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    startTine = dateformat.format(Date())
                }


                //count_ping_hub = count_ping_hub + 1
                val requestAdater = moshi.adapter(PingHubRequest::class.java)
                sendMessage(requestAdater.toJson(request), Utils.ping)
                Log.d("request", requestAdater.toJson(request))
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()

        }
    }

    private fun pingTimer() {
        pingHubJob?.cancel()
        pingHubJob = lifecycleScope.launchWhenResumed {
            while (true) {
                pingHub(true)
                Log.d("pingtimer", "ping")
                delay(15000)

                val dateformat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                if (lastPing != "") {
                    try {

                        val now = Calendar.getInstance()
                        val lastPingTime = Calendar.getInstance().apply {
                            time = dateformat.parse(lastPing)
                        }

                        if (now.timeInMillis - lastPingTime.timeInMillis > 45000) {
                            Log.e("reconnecet", "Losed hub connection")

                            if (hubConnection != null)
                                hubConnection!!.stop()

                            connectToHub()
                        }
                    } catch (e: Exception) {
                        Log.e("reconnecet", e.message)
                        e.printStackTrace()
                    }
                }
            }
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
}