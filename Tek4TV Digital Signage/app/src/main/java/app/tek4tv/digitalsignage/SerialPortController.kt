package app.tek4tv.digitalsignage

import android.content.Context
import android.util.Log
import app.tek4tv.digitalsignage.model.DirectMessage
import app.tek4tv.digitalsignage.utils.Define
import app.tek4tv.digitalsignage.utils.NetworkUtils
import app.tek4tv.digitalsignage.utils.Utils
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class SerialPortController(
    private val appContext: Context,
    private val coroutineScope: CoroutineScope,
    private val hubManager: HubManager,
    private val moshi: Moshi
) {
    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val UART_NAME = "/dev/ttyS4"

    fun connectToSerialPort() {
        try {
            initSerialPort()
            readDevice()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun initSerialPort() {
        serialPort = SerialPort(File(UART_NAME), 9600, 0)
        inputStream = serialPort!!.inputStream
        outputStream = serialPort!!.outputStream
        if (serialPort != null && inputStream != null && outputStream != null) {
            coroutineScope.launch {
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

                val onAir: Boolean = NetworkUtils.isNetworkConnected(appContext)
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
    fun buildWriteMessage(funtionId: String, data: String): String {
        val s = "$$,$funtionId,$data,\r\n"
        Log.d("requestdevice", s)
        return s
    }

    fun buildReadMessage(functionId: String, message: String): String {
        isRead = true
        val s = "$$,$functionId,$message,\r\n"
        Log.d("requestevice", s)
        return s
    }


    var dataFinal = StringBuffer()


    private fun readDevice() {
        coroutineScope.launch {

            withContext(Dispatchers.IO) {
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
                    val receiveMessage = DirectMessage(Utils.getDeviceId(appContext), data)
                    hubManager.sendDirectMessage(
                        hubManager.receivedConnectionId,
                        Utils.DEVICE_INFO,
                        Utils.toJsonString(moshi, DirectMessage::class.java, receiveMessage))
                    isRead = false
                }
            }
        }
    }

    fun writeToDevice(message: String) {
        if (outputStream != null) {
            try {
                outputStream!!.write(message.toByteArray())
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }
}