package com.motionsalt.ccstudio.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

object CcArchive {
    const val MAX_ZIP = 2 * 1024 * 1024
    private const val MAX_RAW = 8 * 1024 * 1024
    private val idPattern = Regex("[a-z0-9_-]{1,32}")
    private val safePath = Regex("[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)*")
    private val loop = Regex("\\bfor\\s*\\([^;]*;([^;]*);")
    @Suppress("RegExpRedundantEscape")
    private val forbidden = Regex("#\\s*(version|extension)|\\b(while|do)\\b\\s*[({]|\\btexture\\s*\\(")
    enum class Collision { REPLACE, KEEP_BOTH, CANCEL }
    data class Pack(val manifest: JSONObject,val entries: Map<String,ByteArray>,val effectIds: List<String>)
    fun inspect(zip: ByteArray): Pack {
        require(zip.size <= MAX_ZIP) { "Plugin exceeds the 2 MB ZIP limit" }
        val entries = linkedMapOf<String,ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = stream.nextEntry ?: break
                val name = entry.name
                require(safePath.matches(name.trimEnd('/')) && name.split('/').none { it == ".." || it == "." }) { "Invalid archive path" }
                if (entry.isDirectory) continue
                require(entries.size < 64 && name !in entries && name.substringAfterLast('.').lowercase() in setOf("json","frag","png","txt")) { "Invalid or duplicate archive file: $name" }
                val out = ByteArrayOutputStream()
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RAW) { "Plugin exceeds the 8 MB extracted limit" }
                    out.write(buffer,0,count)
                }
                entries[name] = out.toByteArray()
            }
        }
        fun json(name: String) = JSONObject(requireNotNull(entries[name]) { "Missing $name" }.decodeToString())
        val manifest = json("manifest.json")
        require(manifest.getInt("format")==1 && manifest.getInt("api")==1 && manifest.getInt("versionCode")>0)
        require(manifest.getString("id").split('.').let { it.size in 2..5 && it.all(idPattern::matches) })
        require(manifest.getString("name").isNotBlank() && manifest.getString("version").isNotBlank())
        val list = manifest.getJSONArray("effects")
        val ids = (0 until list.length()).map { list.getString(it) }
        require(ids.isNotEmpty() && ids.toSet().size==ids.size && ids.all(idPattern::matches))
        for (id in ids) {
            val base="effects/$id/"
            val effect = json(base+"effect.json")
            require(effect.getString("id")==id && effect.getString("name").isNotBlank())
            require(effect.getString("category") in setOf("Blur","Warna","Stilisasi","Distorsi","Gerak","Generator","Keying","Transisi"))
            val params=effect.optJSONArray("params")?:JSONArray()
            require(params.length()<=40)
            val keys=mutableSetOf<String>()
            for (i in 0 until params.length()) {
                val p=params.getJSONObject(i);val key=p.getString("id")
                require(Regex("[A-Za-z][A-Za-z0-9]{0,31}").matches(key) && keys.add(key))
                val default=p.getDouble("default")
                require(default.isFinite())
                when(p.getString("type")) {
                    "scalar" -> require(p.getDouble("min") < p.getDouble("max") && default in p.getDouble("min")..p.getDouble("max"))
                    "bool" -> require(default==0.0 || default==1.0)
                    "choice" -> require(p.getJSONArray("options").length()>=2 && default==default.toInt().toDouble() && default>=0 && default<p.getJSONArray("options").length())
                    else -> error("Unknown parameter type")
                }
            }
            val passes=effect.getJSONArray("passes");require(passes.length() in 1..8)
            for (i in 0 until passes.length()) {
                val pass=passes.getJSONObject(i)
                val name=pass.getString("shader")
                require(name.matches(Regex("[A-Za-z0-9_-]+\\.frag")))
                if (pass.has("prevPass")) require(pass.getInt("prevPass") in 0 until i)
                if (pass.has("requiredParam")) require(pass.getString("requiredParam") in keys)
                require(pass.optDouble("scale",1.0) in (0..8).map { 1.0/(1 shl it) })
                val source=requireNotNull(entries[base+name]) { "Missing shader $name" }
                require(source.size<=32768)
                val text=source.decodeToString()
                require("gl_FragColor" in text && !forbidden.containsMatchIn(text)) { "Shader does not meet API 1 rules" }
                for (m in loop.findAll(text)) require(Regex("[0-9]+$").find(m.groupValues[1].trim())?.value?.toIntOrNull()?.let { it <= 256 } == true)
            }
        }
        return Pack(manifest,entries,ids)
    }
    fun add(pack: Pack, look: CcPlugin.Look, collision: Collision): ByteArray? {
        var id=look.id
        if (id in pack.effectIds) when(collision) {
            Collision.CANCEL -> return null
            Collision.REPLACE -> Unit
            Collision.KEEP_BOTH -> {
                var counter=2
                do { val end="-${counter++}";id=look.id.take(32-end.length)+end } while(id in pack.effectIds)
            }
        }
        val version=pack.manifest.getInt("versionCode").let { require(it<Int.MAX_VALUE);it+1 }
        val fresh=CcPlugin.entries(listOf(look.copy(id=id)),version)
        val ids=if(id in pack.effectIds) pack.effectIds else pack.effectIds+id
        val manifest=JSONObject(pack.manifest.toString()).put("versionCode",version).put("version","1.0.$version").put("effects",JSONArray(ids))
        val merged=linkedMapOf("manifest.json" to manifest.toString(2).encodeToByteArray())
        merged.putAll(pack.entries.filterKeys { it!="manifest.json" && !it.startsWith("effects/$id/") })
        merged.putAll(fresh.filterKeys { it!="manifest.json" })
        return CcPlugin.zip(merged).also { inspect(it) }
    }
}
