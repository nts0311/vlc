package app.tek4tv.digitalsignage
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

class CustomPlayer(libVlc: LibVLC) : MediaPlayer(libVlc) {
    var eventListener: (Int) -> Unit = {}

    override fun onEventNative(
        eventType: Int,
        arg1: Long,
        arg2: Long,
        argf1: Float,
        args1: String?
    ): Event {
        val res = super.onEventNative(eventType, arg1, arg2, argf1, args1)
        eventListener.invoke(eventType)
        return res
    }

}