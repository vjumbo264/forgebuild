package com.motionsalt.ccstudio.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SuiteInfo(
    val id: String = "com.motionsalt.effects",
    val name: String = "MOTIONSALT",
    val author: String = "MOTIONSALT",
    val brand: String = "MOTIONSALT",
    val version: String = "1.0.0",
    val description: String = "Color grading and look suite for Keyflaw 1.4.9",
    val category: String = "MOTIONSALT"
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suiteInfo: SuiteInfo? = null,
    val effectIds: List<String> = emptyList()
)

enum class CollisionPolicy {
    REPLACE,
    KEEP_BOTH_NEW_ID,
    SKIP
}

object PackBuilder {
    const val MANDATORY_PRECISION_HEADER = """#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif"""

    fun ensurePrecisionHeader(source: String): String =
        if (source.trimStart().startsWith("#ifdef GL_FRAGMENT_PRECISION_HIGH")) source else "$MANDATORY_PRECISION_HEADER\n$source"

    private fun safe(path: String): Boolean =
        path.isNotBlank() && !path.startsWith("/") && !path.contains("\\") && path.split('/').none { it == ".." || it == "." }

    private fun id(id: String): Boolean = id.matches(Regex("[a-zA-Z0-9_-]+"))

    private fun read(input: InputStream): MutableMap<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                require(safe(entry.name)) { "Unsafe archive path" }
                if (!entry.isDirectory) {
                    require(!files.containsKey(entry.name)) { "Duplicate archive entry" }
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var n = zip.read(buffer)
                    while (n >= 0) {
                        out.write(buffer, 0, n)
                        total += n
                        require(out.size() <= 20 * 1024 * 1024 && total <= 100 * 1024 * 1024) { "Pack exceeds safety limits" }
                        n = zip.read(buffer)
                    }
                    files[entry.name] = out.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return files
    }

    private fun write(files: Map<String, ByteArray>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                require(safe(name))
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun effectJson(effect: EffectDefinition): JSONObject {
        val obj = JSONObject()
            .put("id", effect.id)
            .put("name", effect.name)
            .put("category", effect.category)
            .put("description", effect.description)
            .put("expandBounds", effect.expandBounds.toDouble())
        if (effect.passes.isNotEmpty()) {
            obj.put("passes", JSONArray(effect.passes))
        }
        val params = JSONArray()
        effect.parameters.forEach { p ->
            val value = if (p.defaultValue is Boolean) {
                if (p.defaultValue) 1.0 else 0.0
            } else p.defaultValue
            val v = JSONObject()
                .put("key", p.key)
                .put("label", p.label)
                .put("type", p.type.name)
                .put("default", value)
                .put("group", p.group)
            if (p.type != ParamType.COLOR) {
                v.put("min", p.min.toDouble())
                    .put("max", p.max.toDouble())
                    .put("step", p.step.toDouble())
            }
            if (p.type == ParamType.CHOICE && p.options.isNotEmpty()) {
                v.put("options", JSONArray(p.options))
            }
            params.put(v)
        }
        return obj.put("parameters", params)
    }

    fun prepareEffect(effect: EffectDefinition, suiteCategory: String = "MOTIONSALT"): EffectDefinition {
        val hasEnabled = effect.parameters.any { it.key == "enabled" }
        val params = if (hasEnabled) {
            effect.parameters
        } else {
            listOf(
                EffectParam(
                    key = "enabled",
                    label = "Enable ${effect.name}",
                    type = ParamType.BOOL,
                    defaultValue = 1.0,
                    min = 0f,
                    max = 1f,
                    step = 1f,
                    group = "General"
                )
            ) + effect.parameters
        }

        var shader = effect.shaderCode
        if (!shader.contains("uEnabled")) {
            val uniformDecl = "uniform float uEnabled;\n"
            val mainCheck = "\n    if (uEnabled <= 0.5) { gl_FragColor = texture2D(uTexture, vTex); return; }\n"
            if (shader.contains("uniform sampler2D uTexture;")) {
                shader = shader.replace("uniform sampler2D uTexture;", "uniform sampler2D uTexture;\n$uniformDecl")
            } else {
                shader = "$uniformDecl$shader"
            }
            val mainPattern = Regex("""void\s+main\s*\(\s*\)\s*\{""")
            val match = mainPattern.find(shader)
            if (match != null) {
                val insertIdx = match.range.last + 1
                shader = shader.substring(0, insertIdx) + mainCheck + shader.substring(insertIdx)
            }
        }

        return effect.copy(
            category = suiteCategory,
            parameters = params,
            shaderCode = shader
        )
    }

    private fun addEffect(files: MutableMap<String, ByteArray>, effect: EffectDefinition) {
        require(id(effect.id)) { "Unsafe effect id" }
        val ready = prepareEffect(effect)
        files["effects/${ready.id}/effect.json"] = effectJson(ready).toString(2).toByteArray(StandardCharsets.UTF_8)
        files["effects/${ready.id}/shader.glsl"] = ensurePrecisionHeader(ready.shaderCode).toByteArray(StandardCharsets.UTF_8)
        for (passName in ready.passes) {
            if (passName != "shader.glsl") {
                val passCode = ready.passShaders[passName]
                require(passCode != null) { "Missing pass file $passName for effect ${ready.id}" }
                files["effects/${ready.id}/$passName"] = ensurePrecisionHeader(passCode).toByteArray(StandardCharsets.UTF_8)
            }
        }
        ready.passShaders.forEach { (name, code) ->
            if (name != "shader.glsl" && !files.containsKey("effects/${ready.id}/$name")) {
                files["effects/${ready.id}/$name"] = ensurePrecisionHeader(code).toByteArray(StandardCharsets.UTF_8)
            }
        }
    }

    fun buildPack(suite: SuiteInfo, effects: List<EffectDefinition>, outputStream: OutputStream) {
        require(effects.isNotEmpty() && effects.map { it.id }.distinct().size == effects.size)
        val files = linkedMapOf<String, ByteArray>()
        val obj = JSONObject()
            .put("id", suite.id)
            .put("name", suite.name)
            .put("author", suite.author)
            .put("brand", suite.brand)
            .put("version", suite.version)
            .put("description", suite.description)
            .put("category", suite.category)
            .put("minAppVersion", "1.4.9")
            .put("effects", JSONArray(effects.map { it.id }))
        files["suite.json"] = obj.toString(2).toByteArray(StandardCharsets.UTF_8)
        effects.forEach { addEffect(files, it) }
        write(files, outputStream)
    }

    fun validatePack(inputStream: InputStream): ValidationResult {
        val errors = mutableListOf<String>()
        val ids = mutableListOf<String>()
        var suite: SuiteInfo? = null
        try {
            val files = read(inputStream)
            val obj = JSONObject(String(files["suite.json"] ?: error("Missing root suite.json"), StandardCharsets.UTF_8))
            val category = obj.getString("category")
            require(obj.getString("id").contains('.')) { "Suite id must be reverse-domain" }
            suite = SuiteInfo(
                obj.getString("id"),
                obj.getString("name"),
                obj.optString("author", ""),
                obj.optString("brand", ""),
                obj.optString("version", "1"),
                obj.optString("description", ""),
                category
            )
            val declared = obj.getJSONArray("effects")
            require(declared.length() > 0) { "No effects declared" }
            for (i in 0 until declared.length()) {
                val id = declared.getString(i)
                require(id(id)) { "suite.effects must contain directory ids, not JSON paths" }
                require(id !in ids) { "Duplicate effect id" }
                ids.add(id)
                val prefix = "effects/$id/"
                val effect = JSONObject(String(files[prefix + "effect.json"] ?: error("Missing effect.json for $id"), StandardCharsets.UTF_8))
                require(effect.getString("id") == id)
                require(effect.getString("category") == category) { "Effect/suite category mismatch for $id" }
                val source = String(files[prefix + "shader.glsl"] ?: error("Missing shader.glsl for $id"), StandardCharsets.UTF_8)
                require(source.contains("void main(")) { "Missing main for $id" }
                require(!source.contains("#version")) { "Expected GLSL ES 1.00" }
                val passes = effect.optJSONArray("passes")
                if (passes != null) {
                    for (j in 0 until passes.length()) {
                        val name = passes.getString(j)
                        require(safe(name) && files.containsKey(prefix + name)) { "Missing or unsafe pass file" }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add(e.message ?: e.javaClass.simpleName)
        }
        return ValidationResult(errors.isEmpty(), errors = errors, suiteInfo = suite, effectIds = ids)
    }

    fun mergePacks(
        existingPackInput: InputStream,
        newEffects: List<EffectDefinition>,
        collisionPolicy: CollisionPolicy,
        outputStream: OutputStream
    ): List<String> {
        val files = read(existingPackInput)
        val original = JSONObject(String(files["suite.json"] ?: error("Missing suite.json"), StandardCharsets.UTF_8))
        val old = original.getJSONArray("effects")
        val ids = (0 until old.length()).map { old.getString(it) }.toMutableList()
        val added = mutableListOf<String>()
        val category = original.getString("category")
        for (effect in newEffects) {
            var next = effect
            if (next.id in ids) when (collisionPolicy) {
                CollisionPolicy.SKIP -> continue
                CollisionPolicy.REPLACE -> {
                    files.keys.filter { it.startsWith("effects/${next.id}/") }.toList().forEach { files.remove(it) }
                }
                CollisionPolicy.KEEP_BOTH_NEW_ID -> {
                    var n = 2
                    while ("${effect.id}_$n" in ids) n++
                    next = effect.copy(id = "${effect.id}_$n")
                }
            }
            next = next.copy(category = category)
            addEffect(files, next)
            if (next.id !in ids) ids.add(next.id)
            added.add(next.id)
        }
        original.put("effects", JSONArray(ids))
        files["suite.json"] = original.toString(2).toByteArray(StandardCharsets.UTF_8)
        write(files, outputStream)
        return added
    }
}
