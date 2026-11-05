package com.motionsalt.ccstudio.ui.export

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import com.forgebuild.engine.files.SafeSave
import com.motionsalt.ccstudio.core.*
import com.motionsalt.ccstudio.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*

enum class PackMode { ADD_TO_EXISTING,CREATE_NEW,MERGE_IMPORTED }
@Composable
fun ExportStage(project:ProjectData,repository:ProjectRepository,effectRepository:EffectRepository) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val available=remember{effectRepository.getAvailableEffects()}
    var mode by remember{mutableStateOf(PackMode.ADD_TO_EXISTING)}
    var name by remember{mutableStateOf(project.lookName)};var suite by remember{mutableStateOf("MOTIONSALT")}
    var imported by remember{mutableStateOf<ByteArray?>(null)};var status by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    var collision by remember{mutableStateOf(false)};var pending by remember{mutableStateOf<ByteArray?>(null)}
    var policy by remember{mutableStateOf(CollisionPolicy.REPLACE)};var saveProof by remember{mutableStateOf(false)};var saveJson by remember{mutableStateOf(false)}
    var tree by remember{mutableStateOf<Uri?>(null)};var saved by remember{mutableStateOf<Uri?>(null)}
    val local=File(context.filesDir,"packs/MOTIONSALT.kfpack")
    fun displayName(uri:Uri):String=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0) else ""}?:""
    fun write(uri:Uri){val bytes=pending?:return
        scope.launch {busy=true;try{withContext(Dispatchers.IO){val actual=displayName(uri);require(actual.endsWith(".kfpack",true)){"Provider renamed the file to $actual. Choose a name ending .kfpack and retry."};check(SafeSave.writeToUri(context,uri,bytes)){"Could not save the pack"};val check=context.contentResolver.openInputStream(uri)?.use{PackBuilder.validatePack(it)}?:error("Cannot read saved pack");require(check.isValid){check.errors.joinToString()};local.parentFile!!.mkdirs();local.writeBytes(bytes)
            tree?.let{folder->if(saveJson){val out=SafeSave.createInTree(context,folder,"${name}_tune.json","application/json")?:error("Cannot create tune-state JSON");check(SafeSave.writeToUri(context,out,project.tuneState.toJsonString().toByteArray()))};if(saveProof){val pair=project.pairs.firstOrNull{it.isComplete};val proof=File(context.filesDir,"projects/${project.id}/results/recreated_${pair?.id}.png");require(proof.exists());val out=SafeSave.createInTree(context,folder,"${name}_match_proof.png","image/png")?:error("Cannot create match proof");check(SafeSave.writeToUri(context,out,proof.readBytes()))}}
        };saved=uri;project.exportHistory.add(uri.toString());repository.saveProject(project);status="Saved and read-back validated: ${displayName(uri)}"}catch(e:Exception){status=e.message}finally{busy=false}}
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r->if(r.resultCode==Activity.RESULT_OK)r.data?.data?.let{write(it)}}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r->if(r.resultCode==Activity.RESULT_OK)r.data?.data?.let{uri->tree=uri;SafeSave.persistGrant(context,uri)}}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{try{val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.use{it.readBytes()}};val v=PackBuilder.validatePack(ByteArrayInputStream(bytes));require(v.isValid){v.errors.joinToString()};imported=bytes;status="Imported ${v.suiteInfo?.name}: ${v.effectIds.size} effects"}catch(e:Exception){status=e.message}}}
    fun export(chosen:CollisionPolicy){scope.launch{busy=true;try{val bytes=withContext(Dispatchers.IO){
        val fit=project.fitResult?:error("Run Fit first")
        val model=ParametricMatch.fromJson(JSONObject(File(fit.modelPath).readText()))
        val defs=project.tuneState.stack.filter{it.enabled}.mapIndexed{i,item->
            if(item.effectId=="colour_match")MatchEffect.definition(model,name,"look_"+project.id.replace("-", ""),item.params)
            else {val original=available.firstOrNull{it.id==item.effectId}?:error("Missing original effect: ${item.effectId}")
                val meta=JSONObject(context.assets.open("effects/${item.effectId}/effect.json").bufferedReader().use{it.readText()})
                require(!meta.has("passes")){"${original.name} needs retained-input multipass export calibration; it cannot be exported faithfully by the public plugin host yet. Disable it for export."}
                original.copy(id="${original.id}_$i",category="MOTIONSALT",parameters=original.parameters.map{p->p.copy(defaultValue=item.params[p.key]?:p.defaultValue)})}
        }
        require(defs.isNotEmpty()){ "Enable at least one stack effect" }
        val existing=when(mode){PackMode.CREATE_NEW->null;PackMode.ADD_TO_EXISTING->local.takeIf{it.exists()}?.readBytes();PackMode.MERGE_IMPORTED->imported?:error("Import a pack first")}
        val out=ByteArrayOutputStream();if(existing==null)PackBuilder.buildPack(SuiteInfo(name=suite),defs,out)else PackBuilder.mergePacks(ByteArrayInputStream(existing),defs,chosen,out)
        val result=out.toByteArray();val validation=PackBuilder.validatePack(ByteArrayInputStream(result));require(validation.isValid){validation.errors.joinToString()};result
    };pending=bytes;tree?.let{uri->val out=SafeSave.createInTree(context,uri,"${suite}.kfpack","application/octet-stream")?:error("Cannot create pack in selected folder");write(out)}?:save.launch(SafeSave.createDocumentIntent("${suite}.kfpack","application/octet-stream"))
    }catch(e:Exception){status=e.message}finally{busy=false}}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Export Keyflaw pack",style=MaterialTheme.typography.titleLarge)
        Text("MOTIONSALT is pinned. The fitted maths is embedded in shader.glsl, not a lookup file.")
        PackMode.entries.forEach{p->FilterChip(mode==p,{mode=p},label={Text(when(p){PackMode.ADD_TO_EXISTING->"Add to MOTIONSALT";PackMode.CREATE_NEW->"Create new pack";PackMode.MERGE_IMPORTED->"Merge imported pack"})})}
        if(mode==PackMode.MERGE_IMPORTED)OutlinedButton(onClick={importer.launch(arrayOf("*/*"))}){Text("Import .kfpack")}
        OutlinedTextField(name,{name=it},label={Text("Effect name")},modifier=Modifier.fillMaxWidth())
        if(mode==PackMode.CREATE_NEW)OutlinedTextField(suite,{suite=it},label={Text("Pack name")})
        Text("Expose controls in Keyflaw: defaults are your tuned values. Control-free export is not yet verified for original multi-pass graphs.")
        project.tuneState.stack.forEach{item->Row{Checkbox(item.expose,{item.expose=it;repository.saveProject(project)});Text(available.firstOrNull{it.id==item.effectId}?.name?:item.effectId)}}
        OutlinedButton(onClick={folder.launch(SafeSave.openTreeIntent())}){Text(if(tree==null)"Change folder (user-selected)" else "Selected folder: ${tree?.lastPathSegment}")}
        Text("Suggested folder: MOTIONSALT/CC Studio. Android's file picker owns the destination; no broad storage permission is needed.")
        Row{Checkbox(saveProof,{saveProof=it});Text("Also save colour-match proof PNG (before added stack effects)")}
        Row{Checkbox(saveJson,{saveJson=it});Text("Also save tune-state JSON")}
        if((saveProof||saveJson)&&tree==null)Text("Select a folder to save these optional extra files.")
        Button(enabled=!busy&&project.fitResult!=null&&name.isNotBlank(),onClick={if(mode!=PackMode.CREATE_NEW&&(imported!=null||local.exists()))collision=true else export(policy)}){Text(if(busy)"Exporting…" else "Export .kfpack")}
        status?.let{Text(it)}
        saved?.let{uri->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share pack"))}.onFailure{status=it.message}}){Text("Share")};OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{status="No app registered for this pack; import it from Keyflaw."}}){Text("Open in Keyflaw")}}}
    }
    if(collision)AlertDialog(onDismissRequest={collision=false},title={Text("Effect ID collision policy")},text={Column{Text("Apply to duplicate IDs only; other existing effects are preserved.");TextButton(onClick={collision=false;policy=CollisionPolicy.KEEP_BOTH_NEW_ID;export(policy)}){Text("Keep both with new ID")}}},confirmButton={TextButton(onClick={collision=false;policy=CollisionPolicy.REPLACE;export(policy)}){Text("Replace matching IDs")}},dismissButton={TextButton(onClick={collision=false}){Text("Cancel")}})
}
