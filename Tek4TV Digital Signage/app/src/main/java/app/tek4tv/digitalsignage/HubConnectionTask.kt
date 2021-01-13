package app.tek4tv.digitalsignage

import android.os.AsyncTask
import android.util.Log
import com.microsoft.signalr.HubConnection

class HubConnectionTask(private val hubCallBack: HubCallBack) :
    AsyncTask<HubConnection?, Void?, String?>() {
    private val LOG_TAG = "HubConnectionTask"

    override fun doInBackground(vararg hubConnections: HubConnection?): String? {
        return try {
            val hubConnection = hubConnections[0]
            hubConnection!!.start().blockingAwait()
            hubConnection!!.connectionId
        } catch (e: Exception) {
            Log.e(LOG_TAG, "error connecting tto hub")
            null
        }
    }

    override fun onPostExecute(aVoid: String?) {
        super.onPostExecute(aVoid)
        hubCallBack.onCallBack(aVoid)
    }
}