package app.tek4tv.digitalsignage.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import app.tek4tv.digitalsignage.HubManager
import app.tek4tv.digitalsignage.R
import app.tek4tv.digitalsignage.media.PlayerManager
import app.tek4tv.digitalsignage.model.*
import app.tek4tv.digitalsignage.utils.*
import app.tek4tv.digitalsignage.viewmodels.MainViewmodel
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.videolan.libvlc.*
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity()
{

    private val UPDATE_PERMISSION_REQUEST_CODE = 1
    private val PREF_ORIENTATION = "pref_orientation"

    private val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_SETTINGS
    )

    private lateinit var mVideoLayout: VLCVideoLayout

    private val viewModel by viewModels<MainViewmodel>()

    @Inject
    lateinit var moshi: Moshi

    private lateinit var hubManager: HubManager
    private lateinit var playerManager: PlayerManager
    private lateinit var audioManager: AudioManager

    private lateinit var preference: SharedPreferences
    var d = 0.0f
    var i = 0

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // TODO : add crash handler to app when building for customer
        //Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))

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
    }

    override fun onStop()
    {
        super.onStop()
        playerManager.onActivityStop()
    }

    override fun onDestroy()
    {
        super.onDestroy()
        playerManager.onActivityDestroy()
    }

    override fun onStart()
    {
        super.onStart()
        playerManager.attachVisualPlayerView()
        startPlayingMedia()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    )
    {
        when (requestCode)
        {
            UPDATE_PERMISSION_REQUEST_CODE ->
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                )
                {
                } else
                {
                }
            }
        }
    }

    private fun needGrantPermission(): Boolean
    {
        var needGrantPermission = false

        permissions.forEach {
            if (checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED)
            {
                needGrantPermission = true
                return@forEach
            }
        }

        return needGrantPermission
    }

    private fun requestAppPermission()
    {
        requestPermissions(permissions, UPDATE_PERMISSION_REQUEST_CODE)
    }


    private fun registerObservers()
    {
        viewModel.playlist.observe(this)
        {
            playerManager.setPlaylistContent(it, viewModel.getAudioList(applicationContext))

            playerManager.checkScheduledMedia()

            viewModel.downloadMedias(applicationContext)


            /*if (viewModel.playlist.value != null && viewModel.playlistIndex >= viewModel.playlist.value!!.size)
                viewModel.playlistIndex = 0
            playerManager.playMediaByIndex(viewModel.playlistIndex)*/
        }
    }

    private fun startPlayingMedia()
    {
        viewModel.getPlaylist(applicationContext, false)
        viewModel.checkPlaylist(applicationContext)
    }


    private fun initHubConnect()
    {
        NetworkUtils.instance.mNetworkLive.observe(this)
        { isConnected ->
            if (isConnected)
            {

                if (!viewModel.isPlaying)
                    startPlayingMedia()

                if (hubManager.hubConnection == null)
                {
                    hubManager.createNewHubConnection()
                    Log.d("Connected", "Connected")
                }
            } else
            {
                hubManager.hubConnection = null
            }

        }
    }

    private fun playMediaItemByIndex(index: Int)
    {
        viewModel.playlistIndex = index
        playerManager.playMediaByIndex(viewModel.playlistIndex)
    }

    private fun handleFromCommandServer(command: String?, message: String?)
    {
        try
        {
            if (command == null || command.isEmpty())
            {
                return
            }
            if (message == null || message.isEmpty())
            {
                return
            }
            //count_ping_hub = 0
            var responseHub = ReponseHub()
            if (message.startsWith("{"))
            {
                val jsonAdapter = moshi.adapter(ReponseHub::class.java)
                responseHub = jsonAdapter.fromJson(message)!!
            }

            val isImei = Utils.getDeviceId(applicationContext) == responseHub.imei
            if (isImei)
            {
                when (command)
                {
                    Status.GET_LIST ->
                    {
                    }
                    Status.UPDATE_LIST ->
                    {
                        viewModel.playlistIndex = 0
                        viewModel.getPlaylist(applicationContext, true)
                    }
                    Status.NEXT ->
                    {
                    }
                    Status.PREVIEW ->
                    {
                    }
                    Status.JUMP ->
                    {
                        Log.d(command, message)
                        val id = responseHub.message!!.trim().toInt()
                        playMediaItemByIndex(id)
                    }
                    Status.LIVE ->
                    {
                        /*volume = reponseHub.getVolume()
                        playURLVideo(reponseHub.getMessage().trim(), false)*/
                    }
                    Status.UPDATE_STATUS -> hubManager.pingHub(true)
                    Status.GET_LOCATION ->
                    {
                    }
                    Status.SET_VOLUME ->
                    {
                    }
                    Status.STOP ->
                    {
                    }
                    Status.PAUSE ->
                    {
                    }
                    Status.START ->
                    {
                    }
                    Status.RESTART ->
                    {
                    }
                    Status.RELOAD ->
                    {

                    }
                    Status.SWITCH_MODE_FM ->
                    {
                    }
                    Status.SET_MUTE_DEVICE ->
                    {
                        if (responseHub.message != null)
                            setMute(responseHub.message!!)
                    }
                    Status.SET_VOLUME_DEVICE ->
                    {
                        if (responseHub.message != null)
                            setVolume(responseHub.message!!)
                    }
                    Status.GET_VOLUME_DEVICE ->
                    {
                    }
                    Status.GET_SOURCE_AUDIO ->
                    {
                    }
                    /*Status.GET_PA -> {
                        deviceAdrress = Define.Power_Amplifier_R
                        writeToDevice(buildReadMessageNew(Define.FUNC_WRITE_READ_DEVICE_INFO, "6"))
                    }*/
                    Status.GET_FM_FQ ->
                    {
                    }
                    Status.GET_AM_FQ ->
                    {
                    }
                    Status.GET_TEMPERATURE ->
                    {
                    }

                    Status.UPDATE_VERSION ->
                    {
                        if (responseHub.message != null)
                        {
                            Log.d("update", responseHub.message!!)
                            downloadUpdateApk(responseHub.message!!, this)
                        }
                    }

                    Status.UPDATE_MUSIC ->
                    {
                        if (responseHub.message != null)
                        {
                            Log.d("Update Music", responseHub.message!!)
                            viewModel.getAudioListFromNetwork(
                                    applicationContext,
                                    responseHub.message!!
                            )

                            playerManager.audioList = viewModel.getAudioList(applicationContext)
                        }
                    }
                    Status.ROTATION ->
                    {


                        if (responseHub.message != null)
                        {
                            Log.d("rotation", responseHub.message.toString())
                            try
                            {
                                val requireOrientation = responseHub.message!!.toInt()
                                preference.edit {
                                    putInt(PREF_ORIENTATION, requireOrientation)
                                }

                                playerManager.switchViewOrientation(requireOrientation)

                                //setScreenOrientation(getOrientation(requireOrientation))
                            } catch (e: Exception)
                            {
                                Log.e("orientation", "Error parsing orientation")
                            }
                        }

                    }


                }
            }
        } catch (e: java.lang.Exception)
        {
            e.printStackTrace()
        }
    }

    private fun setMute(mute: String)
    {
        val direction = if (mute == "1") AudioManager.ADJUST_MUTE
        else AudioManager.ADJUST_UNMUTE

        audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
        )
    }

    private fun setVolume(message: String)
    {
        var volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        var maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        try
        {
            volume = ((maxVolume.toFloat() / 100) * message.toInt()).toInt()
        } catch (e: NumberFormatException)
        {

        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
    }

    fun getOrientation(requestOri: Int): Int
    {
        return when (requestOri)
        {
            1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            3 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            4 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    fun testOrientation()
    {
        val mParentLayout = findViewById(R.id.video_layout) as View
        mParentLayout.rotation = d
        val lp = ConstraintLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        mParentLayout.layoutParams = lp


        /* val displayMetrics = DisplayMetrics()
         getWindowManager().getDefaultDisplay().getMetrics(displayMetrics)

         val height = displayMetrics.heightPixels
         val width = displayMetrics.widthPixels
         val offset = (width - height) / 2
         val lp = FrameLayout.LayoutParams(height, width)
         mParentLayout.layoutParams = lp
         mParentLayout.rotation = 180.0f
         mParentLayout.translationX = offset.toFloat()
         mParentLayout.translationY = -offset.toFloat()*/
    }
}