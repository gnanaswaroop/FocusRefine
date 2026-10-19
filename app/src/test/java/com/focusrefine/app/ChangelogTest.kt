package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogTest {

    private val sample = """
        {"releases":[
          {"version":"2.0","date":"2027-01-02","title":"Second","sections":[
            {"heading":"New","items":["a","b"]},{"heading":"Fixed","items":["c"]}]},
          {"version":"1.0","date":"2026-10-19","sections":[{"heading":"New","items":["first"]}]}
        ]}
    """.trimIndent()

    @Test
    fun parsesReleasesInOrderWithTheirSections() {
        val releases = Changelog.parse(sample)
        assertEquals(listOf("2.0", "1.0"), releases.map { it.version })
        assertEquals("Second", releases[0].title)
        assertEquals("", releases[1].title) // the title is optional
        assertEquals(listOf("New", "Fixed"), releases[0].sections.map { it.heading })
        assertEquals(listOf("a", "b"), releases[0].sections[0].items)
    }

    private fun bundled(): String {
        val file = listOf(File("src/main/assets/changelog.json"), File("app/src/main/assets/changelog.json")).first { it.exists() }
        return file.readText()
    }

    private fun versionName(): String {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.exists() }.readText()
        return Regex("""versionName\s*=\s*"([^"]+)"""").find(gradle)!!.groupValues[1]
    }

    @Test
    fun theBundledChangelogIsWellFormed() {
        val releases = Changelog.parse(bundled())
        assertTrue("at least one release", releases.isNotEmpty())
        releases.forEach { r ->
            assertTrue("release ${r.version} has sections", r.sections.isNotEmpty())
            assertTrue("a date like 2026-10-19: ${r.date}", Regex("""\d{4}-\d{2}-\d{2}""").matches(r.date))
            r.sections.forEach { s ->
                assertTrue("${r.version}/${s.heading} has items", s.items.isNotEmpty())
                s.items.forEach { assertFalse("no blank entries", it.isBlank()) }
            }
        }
    }

    @Test
    fun theNewestEntryMatchesTheAppVersion() {
        // Bumping versionName in build.gradle.kts without writing release notes fails here.
        assertEquals(versionName(), Changelog.parse(bundled()).first().version)
    }

    @Test
    fun versionsAreNotRepeated() {
        val versions = Changelog.parse(bundled()).map { it.version }
        assertEquals(versions.distinct(), versions)
    }
}
