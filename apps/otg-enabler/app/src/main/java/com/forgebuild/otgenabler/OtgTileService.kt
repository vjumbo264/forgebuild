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
        val state = controller.verificationState.value
        when (state) {
            OtgVerificationState.VERIFIED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "USB OTG"
                tile.subtitle = "Verified (Host + VBUS)"
            }
            OtgVerificationState.PARTIAL -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "USB OTG"
                tile.subtitle = "Partial (No VBUS)"
            }
            OtgVerificationState.FAILED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "USB OTG"
                tile.subtitle = "Failed"
            }
            OtgVerificationState.DISABLED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "USB OTG"
                tile.subtitle = "Disabled"
            }
        }
        tile.updateTile()
    }
}
