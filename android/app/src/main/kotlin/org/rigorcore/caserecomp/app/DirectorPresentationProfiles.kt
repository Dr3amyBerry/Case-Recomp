package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.director.DirectorContent
import java.security.MessageDigest

/** Data only: no Lingo, scripts, reflection or game logic in presentation profiles. */
internal data class FontPresentation(
    val widthScale: Float = 1f,
    val sizeScale: Float = 1f,
    val linePitchScale: Float? = null,
    val skew: Float = 0f,
)

internal data class FontPresentationRule(
    val containsAll: List<String>,
    val widthScale: Float? = null,
    val sizeScale: Float? = null,
    val linePitchScale: Float? = null,
    val skew: Float? = null,
)

internal data class TextBoxPresentation(
    val centerMargin: Int,
    val minimumPitch: Float,
    val firstBaseline: Float,
    val leadingBlank: Float,
    val condense: List<Float>,
    val shrink: List<Float>,
    val maximumHeightMultiplier: Int,
)

internal data class DirectorTextPresentation(
    val defaultFont: FontPresentation = FontPresentation(),
    val fontRules: List<FontPresentationRule> = emptyList(),
    val embeddedStyleFromFaceName: Boolean = false,
    val boxes: TextBoxPresentation? = null,
) {
    fun font(face: String): FontPresentation {
        val name = face.lowercase()
        val matching = fontRules.filter { rule -> rule.containsAll.all { it in name } }
        return FontPresentation(
            widthScale = matching.firstNotNullOfOrNull { it.widthScale } ?: defaultFont.widthScale,
            sizeScale = matching.firstNotNullOfOrNull { it.sizeScale } ?: defaultFont.sizeScale,
            linePitchScale = matching.firstNotNullOfOrNull { it.linePitchScale } ?: defaultFont.linePitchScale,
            skew = matching.firstNotNullOfOrNull { it.skew } ?: defaultFont.skew,
        )
    }
}

internal data class BundleFingerprint(val movieSha256: String, val lingoSha256: String) {
    companion object {
        fun of(movie: ByteArray, lingo: ByteArray) = BundleFingerprint(sha(movie), sha(lingo))
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}

internal data class DirectorPresentationProfile(
    val id: String,
    val schemaVersion: Int,
    val revision: Int,
    val text: DirectorTextPresentation,
    val approvedLegacyNudges: Map<String, Pair<Int, Int>> = emptyMap(),
) {
    /** Legacy file requests only reviewed offsets. It cannot choose a profile or supply code. */
    fun legacyNudges(json: String): Map<String, Pair<Int, Int>> {
        if (approvedLegacyNudges.isEmpty()) return emptyMap()
        require(json.length <= 64 * 1024) { "presentation config exceeds cap" }
        val root = MiniJson.parse(json) as? Map<*, *> ?: error("presentation must be an object")
        require(root.keys == setOf("nudge")) { "unknown presentation configuration" }
        val rows = root["nudge"] as? Map<*, *> ?: error("nudge must be an object")
        require(rows.size <= approvedLegacyNudges.size) { "unreviewed offsets" }
        return rows.entries.associate { (key, value) ->
            val name = key as? String ?: error("invalid member name")
            val pair = value as? List<*> ?: error("offset must be a pair")
            require(pair.size == 2 && pair.all { it is Long && it in -50L..50L }) { "invalid offset" }
            val offset = (pair[0] as Long).toInt() to (pair[1] as Long).toInt()
            require(approvedLegacyNudges[name] == offset) { "offset has no reviewed evidence" }
            name to offset
        }
    }
}

/** App-side registry. The Director engine has no commercial-title conditionals. */
internal object DirectorPresentationProfiles {
    val GENERIC = DirectorPresentationProfile("director-generic", 1, 1, DirectorTextPresentation())

    // Calibrations from 6c03811, 252ba53, cfeadd2 and 747d659. Values unchanged.
    // See docs/HUNTSVILLE_PRESENTATION_ISOLATION.md for evidence and retirement criteria.
    val HUNTSVILLE_ES = DirectorPresentationProfile(
        id = "huntsville-es-presentation", schemaVersion = 1, revision = 1,
        text = DirectorTextPresentation(
            defaultFont = FontPresentation(linePitchScale = 1.25f),
            fontRules = listOf(
                FontPresentationRule(listOf("typewriter"), widthScale = 0.9f),
                FontPresentationRule(listOf("palatino"), widthScale = 0.88f, linePitchScale = 1.08f),
                FontPresentationRule(listOf("times"), widthScale = 0.86f, linePitchScale = 1.08f),
                FontPresentationRule(listOf("tekto", "italic"), widthScale = 0.95f, sizeScale = 0.68f, linePitchScale = 1.32f),
                FontPresentationRule(listOf("tekto"), widthScale = 1.15f, sizeScale = 0.8f, linePitchScale = 1.12f),
                FontPresentationRule(listOf("readout"), widthScale = 0.9f, sizeScale = 0.95f, skew = -0.18f),
            ),
            embeddedStyleFromFaceName = true,
            boxes = TextBoxPresentation(
                centerMargin = 6, minimumPitch = 0.75f, firstBaseline = 0.78f, leadingBlank = 0.75f,
                condense = listOf(1f, 0.95f, 0.9f, 0.85f), shrink = listOf(0.9f, 0.8f, 0.7f),
                maximumHeightMultiplier = 4,
            ),
        ),
        // Optional, preserving the existing file-driven behavior: not enabled when no file exists.
        approvedLegacyNudges = mapOf("cluesFoundLabel" to (-5 to 0)),
    )

    private const val KNOWN_LINGO = "da9c8c41c04cedc8b4cfd30f95f2c0aa905f207238d2ee9e8fd082daf2e5a718"
    private val verifiedBundles = setOf(
        // Local v1 and current v2 (with/without cover). Fingerprints are hashes, not game content.
        "35d9939d62e9752aac82d0b90a450ad84586f0508f98501b44e980717e3eb69d",
        "59eaebfb1dc6b258872eccfc1532ae85895c51f9c7f3bf4c74d66bb49a212d6c",
        // Archived bundles already imported on WSA, reviewed before the migration.
        "83da39a50fe7f98a77254b33b3e27619176ba0d2012220f3ffed3212cf2f7914",
        "e069d304f1a54e31278322cae5db15115cc13e5dd01212c013613f596dbc9fa5",
        "ab79bed89f4b16fa469676a8bed61d9272b14f9fd5e01ffda65dda514911a0a5",
        "e632d65de5883f939edc810a7ecb55874ec48374e3267da91ba618c6eab9ab99",
    ).map { BundleFingerprint(it, KNOWN_LINGO) }.toSet()

    fun select(fingerprint: BundleFingerprint): DirectorPresentationProfile =
        if (fingerprint in verifiedBundles) HUNTSVILLE_ES else GENERIC

    fun resolve(content: DirectorContent): DirectorPresentationProfile = select(BundleFingerprint.of(
        content.read("movie.json") ?: error("movie bundle missing"),
        content.read("lingo.json") ?: error("Lingo bundle missing"),
    ))
}
