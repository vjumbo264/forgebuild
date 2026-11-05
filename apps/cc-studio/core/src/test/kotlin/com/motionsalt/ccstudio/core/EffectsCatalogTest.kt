package com.motionsalt.ccstudio.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EffectsCatalogTest {

    @Test
    fun testAllRequiredEffectsAndCategoriesPresent() {
        val candidates = listOf(
            File("../app/src/main/assets/effects"),
            File("apps/cc-studio/app/src/main/assets/effects"),
            File("app/src/main/assets/effects"),
            File("/tmp/forgebuild/apps/cc-studio/app/src/main/assets/effects")
        )
        val dir = candidates.firstOrNull { it.exists() }
        assertNotNull("Assets effects directory must exist", dir)

        val effectFolders = dir!!.listFiles { f -> f.isDirectory } ?: emptyArray()
        assertTrue("Must contain at least 20 effects (found ${effectFolders.size})", effectFolders.size >= 20)

        val categories = mutableSetOf<String>()
        var foundTritone = false

        for (f in effectFolders) {
            val jsonFile = File(f, "effect.json")
            assertTrue("effect.json must exist in ${f.name}", jsonFile.exists())
            val content = jsonFile.readText()
            if (f.name == "ms_tritone") {
                foundTritone = true
                assertTrue("ms_tritone category must be Toning", content.contains("\"Toning\""))
            }
            if (content.contains("\"Colour correction\"")) categories.add("Colour correction")
            if (content.contains("\"Tone\"")) categories.add("Tone")
            if (content.contains("\"Toning\"")) categories.add("Toning")
            if (content.contains("\"Detail\"")) categories.add("Detail")
            if (content.contains("\"Look\"")) categories.add("Look")
        }

        assertTrue("Tritone must be present and categorized in Toning", foundTritone)
        assertTrue("Must contain Colour correction category", categories.contains("Colour correction"))
        assertTrue("Must contain Tone category", categories.contains("Tone"))
        assertTrue("Must contain Toning category", categories.contains("Toning"))
        assertTrue("Must contain Detail category", categories.contains("Detail"))
        assertTrue("Must contain Look category", categories.contains("Look"))
    }
}
