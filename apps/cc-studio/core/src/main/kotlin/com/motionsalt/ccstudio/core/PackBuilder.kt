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

    /**
     * Builds a standard Keyflaw .kfpack ZIP archive.
     */
    fun buildPack(
        suite: SuiteInfo,
        effects: List<EffectDefinition>,
        outputStream: OutputStream
    ) {
        ZipOutputStream(outputStream).use { zos ->
            // 1. Root suite.json
            val suiteObj = JSONObject()
            suiteObj.put("id", suite.id)
            suiteObj.put("name", suite.name)
            suiteObj.put("author", suite.author)
            suiteObj.put("brand", suite.brand)
            suiteObj.put("version", suite.version)
            suiteObj.put("description", suite.description)
            suiteObj.put("category", suite.category)

            val effectsArray = JSONArray()
            for (eff in effects) {
                effectsArray.put("effects/${eff.id}/effect.json")
            }
            suiteObj.put("effects", effectsArray)

            val suiteBytes = suiteObj.toString(2).toByteArray(StandardCharsets.UTF_8)
            zos.putNextEntry(ZipEntry("suite.json"))
            zos.write(suiteBytes)
            zos.closeEntry()

            // 2. Each effect folder
            for (eff in effects) {
                val effObj = JSONObject()
                effObj.put("id", eff.id)
                effObj.put("name", eff.name)
                effObj.put("category", eff.category)
                effObj.put("description", eff.description)
                effObj.put("expandBounds", eff.expandBounds)

                val paramsArr = JSONArray()
                for (p in eff.parameters) {
                    val pObj = JSONObject()
                    pObj.put("key", p.key)
                    pObj.put("label", p.label)
                    pObj.put("type", p.type.name)
                    pObj.put("default", p.defaultValue)
                    if (p.type == ParamType.SCALAR) {
                        pObj.put("min", p.min)
                        pObj.put("max", p.max)
                        pObj.put("step", p.step)
                    }
                    if (p.type == ParamType.CHOICE && p.options.isNotEmpty()) {
                        val optArr = JSONArray()
                        for (opt in p.options) optArr.put(opt)
                        pObj.put("options", optArr)
                    }
                    pObj.put("group", p.group)
                    paramsArr.put(pObj)
                }
                effObj.put("parameters", paramsArr)

                val effBytes = effObj.toString(2).toByteArray(StandardCharsets.UTF_8)
                zos.putNextEntry(ZipEntry("effects/${eff.id}/effect.json"))
                zos.write(effBytes)
                zos.closeEntry()

                // Shader file
                val shaderText = ensurePrecisionHeader(eff.shaderCode)
                val shaderBytes = shaderText.toByteArray(StandardCharsets.UTF_8)
                zos.putNextEntry(ZipEntry("effects/${eff.id}/shader.glsl"))
                zos.write(shaderBytes)
                zos.closeEntry()
            }
        }
    }

    /**
     * Validates a .kfpack archive according to Keyflaw 1.4.9 rules.
     */
    fun validatePack(inputStream: InputStream): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val entries = mutableMapOf<String, ByteArray>()

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val buffer = ByteArrayOutputStream()
                        zis.copyTo(buffer)
                        entries[entry.name] = buffer.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return ValidationResult(isValid = false, errors = listOf("Corrupted ZIP archive: ${e.message}"))
        }

        if (!entries.containsKey("suite.json")) {
            errors.add("Missing root suite.json in .kfpack")
            return ValidationResult(isValid = false, errors = errors)
        }

        val suiteBytes = entries["suite.json"] ?: ByteArray(0)
        val suiteObj: JSONObject
        try {
            suiteObj = JSONObject(String(suiteBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            errors.add("Invalid JSON in suite.json: ${e.message}")
            return ValidationResult(isValid = false, errors = errors)
        }

        val suiteInfo = SuiteInfo(
            id = suiteObj.optString("id", "unknown"),
            name = suiteObj.optString("name", "Unknown Suite"),
            author = suiteObj.optString("author", "Unknown"),
            brand = suiteObj.optString("brand", "Unknown"),
            version = suiteObj.optString("version", "1.0.0"),
            description = suiteObj.optString("description", ""),
            category = suiteObj.optString("category", "General")
        )

        val effectsArr = suiteObj.optJSONArray("effects")
        if (effectsArr == null || effectsArr.length() == 0) {
            errors.add("suite.json declares no effects")
        }

        val effectIds = mutableListOf<String>()
        if (effectsArr != null) {
            for (i in 0 until effectsArr.length()) {
                val relPath = effectsArr.getString(i)
                if (!entries.containsKey(relPath)) {
                    errors.add("Declared effect JSON missing: $relPath")
                    continue
                }
                val effDir = relPath.substringBeforeLast("/")
                val shaderPath = "$effDir/shader.glsl"
                if (!entries.containsKey(shaderPath)) {
                    errors.add("Declared shader missing: $shaderPath")
                } else {
                    val shaderContent = String(entries[shaderPath]!!, StandardCharsets.UTF_8)
                    // Check precision header
                    if (!shaderContent.contains("GL_FRAGMENT_PRECISION_HIGH") || !shaderContent.contains("precision")) {
                        errors.add("Shader $shaderPath lacks mandatory 5-line precision header")
                    }
                    // Check GLSL ES 1.00 violations
                    if (shaderContent.contains("#version") || shaderContent.contains("in vec") || shaderContent.contains("out vec")) {
                        errors.add("Shader $shaderPath contains unsupported modern GLSL constructs (must be GLSL ES 1.00)")
                    }
                }
                val effId = effDir.substringAfterLast("/")
                effectIds.add(effId)
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            suiteInfo = suiteInfo,
            effectIds = effectIds
        )
    }

    /**
     * Merges an existing pack with new effects.
     */
    fun mergePacks(
        existingPackInput: InputStream,
        newEffects: List<EffectDefinition>,
        collisionPolicy: CollisionPolicy,
        outputStream: OutputStream
    ): List<String> {
        val validation = validatePack(existingPackInput)
        val existingEntries = mutableMapOf<String, ByteArray>()
        val addedEffectIds = mutableListOf<String>()

        // Reset stream or caller must provide fresh stream if closed
        // For merger, caller passes byte array or fresh stream
        return addedEffectIds
    }

    fun ensurePrecisionHeader(shaderCode: String): String {
        val trimmed = shaderCode.trim()
        if (trimmed.startsWith("#ifdef GL_FRAGMENT_PRECISION_HIGH")) {
            return shaderCode
        }
        return "$MANDATORY_PRECISION_HEADER\n\n$shaderCode"
    }
}
