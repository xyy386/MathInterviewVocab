package com.xingyiyao.mathinterviewvocab.audio

import com.xingyiyao.mathinterviewvocab.core.Term
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AudioInventoryTest {
    private val root = File(requireNotNull(System.getProperty("math.repositoryRoot")))
    private val resources = File(root, "Windows/resources")
    private val manifestText = File(resources, "audio-manifest.json").readText()
    private val inventory = AudioInventory.parse(manifestText)

    @Test fun everyClipMatchesManifestByteCountAndSha256() {
        assertEquals(2328, inventory.size)
        val clips = File(resources, "audio").listFiles { file -> file.extension == "mp3" && !file.name.startsWith(".") }!!
        assertEquals(2328, clips.size)
        inventory.allEntries().forEach { entry ->
            val bytes = File(resources, "audio/${entry.file}").readBytes()
            assertEquals(entry.file, entry.bytes, bytes.size.toLong())
            assertEquals(entry.file, entry.sha256, AudioInventory.sha256(bytes))
        }
    }

    @Test fun everyTermHasBothFullPronunciationsAndIPA() {
        val terms = Json.decodeFromString<List<Term>>(File(root, "Resources/terms.json").readText())
        assertEquals(326, terms.size)
        assertEquals(130, terms.count(Term::important))
        terms.forEach { term ->
            assertTrue(term.pronunciation.americanIPA.isNotBlank())
            assertTrue(term.pronunciation.britishIPA.isNotBlank())
            AudioInventory.accents.forEach { accent -> assertNotNull("${term.id}: $accent", inventory.find(term.speechText, accent)) }
        }
    }

    @Test fun selectionsTrimBoundaryPunctuationButDoNotInventClips() {
        AudioInventory.accents.forEach { accent ->
            assertNotNull(inventory.find("  ‘unbounded’!  ", accent))
            assertNotNull(inventory.find("unbounded function", accent))
        }
        assertNull(inventory.find("boundedXYZmissing", "en-US"))
        assertNull(inventory.find(" ", "en-US"))
        assertNull(inventory.find("unbounded", "fr-FR"))
    }

    @Test fun malformedManifestFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioInventory.parse(manifestText.replaceFirst("\"complete\": true", "\"complete\": false"))
        }
    }

    @Test fun noticesArePresentWithoutBundledModelWeights() {
        assertTrue(File(root, "LICENSE").readText().contains("MIT License"))
        assertTrue(File(resources, "KOKORO-APACHE-2.0.txt").readText().contains("Apache License"))
        assertTrue(File(resources, "AUDIO-SOURCES.md").readText().contains("af_heart"))
    }
}
