package com.motionsalt.ccstudio.data

import com.motionsalt.ccstudio.core.TuneState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("left", left.toDouble())
        o.put("top", top.toDouble())
        o.put("right", right.toDouble())
        o.put("bottom", bottom.toDouble())
        return o
    }

    companion object {
        fun fromJsonObject(o: JSONObject?): CropRect {
            if (o == null) return CropRect()
            return CropRect(
                left = o.optDouble("left", 0.0).toFloat(),
                top = o.optDouble("top", 0.0).toFloat(),
                right = o.optDouble("right", 1.0).toFloat(),
                bottom = o.optDouble("bottom", 1.0).toFloat()
            )
        }
    }
}

data class ImageFrame(
    val filePath: String,
    val cropRect: CropRect = CropRect(),
    val ignoreRects: List<CropRect> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("filePath", filePath)
        o.put("cropRect", cropRect.toJsonObject())
        val arr = JSONArray()
        for (ir in ignoreRects) arr.put(ir.toJsonObject())
        o.put("ignoreRects", arr)
        return o
    }

    companion object {
        fun fromJsonObject(o: JSONObject?): ImageFrame? {
            if (o == null) return null
            val fp = o.optString("filePath", "")
            if (fp.isEmpty()) return null
            val cr = CropRect.fromJsonObject(o.optJSONObject("cropRect"))
            val iList = mutableListOf<CropRect>()
            val arr = o.optJSONArray("ignoreRects")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    iList.add(CropRect.fromJsonObject(arr.optJSONObject(i)))
                }
            }
            return ImageFrame(fp, cr, iList)
        }
    }
}

enum class PairMode {
    SAME_FRAME,
    DIFFERENT_SCENES
}

data class FramePair(
    val id: String = UUID.randomUUID().toString(),
    var mode: PairMode = PairMode.SAME_FRAME,
    var base: ImageFrame? = null,
    var target: ImageFrame? = null
) {
    val isComplete: Boolean get() = base != null && target != null

    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("mode", mode.name)
        if (base != null) o.put("base", base!!.toJsonObject())
        if (target != null) o.put("target", target!!.toJsonObject())
        return o
    }

    companion object {
        fun fromJsonObject(o: JSONObject): FramePair {
            val id = o.optString("id", UUID.randomUUID().toString())
            val mode = try {
                PairMode.valueOf(o.optString("mode", PairMode.SAME_FRAME.name))
            } catch (e: Exception) {
                PairMode.SAME_FRAME
            }
            val base = ImageFrame.fromJsonObject(o.optJSONObject("base"))
            val target = ImageFrame.fromJsonObject(o.optJSONObject("target"))
            return FramePair(id, mode, base, target)
        }
    }
}

data class FitResultData(
    val modelPath:String,
    val meanDeltaE:Float,
    val p95DeltaE:Float,
    val timestamp:Long,
    val identityMean:Float,
    val identityP95:Float,
    val holdoutMean:Float,
    val blurredMean:Float,
    val aligned:Boolean=true
) {
    fun toJsonObject()=JSONObject().put("modelPath",modelPath).put("meanDeltaE",meanDeltaE.toDouble()).put("p95DeltaE",p95DeltaE.toDouble()).put("timestamp",timestamp)
        .put("identityMean",identityMean.toDouble()).put("identityP95",identityP95.toDouble()).put("holdoutMean",holdoutMean.toDouble()).put("blurredMean",blurredMean.toDouble()).put("aligned",aligned)
    companion object {
        fun fromJsonObject(o:JSONObject?):FitResultData? {
            // Old result formats require a new fit, never pretend a colour lattice is this model.
            if(o==null||!o.has("modelPath"))return null
            return FitResultData(o.getString("modelPath"),o.getDouble("meanDeltaE").toFloat(),o.getDouble("p95DeltaE").toFloat(),o.getLong("timestamp"),o.getDouble("identityMean").toFloat(),o.getDouble("identityP95").toFloat(),o.getDouble("holdoutMean").toFloat(),o.getDouble("blurredMean").toFloat(),o.optBoolean("aligned",true))
        }
    }
}

data class ProjectData(
    val id: String,
    var name: String,
    var lookName: String,
    val createdAt: Long,
    var updatedAt: Long,
    val pairs: MutableList<FramePair> = mutableListOf(),
    var fitResult: FitResultData? = null,
    var tuneState: TuneState = TuneState.defaultState(),
    val exportHistory: MutableList<String> = mutableListOf()
) {
    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("lookName", lookName)
        o.put("createdAt", createdAt)
        o.put("updatedAt", updatedAt)

        val pairsArr = JSONArray()
        for (p in pairs) pairsArr.put(p.toJsonObject())
        o.put("pairs", pairsArr)

        if (fitResult != null) o.put("fitResult", fitResult!!.toJsonObject())
        o.put("tuneState", JSONObject(tuneState.toJsonString()))

        val expArr = JSONArray()
        for (eh in exportHistory) expArr.put(eh)
        o.put("exportHistory", expArr)

        return o
    }

    companion object {
        fun fromJsonObject(o: JSONObject): ProjectData {
            val id = o.getString("id")
            val name = o.optString("name", "Untitled Look")
            val lookName = o.optString("lookName", "MOTIONSALT_Grade")
            val createdAt = o.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = o.optLong("updatedAt", System.currentTimeMillis())

            val pairsList = mutableListOf<FramePair>()
            val pairsArr = o.optJSONArray("pairs")
            if (pairsArr != null) {
                for (i in 0 until pairsArr.length()) {
                    pairsList.add(FramePair.fromJsonObject(pairsArr.getJSONObject(i)))
                }
            }

            val fitResult = FitResultData.fromJsonObject(o.optJSONObject("fitResult"))

            val tuneStateObj = o.optJSONObject("tuneState")
            val tuneState = if (tuneStateObj != null) {
                TuneState.fromJsonString(tuneStateObj.toString())
            } else {
                TuneState.defaultState()
            }

            val expList = mutableListOf<String>()
            val expArr = o.optJSONArray("exportHistory")
            if (expArr != null) {
                for (i in 0 until expArr.length()) expList.add(expArr.getString(i))
            }

            return ProjectData(
                id = id,
                name = name,
                lookName = lookName,
                createdAt = createdAt,
                updatedAt = updatedAt,
                pairs = pairsList,
                fitResult = fitResult,
                tuneState = tuneState,
                exportHistory = expList
            )
        }
    }
}
