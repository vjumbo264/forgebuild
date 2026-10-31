package com.forgebuild.otgenabler

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forgebuild.engine.files.SafeSave
import com.forgebuild.engine.ui.theme.ForgeBuildTheme
import com.forgebuild.otgenabler.ui.OtgEnablerScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var pendingDiagnosticsText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val saveLauncher = SafeSave.registerCreateDocument(this, "text/plain") { uri ->
            if (uri != null && pendingDiagnosticsText.isNotEmpty()) {
                runCatching {
                    SafeSave.writeBytes(this, uri, pendingDiagnosticsText.toByteArray(Charsets.UTF_8))
                }.onSuccess {
                    Toast.makeText(this, "Diagnostics saved", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this, "Save failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        setContent {
            ForgeBuildTheme {
                OtgEnablerScreen(
                    activity = this@MainActivity,
                    onSaveDiagnosticsFile = { report ->
                        pendingDiagnosticsText = report
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        saveLauncher.launch("otg-enabler-diagnostics-$stamp.txt")
                    }
                )
            }
        }
    }
}
