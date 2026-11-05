package com.motionsalt.ccstudio.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val projectsRoot: File
        get() {
            val dir = File(context.filesDir, "projects")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun getAllProjects(): List<ProjectData> {
        val list = mutableListOf<ProjectData>()
        val files = projectsRoot.listFiles() ?: return emptyList()
        for (f in files) {
            if (f.isDirectory) {
                val pFile = File(f, "project.json")
                if (pFile.exists()) {
                    try {
                        val json = pFile.readText(StandardCharsets.UTF_8)
                        val project = ProjectData.fromJsonObject(JSONObject(json))
                        list.add(project)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun getProject(id: String): ProjectData? {
        val pDir = File(projectsRoot, id)
        val pFile = File(pDir, "project.json")
        if (!pFile.exists()) return null
        return try {
            val json = pFile.readText(StandardCharsets.UTF_8)
            ProjectData.fromJsonObject(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    fun saveProject(project: ProjectData) {
        val pDir = File(projectsRoot, project.id)
        if (!pDir.exists()) pDir.mkdirs()
        val imagesDir = File(pDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val resultsDir = File(pDir, "results")
        if (!resultsDir.exists()) resultsDir.mkdirs()

        project.updatedAt = System.currentTimeMillis()
        val pFile = File(pDir, "project.json")
        pFile.writeText(project.toJsonObject().toString(2), StandardCharsets.UTF_8)
    }

    fun createProject(name: String, lookName: String = "MOTIONSALT_Grade"): ProjectData {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = ProjectData(
            id = id,
            name = name,
            lookName = lookName,
            createdAt = now,
            updatedAt = now
        )
        saveProject(project)
        return project
    }

    fun duplicateProject(id: String): ProjectData? {
        val orig = getProject(id) ?: return null
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val copy = ProjectData(
            id = newId,
            name = "${orig.name} (Copy)",
            lookName = orig.lookName,
            createdAt = now,
            updatedAt = now,
            pairs = orig.pairs.map { p ->
                FramePair(
                    id = UUID.randomUUID().toString(),
                    mode = p.mode,
                    base = p.base?.copy(),
                    target = p.target?.copy()
                )
            }.toMutableList(),
            fitResult = orig.fitResult,
            tuneState = orig.tuneState,
            exportHistory = mutableListOf()
        )
        saveProject(copy)
        return copy
    }

    fun deleteProject(id: String): Boolean {
        val pDir = File(projectsRoot, id)
        if (pDir.exists()) {
            return pDir.deleteRecursively()
        }
        return false
    }

    fun deleteAllProjects(): Boolean {
        val files = projectsRoot.listFiles() ?: return true
        var allSuccess = true
        for (f in files) {
            if (f.isDirectory) {
                if (!f.deleteRecursively()) allSuccess = false
            }
        }
        return allSuccess
    }

    fun createDemoProject(): ProjectData {
        val project = createProject(
            name = "Demo: Gojo real-pair regression",
            lookName = "GOJO_CC"
        )
        val pDir = File(projectsRoot, project.id)
        val imagesDir = File(pDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        // Generate synthetic Base and Target charts
        val baseBytes = context.assets.open("regression/gojo_before.png").use { it.readBytes() }
        val targetBytes = context.assets.open("regression/gojo_target.png").use { it.readBytes() }

        val baseFile = File(imagesDir, "gojo_before.png")
        val targetFile = File(imagesDir, "gojo_target.png")

        baseFile.writeBytes(baseBytes)
        targetFile.writeBytes(targetBytes)

        val pair = FramePair(
            id = UUID.randomUUID().toString(),
            mode = PairMode.SAME_FRAME,
            base = ImageFrame(baseFile.absolutePath),
            target = ImageFrame(targetFile.absolutePath)
        )
        project.pairs.add(pair)
        saveProject(project)
        return project
    }

    fun copyFileToProject(projectId: String, sourceStream: java.io.InputStream, filename: String): String {
        val pDir = File(projectsRoot, projectId)
        val imagesDir = File(pDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val destFile = File(imagesDir, "${System.currentTimeMillis()}_$filename")
        FileOutputStream(destFile).use { out ->
            sourceStream.copyTo(out)
        }
        return destFile.absolutePath
    }
}
