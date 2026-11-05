package com.motionsalt.ccstudio.ui.packs

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.motionsalt.ccstudio.data.PackSave as SafeSave
import com.motionsalt.ccstudio.core.PackBuilder
import com.motionsalt.ccstudio.data.EffectRepository
import java.io.File

@Composable
fun PacksScreen(effectRepository:EffectRepository) {
    val context=LocalContext.current;var status by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<File?>(null)};var revision by remember{mutableIntStateOf(0)}
    val packs=remember(revision){File(context.filesDir,"packs").listFiles()?.filter{it.extension=="kfpack"}?.sortedBy{if(it.name=="MOTIONSALT.kfpack")""else it.name}?:emptyList()}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->if(result.resultCode==Activity.RESULT_OK)result.data?.data?.let{uri->status=if(selected?.let{SafeSave.writeToUri(context,uri,it.readBytes())}==true)"Saved pack copy"else "Could not save pack copy"}}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)runCatching{val bytes=context.contentResolver.openInputStream(uri)!!.use{it.readBytes()};val check=PackBuilder.validatePack(bytes.inputStream());require(check.isValid){check.errors.joinToString()};val dir=File(context.filesDir,"packs").apply{mkdirs()};val id=check.suiteInfo!!.id.replace(Regex("[^A-Za-z0-9._-]"),"_");File(dir,"${id}_${System.currentTimeMillis()}.kfpack").writeBytes(bytes);revision++;status="Imported ${check.effectIds.size} effects"}.onFailure{status=it.message}}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Packs",style=MaterialTheme.typography.titleLarge)
        Text("MOTIONSALT is pinned. Create your fitted pack in a project's Export stage.")
        Button(onClick={importer.launch(arrayOf("*/*"))}){Text("Import and validate .kfpack")}
        packs.forEach{file->Card{Column(Modifier.fillMaxWidth().padding(12.dp)){Text(file.name);OutlinedButton(onClick={selected=file;save.launch(SafeSave.createDocumentIntent(file.name,"application/octet-stream"))}){Text("Save a copy")}}}}
        status?.let{Text(it)}
    }
}
