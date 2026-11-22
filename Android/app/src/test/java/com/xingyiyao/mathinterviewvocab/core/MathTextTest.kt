package com.xingyiyao.mathinterviewvocab.core

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MathTextTest {
    private fun repositoryRoot(): File =
        System.getProperty("math.repositoryRoot")?.let(::File)
            ?: generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
                .first { File(it, "Resources/terms.json").isFile }

    private fun assertAllCoordinatesInsideViewBox(doc: MathSvgDocument, label: String) {
        assertTrue("$label should have path ops", doc.ops.isNotEmpty())
        val margin = maxOf(doc.width, doc.height) * 0.05f + 5f
        val minXBound = doc.minX - margin
        val maxXBound = doc.minX + doc.width + margin
        val minYBound = doc.minY - margin
        val maxYBound = doc.minY + doc.height + margin

        fun checkPoint(x: Float, y: Float) {
            assertTrue("$label finite x=$x y=$y", x.isFinite() && y.isFinite())
            assertTrue(
                "$label x=$x out of viewBox [${doc.minX}, ${doc.minX + doc.width}]",
                x in minXBound..maxXBound,
            )
            assertTrue(
                "$label y=$y out of viewBox [${doc.minY}, ${doc.minY + doc.height}]",
                y in minYBound..maxYBound,
            )
        }

        for (op in doc.ops) {
            when (op) {
                is MathPathOp.MoveTo -> checkPoint(op.x, op.y)
                is MathPathOp.LineTo -> checkPoint(op.x, op.y)
                is MathPathOp.QuadTo -> {
                    checkPoint(op.x1, op.y1)
                    checkPoint(op.x, op.y)
                }
                is MathPathOp.CubicTo -> {
                    checkPoint(op.x1, op.y1)
                    checkPoint(op.x2, op.y2)
                    checkPoint(op.x, op.y)
                }
                MathPathOp.Close -> Unit
            }
        }
    }

    @Test
    fun segmentationPreservesSourceStripsBackticksAndIgnoresUnderscores() {
        val mixed = MathTextDocument.segments("中文🙂 `\\(H_0\\)` 和 \\[x_{ij}^2\\] 及 `code`")
        assertEquals(
            listOf(
                MathSegment("中文🙂 ", null, false, "中文🙂 "),
                MathSegment("\\(H_0\\)", "H_0", false, "\\(H_0\\)"),
                MathSegment(" 和 ", null, false, " 和 "),
                MathSegment("\\[x_{ij}^2\\]", "x_{ij}^2", true, "\\[x_{ij}^2\\]"),
                MathSegment(" 及 code", null, false, " 及 code"),
            ),
            mixed,
        )
        assertFalse(MathTextDocument.containsMath("file_name and H_0 and `plain`"))
        assertEquals(
            listOf(MathSegment("file_name and H_0 and plain", null, false, "file_name and H_0 and plain")),
            MathTextDocument.segments("file_name and H_0 and `plain`"),
        )
    }

    @Test
    fun searchMatchesSubscriptAliasesForCanonicalTerms() {
        val terms = canonicalTerms()
        for (alias in listOf("H_0", "H₀", "H0", "\\(H_0\\)")) {
            assertEquals(MathTextDocument.searchText("H_0"), MathTextDocument.searchText(alias))
            val matches = VocabularyCore.filterTerms(
                terms = terms,
                filters = TermFilters(query = alias),
                progress = ProgressData(),
            )
            assertTrue("Query $alias should match Null Hypothesis (323)", matches.any { it.id == 323 })
        }
    }

    @Test
    fun canonicalMathManifestAndAll63SvgsParseCleanlyWithValidHashes() {
        val root = repositoryRoot()
        val mathDir = File(root, "Resources/Math")
        val termsSha = MathManifest.sha256Hex(File(root, "Resources/terms.json").readBytes())
        val manifest = MathManifest.parseAndValidate(
            File(mathDir, "manifest.json").readText(Charsets.UTF_8),
            expectedTermsSha = termsSha,
        )
        assertEquals(63, manifest.entries.size)
        assertEquals("MathJax 4.1.3", manifest.engine)
        val integral = manifest.lookup("F(x)=\\int_{-\\infty}^{x}f(t)\\,\\mathrm{d}t", display = false)
        assertNotNull(integral)
        assertTrue(integral!!.widthEm > 8.0)

        for (entry in manifest.entries) {
            val svgBytes = File(mathDir, entry.svg).readBytes()
            assertEquals("SHA-256 mismatch for ${entry.tex}", entry.svgSHA256, MathManifest.sha256Hex(svgBytes))
            assertTrue(entry.widthEm > 0.0 && entry.heightEm > entry.depthEm && entry.depthEm >= 0.0)
            val doc = MathSvgParser.parse(svgBytes.toString(Charsets.UTF_8))
            assertAllCoordinatesInsideViewBox(doc, entry.tex)
        }

        // 专项确认包含未转义 `<` 的 `data-latex="f(a)f(b)<0"` SVG 解析无异常
        val inequality = manifest.lookup("f(a)f(b)<0", display = false)!!
        val inequalityDoc = MathSvgParser.parse(File(mathDir, inequality.svg).readText(Charsets.UTF_8))
        assertTrue(inequalityDoc.ops.isNotEmpty())

        // 核对全部 326 条规范词条中的公式均已收录
        val terms = canonicalTerms()
        var fieldCount = 0
        val matchedTermIds = mutableSetOf<Int>()
        for (term in terms) {
            for (field in listOf(term.chinese, term.english, term.definition)) {
                val mathParts = MathTextDocument.segments(field).filter { it.latex != null }
                if (mathParts.isNotEmpty()) {
                    fieldCount++
                    matchedTermIds += term.id
                    for (part in mathParts) {
                        assertNotNull(
                            "Missing asset for term ${term.id}: ${part.source}",
                            manifest.lookup(part.latex!!, part.display),
                        )
                    }
                }
            }
        }
        assertEquals(45, fieldCount)
        assertEquals(44, matchedTermIds.size)
    }

    @Test
    fun complexMathFixturesIncludingFractionRectParseCleanly() {
        val fixturesDir = File(repositoryRoot(), "Tests/Fixtures/Math")
        val manifest = MathManifest.parseAndValidate(
            File(fixturesDir, "manifest.json").readText(Charsets.UTF_8),
        )
        assertEquals(6, manifest.entries.size)
        for (entry in manifest.entries) {
            assertTrue(entry.display)
            val svgBytes = File(fixturesDir, entry.svg).readBytes()
            assertEquals(entry.svgSHA256, MathManifest.sha256Hex(svgBytes))
            val doc = MathSvgParser.parse(svgBytes.toString(Charsets.UTF_8))
            assertAllCoordinatesInsideViewBox(doc, entry.tex)
            if (entry.tex.contains("\\frac")) {
                assertTrue(svgBytes.toString(Charsets.UTF_8).contains("<rect"))
                assertTrue(doc.ops.any { it == MathPathOp.Close })
            }
        }
    }

    @Test
    fun svgParserSupportsRelativeAndAffineCommands() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -100 200 200">
              <g transform="translate(10, 20) scale(2, -1) rotate(90)">
                <path d="M0 0h10v10H0V0m5,5l5,5c1,1,2,2,3,3s1,2,3,3q1,2,3,4t2,2z"/>
                <rect x="2" y="3" width="4" height="5"/>
              </g>
            </svg>
        """.trimIndent()
        val doc = MathSvgParser.parse(svg)
        assertEquals(0f, doc.minX, 0.001f)
        assertEquals(-100f, doc.minY, 0.001f)
        assertEquals(200f, doc.width, 0.001f)
        assertEquals(200f, doc.height, 0.001f)
        val first = doc.ops.first() as MathPathOp.MoveTo
        assertEquals(10f, first.x, 0.01f)
        assertEquals(20f, first.y, 0.01f)
        assertTrue(doc.ops.count { it == MathPathOp.Close } >= 2)
    }

    @Test
    fun svgParserAndManifestRejectMaliciousOrInvalidInputs() {
        val badSvgs = listOf(
            """<!DOCTYPE svg><svg viewBox="0 0 10 10"><path d="M0 0L1 1"/></svg>""",
            """<svg viewBox="0 0 10 10"><defs><path id="a" d="M0 0"/></defs></svg>""",
            """<svg viewBox="0 0 10 10"><use href="#a"/></svg>""",
            """<svg viewBox="0 0 10 10"><text>hi</text></svg>""",
            """<svg viewBox="0 0 10 10"><g><path d="M0 0"/></svg>""",
        )
        for (bad in badSvgs) {
            try {
                MathSvgParser.parse(bad)
                fail("Expected parse failure for: $bad")
            } catch (_: IllegalArgumentException) {
            }
        }

        val root = repositoryRoot()
        val rawManifest = File(root, "Resources/Math/manifest.json").readText(Charsets.UTF_8)
        val validManifest = MathManifest.parseAndValidate(rawManifest)
        val validJson = rawManifest

        val badManifestChecks = listOf(
            { MathManifest.parseAndValidate(validJson, expectedTermsSha = "0".repeat(64)) },
            { MathManifest.parseAndValidate(validJson.replace("\"schema\": 1", "\"schema\": 2")) },
            { MathManifest.parseAndValidate(validJson.replace("MathJax 4.1.3", "MathJax 3.0")) },
            {
                MathManifest.parseAndValidate(
                    validJson.replace(validManifest.entries.first().id, "bad_id").takeIf { it != validJson }
                        ?: validJson.replace("\"id\": \"${validManifest.entries.first().id}\"", "\"id\": \"bad\""),
                )
            },
        )
        for (check in badManifestChecks) {
            try {
                check()
                fail("Expected manifest validation failure")
            } catch (_: Exception) {
            }
        }
        assertFalse(abs(validManifest.entries.first().widthEm) < 0.0)
    }

    @Test
    fun speechControllerHelperBlocksFormulasAndAllowsPlainSpeechText() {
        assertNotNull(MathTextDocument.mathSpeechBlockMessage("\\(H_0\\)"))
        assertNotNull(MathTextDocument.mathSpeechBlockMessage("Null Hypothesis（\\(H_0\\)）"))
        assertNull(MathTextDocument.mathSpeechBlockMessage("Null Hypothesis (H 0)"))
        assertNull(MathTextDocument.mathSpeechBlockMessage("unbounded function"))
    }
}
