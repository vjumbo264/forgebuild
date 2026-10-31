package com.forgebuild.otgenabler

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OtgTileService : TileService() {
    private lateinit var controller: OtgController

    override fun onCreate() {
        super.onCreate()
        controller = OtgController(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentEnabled = controller.isOtgEnabled.value
        CoroutineScope(Dispatchers.IO).launch {
            if (currentEnabled) {
                controller.disableOtg()
            } else {
                controller.enableOtg()
            }
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = controller.isOtgEnabled.value
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "USB OTG"
        tile.subtitle = if (enabled) "Enabled (Host)" else "Disabled"
        tile.updateTile()
    }
}
