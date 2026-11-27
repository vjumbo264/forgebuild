package com.motionsalt.ccstudio.ui.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.PackSave
import com.motionsalt.ccstudio.data.ProjectData
import com.motionsalt.ccstudio.data.ProjectRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val MIME="application/octet-stream"
private const val DIR="MOTIONSALT/CC Studio"
private data class Payload(val filename:String,val bytes:ByteArray,val stem:String,val proof:ByteArray,val tune:ByteArray)
private enum class Mode(val title:String) { NEW("Create new"), ADD("Add to last export"), MERGE("Merge imported plugin") }

@Composable
fun ExportStage(project: ProjectData, repository: ProjectRepository) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val prefs=remember{ context.getSharedPreferences("cc_plugin_export",Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(Mode.NEW) }
    var imported by remember { mutableStateOf<Uri?>(null) }
    var folder by remember { mutableStateOf(prefs.getString("folder",null)?.let(Uri::parse)) }
    var selectedName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<Pair<Uri,String>?>(null) }
    var collision by remember { mutableStateOf<Pair<CcArchive.Pack,CcPlugin.Look>?>(null) }
    var pending by remember { mutableStateOf<Payload?>(null) }
    val local = remember { File(context.filesDir,"latest_cc_plugin.kfplug") }
    val pickImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        imported=uri
        selectedName=uri?.let { nameOf(context,it) }.orEmpty()
    }
    val pickFolder=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { folder=it;PackSave.persistGrant(context,it);prefs.edit().putString("folder",it.toString()).apply() }
    }
    fun record(payload:Payload,uri:Uri) {
        local.writeBytes(payload.bytes)
        val name=nameOf(context,uri)
        project.exportHistory.add(name)
        repository.saveProject(project)
    }
    fun verify(payload:Payload,uri:Uri) {
        check(nameOf(context,uri).endsWith(".kfplug")) { "Storage provider did not preserve the .kfplug extension" }
        val read=readPlugin(context,uri)
        check(read.contentEquals(payload.bytes)) { "Saved plugin differs from the generated archive" }
        CcArchive.inspect(read)
    }
    val pickFile=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val payload=pending
        val uri=result.data?.data
        pending=null
        if(uri!=null && payload!=null) scope.launch {
            busy=true
            runCatching { withContext(Dispatchers.IO) {
                PackSave.writeToUri(context,uri,payload.bytes);verify(payload,uri)
                val privateProof=File(context.filesDir,"cc_proof").apply{mkdirs()}
                File(privateProof,"${payload.stem}.png").writeBytes(payload.proof)
                File(privateProof,"${payload.stem}.json").writeBytes(payload.tune)
                record(payload,uri)
            } }.onSuccess{ success=uri to "Proof PNG and tune JSON saved in app storage." }
                .onFailure{ message=it.message?:"Could not save plugin" }
            busy=false
        }
    }
    fun publish(look:CcPlugin.Look,base:CcArchive.Pack?,policy:CcArchive.Collision) {
        scope.launch {
            busy=true;message=null
            runCatching { withContext(Dispatchers.IO) {
                val version=max(System.currentTimeMillis().div(1000).toInt(),1)
                val bytes=if(base==null) CcPlugin.zip(CcPlugin.entries(listOf(look),version)) else checkNotNull(CcArchive.add(base,look,policy))
                val pack=CcArchive.inspect(bytes)
                val filename=pack.manifest.getString("id")+".kfplug"
                val payload=Payload(filename,bytes,"${look.id}-proof",proof(context,project,look),JSONObject(look.values).toString(2).encodeToByteArray())
                val tree=folder
                if(tree!=null || Build.VERSION.SDK_INT>=29) {
                    fun save(name:String,mime:String,data:ByteArray):Uri {
                        if(tree!=null) {
                            val target=checkNotNull(PackSave.createInTree(context,tree,name,mime)) { "Cannot create a file in the selected folder" }
                            PackSave.writeToUri(context,target,data);return target
                        }
                        return saveDownload(context,name,mime,data)
                    }
                    val uri=save(payload.filename,MIME,payload.bytes);verify(payload,uri)
                    save("${payload.stem}.png","image/png",payload.proof)
                    save("${payload.stem}.json","application/json",payload.tune)
                    record(payload,uri)
                    payload to uri
                } else payload to null
            } }.onSuccess { (payload,uri) ->
                if(uri!=null) success=uri to (if(folder==null) "Saved to Downloads/$DIR with proof PNG and tune JSON." else "Saved in your selected folder with proof PNG and tune JSON.")
                else { pending=payload;pickFile.launch(PackSave.createDocumentIntent(payload.filename,MIME)) }
            }.onFailure { message=it.message?:"Could not create plugin" }
            busy=false
        }
    }
    fun start() {
        scope.launch {
            busy=true;message=null
            runCatching { withContext(Dispatchers.IO) {
                val path=checkNotNull(project.fitResult?.modelPath) { "Fit the look first" }
                val model=ParametricMatch.fromJson(JSONObject(File(path).readText()))
                val id=project.lookName.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]+"),"-").trim('-').take(32).ifBlank { "cc-look" }
                val look=CcPlugin.Look(id,project.lookName,model,project.ccValues)
                val base=when(mode) {
                    Mode.NEW -> null
                    Mode.ADD -> CcArchive.inspect(local.readBytes())
                    Mode.MERGE -> CcArchive.inspect(readPlugin(context,checkNotNull(imported)))
                }
                look to base
            } }.onSuccess { (look,base) ->
                if(base!=null && look.id in base.effectIds) collision=base to look else publish(look,base,CcArchive.Collision.CANCEL)
            }.onFailure { message=it.message?:"Could not read the look" }
            busy=false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Export Keyflaw plugin (.kfplug)",style=MaterialTheme.typography.titleLarge)
        Text("One effect per look. Colour Match, Clarity, Structure, AE Unsharp Mask and Haze Flare are controls inside it.")
        for(option in Mode.entries) Row {
            RadioButton(selected=mode==option,onClick={mode=option},enabled=!busy && (option!=Mode.ADD || local.isFile))
            Text(option.title,Modifier.padding(top=12.dp))
        }
        if(mode==Mode.MERGE) OutlinedButton(onClick={pickImport.launch(arrayOf("*/*"))},enabled=!busy) { Text(selectedName.ifBlank { "Select .kfplug" }) }
        Text(if(folder==null) "Downloads/$DIR" else "Custom save folder")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={pickFolder.launch(PackSave.openTreeIntent())},enabled=!busy) { Text("Choose folder") }
            if(folder!=null) TextButton(onClick={folder=null;prefs.edit().remove("folder").apply()},enabled=!busy){Text("Use Downloads")}
        }
        Button(onClick={start()},enabled=!busy && (mode!=Mode.MERGE || imported!=null)) { Text("Export plugin") }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{ Text(it,color=MaterialTheme.colorScheme.error) }
    }
    collision?.let { (pack,look) ->
        AlertDialog(onDismissRequest={collision=null},title={Text("A look with this ID already exists")},text={Text(look.id)},
            confirmButton={TextButton(onClick={collision=null;publish(look,pack,CcArchive.Collision.REPLACE)}){Text("Replace")}},
            dismissButton={Row {
                TextButton(onClick={collision=null}){Text("Cancel")}
                TextButton(onClick={collision=null;publish(look,pack,CcArchive.Collision.KEEP_BOTH)}){Text("Keep both")}
            }})
    }
    success?.let { (uri,where) ->
        AlertDialog(onDismissRequest={success=null},title={Text("Plugin exported")},text={Text(where)},
            confirmButton={TextButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share .kfplug"))}){Text("Share")}},
            dismissButton={Row {
                TextButton(onClick={success=null}){Text("Done")}
                TextButton(onClick={runCatching {
                    val tree=folder
                    if(tree==null) context.startActivity(Intent("android.intent.action.VIEW_DOWNLOADS"))
                    else context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(tree,"vnd.android.document/directory").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure{success=null;message="Open your Files app to view the export folder."}}){Text("Open folder")}
            }})
    }
}

private fun nameOf(context:Context,uri:Uri):String = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)
    ?.use { if(it.moveToFirst()) it.getString(0) else null }.orEmpty()

private fun readPlugin(context:Context,uri:Uri):ByteArray = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
    val out= ByteArrayOutputStream(); val buffer=ByteArray(16384)
    while(true) {
        val n=input.read(buffer)
        if(n<0)break
        require(out.size()+n<=CcArchive.MAX_ZIP) { "Plugin exceeds the 2 MB limit" }
        out.write(buffer,0,n)
    }
    out.toByteArray()
}

@androidx.annotation.RequiresApi(29)
private fun saveDownload(context:Context,name:String,mime:String,data:ByteArray):Uri {
    val resolver=context.contentResolver
    val values=ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/$DIR")
        put(MediaStore.MediaColumns.IS_PENDING,1)
    }
    val uri=checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
    try {
        PackSave.writeToUri(context,uri,data)
        resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
        return uri
    } catch(e:Exception) { resolver.delete(uri,null,null);throw e }
}

private fun proof(context:Context,project:ProjectData,look:CcPlugin.Look):ByteArray {
    val pair=checkNotNull(project.pairs.firstOrNull { it.isComplete })
    val prepared=File(context.filesDir,"projects/${project.id}/results/before_${pair.id}.png")
    val original=checkNotNull(BitmapFactory.decodeFile(if(prepared.isFile) prepared.path else checkNotNull(pair.base).filePath)) { "Cannot read proof source" }
    val factor=min(1.0,560.0/max(original.width,original.height))
    val w=max(1,(original.width*factor).roundToInt());val h=max(1,(original.height*factor).roundToInt())
    val source=Bitmap.createScaledBitmap(original,w,h,true)
    val argb=IntArray(w*h);source.getPixels(argb,0,w,0,0,w,h)
    if(source!==original)source.recycle();original.recycle()
    fun v(id:String)=look.values[id]?:CcPlugin.initial.getValue(id)
    fun on(id:String)=v(id)>=.5
    val data=FloatArray(w*h*4)
    for (y in 0 until h) for(x in 0 until w) {
        val c=argb[(h-1-y)*w+x].let { if(on("matchOn")) look.model.applyArgb(it,v("matchStrength")) else it }
        val at=(y*w+x)*4
        val alpha=Color.alpha(c)/255f
        data[at]=Color.red(c)/255f*alpha;data[at+1]=Color.green(c)/255f*alpha;data[at+2]=Color.blue(c)/255f*alpha;data[at+3]=alpha
    }
    var frame=CcFinishing.detail(CcFrame(w,h,data),if(on("clarityOn"))v("clarity") else 0.0,if(on("structureOn"))v("structure") else 0.0)
    if(on("usmOn")) frame=CcFinishing.unsharp(frame,v("usmAmount"),v("usmRadius"),v("usmThreshold"))
    if(on("hazeOn")) frame=CcFinishing.haze(frame,CcHaze(v("hazeIntensity"),v("hazeThreshold"),v("hazeOpacity"),v("hazeSpread"),v("hazeBand"),v("hazeSide"),v("hazeCenter"),v("hazeGain"),v("hazeMode").toInt()))
    for(y in 0 until h)for(x in 0 until w) {
        val at=(y*w+x)*4;val a=frame.rgba[at+3]
        fun channel(i:Int)=(255*frame.rgba[at+i]/a.coerceAtLeast(.0001f)).roundToInt().coerceIn(0,255)
        argb[(h-1-y)*w+x]=Color.argb((a*255).roundToInt(),channel(0),channel(1),channel(2))
    }
    val result=Bitmap.createBitmap(argb,w,h,Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().use { out -> check(result.compress(Bitmap.CompressFormat.PNG,100,out));result.recycle();out.toByteArray() }
}
