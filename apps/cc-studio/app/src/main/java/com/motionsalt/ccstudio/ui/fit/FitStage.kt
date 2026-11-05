package com.motionsalt.ccstudio.ui.fit

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.motionsalt.ccstudio.core.ColourMetrics
import com.motionsalt.ccstudio.data.*
import com.motionsalt.ccstudio.service.FitService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun FitStage(project:ProjectData,repository:ProjectRepository,onContinueToTune:()->Unit) {
    val context=LocalContext.current
    val serviceStatus by FitService.state.collectAsState()
    val status=serviceStatus.takeIf{it.projectId==project.id}
    var result by remember(project.id){mutableStateOf(project.fitResult)}
    var selected by remember{mutableIntStateOf(0)}
    var explain by remember{mutableStateOf(false)}
    var bitmap by remember{mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(status?.result){status?.result?.let{result=it;project.fitResult=it}}
    fun start(){context.startForegroundService(Intent(context,FitService::class.java).putExtra("projectId",project.id))}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){start()}
    val pair=project.pairs.firstOrNull{it.isComplete}
    val dir=File(context.filesDir,"projects/${project.id}/results")
    val names=listOf("Before","Target","Recreated","Heatmap")
    val file=File(dir,"${listOf("before","target","recreated","heatmap")[selected]}_${pair?.id}.png")
    LaunchedEffect(file.absolutePath,result?.timestamp){bitmap=withContext(Dispatchers.IO){if(file.exists())BitmapFactory.decodeFile(file.absolutePath)else null}}
    fun n(v:Float)=String.format(Locale.US,"%.2f",v)
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Direct Colour Match",style=MaterialTheme.typography.titleLarge)
        Text("Linear-light matrix, monotone tone curves and colour-selective hue correction. Entirely offline.")
        if(status?.running==true) {
            Text(status.message);LinearProgressIndicator(progress={status.step/10f},modifier=Modifier.fillMaxWidth())
            Button(onClick={context.startService(Intent(context,FitService::class.java).setAction(FitService.CANCEL))}){Text("Cancel")}
        } else {
            status?.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            result?.let { r->
                Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(ColourMetrics.qualityLabel(r.meanDeltaE.toDouble()),style=MaterialTheme.typography.titleMedium)
                    Text("CIEDE2000 mean ${n(r.meanDeltaE)} · p95 ${n(r.p95DeltaE)}")
                    Text("Identity mean ${n(r.identityMean)} · p95 ${n(r.identityP95)}")
                    if(r.aligned)Text("Held-out right 20% mean ${n(r.holdoutMean)} · colour-only mean ${n(r.blurredMean)}")
                    else Text("Different scenes: distribution comparison, not aligned pixel accuracy or held-out spatial validation.")
                }}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){names.forEachIndexed{i,name->FilterChip(selected==i,{selected=i},label={Text(name)},modifier=Modifier.weight(1f))}}
                bitmap?.let{Image(it.asImageBitmap(),names[selected],Modifier.fillMaxWidth().weight(1f),contentScale=ContentScale.Fit)}?:Text("No saved proof for this pair; run Fit again.")
                if(selected==3)Text("Error scale: dark purple 0 → orange 7.5 → pale yellow ≥15 ΔE. Ignored borders are black.")
                Button(onClick=onContinueToTune,modifier=Modifier.fillMaxWidth()){Text("Continue to Tune")}
            }
            Button(enabled=pair!=null,onClick={
                val prefs=context.getSharedPreferences("fit_permissions",0)
                if(Build.VERSION.SDK_INT>=33&&!prefs.getBoolean("notificationExplained",false))explain=true else start()
            },modifier=Modifier.fillMaxWidth()){Text(if(result==null)"Fit look" else "Re-fit look")}
        }
    }
    if(explain)AlertDialog(onDismissRequest={explain=false},title={Text("Background fit progress")},text={Text("Notifications show matching progress while this app is in the background. Denying permission does not prevent fitting.")},confirmButton={TextButton(onClick={explain=false;context.getSharedPreferences("fit_permissions",0).edit().putBoolean("notificationExplained",true).apply();if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)else start()}){Text("Continue")}},dismissButton={TextButton(onClick={explain=false;start()}){Text("Skip")}})
}
