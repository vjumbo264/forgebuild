package com.motionsalt.ccstudio.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.forgebuild.engine.files.SafeSave

/** Compose launch-intent adapter; all bytes are written through the existing Engine SafeSave helper. */
object PackSave {
    fun createDocumentIntent(name:String,mime:String)=Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    fun openTreeIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    fun persistGrant(context:Context,uri:Uri)=SafeSave.takePersistablePermission(context,uri)
    fun writeToUri(context:Context,uri:Uri,bytes:ByteArray):Boolean { SafeSave.writeBytes(context,uri,bytes);return true }
    fun createInTree(context:Context,tree:Uri,name:String,mime:String):Uri? {
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(context.contentResolver,parent,mime,name)
    }
}
