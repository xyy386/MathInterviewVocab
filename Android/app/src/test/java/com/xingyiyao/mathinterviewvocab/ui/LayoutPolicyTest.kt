package com.xingyiyao.mathinterviewvocab.ui

import com.xingyiyao.mathinterviewvocab.audio.AudioInventory
import com.xingyiyao.mathinterviewvocab.core.VocabularyJson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LayoutPolicyTest {
    @Test
    fun compactAndMediumBoundaryUsesWindowWidth() {
        assertEquals(AppWindowLayout.Compact, layoutForWidth(320f))
        assertEquals(AppWindowLayout.Compact, layoutForWidth(599.99f))
        assertEquals(AppWindowLayout.Medium, layoutForWidth(600f))
        assertEquals(AppWindowLayout.Medium, layoutForWidth(839.99f))
    }

    @Test
    fun expandedStartsAt840AndSupportsLargeTablets() {
        assertEquals(AppWindowLayout.Expanded, layoutForWidth(840f))
        assertEquals(AppWindowLayout.Expanded, layoutForWidth(1280f))
        assertEquals(AppWindowLayout.Expanded, layoutForWidth(2560f))
    }

    @Test
    fun resizeAndSplitScreenRecomputeWithoutDeviceAssumptions() {
        val widths = listOf(1280f, 839f, 599f, 600f, 840f)
        assertEquals(
            listOf(AppWindowLayout.Expanded, AppWindowLayout.Medium, AppWindowLayout.Compact,
                AppWindowLayout.Medium, AppWindowLayout.Expanded),
            widths.map(::layoutForWidth),
        )
    }

    @Test
    fun invalidWindowMetricsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { layoutForWidth(-1f) }
        assertThrows(IllegalArgumentException::class.java) { layoutForWidth(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { layoutForWidth(Float.POSITIVE_INFINITY) }
    }

    @Test
    fun voicePreviewUsesCanonicalAudioTextForBothAccents() {
        val root = File(checkNotNull(System.getProperty("math.repositoryRoot")))
        val terms = VocabularyJson.decodeTerms(File(root, "Resources/terms.json").readText())
        val inventory = AudioInventory.parse(File(root, "Windows/resources/audio-manifest.json").readText())
        val text = checkNotNull(voicePreviewText(terms))
        assertEquals(terms.first { it.id == 1 }.speechText, text)
        assertNotNull(inventory.find(text, "en-US"))
        assertNotNull(inventory.find(text, "en-GB"))
    }

    @Test
    fun absentCanonicalTermDisablesPreviewInsteadOfGuessing() {
        assertNull(voicePreviewText(emptyList()))
    }
}
