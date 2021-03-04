package app.tek4tv.digitalsignage.media

import java.lang.ref.WeakReference

class PlayerManagerHolder {
    companion object {
        private var playerManagerRef = WeakReference<PlayerManager>(null)

        fun updatePlayerManager(pm: PlayerManager) {
            playerManagerRef = WeakReference(pm)
        }

        fun getPlayerManager() = playerManagerRef.get()
    }
}