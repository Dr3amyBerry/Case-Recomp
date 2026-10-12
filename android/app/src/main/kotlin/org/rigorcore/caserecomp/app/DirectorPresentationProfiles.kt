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
    val matteWhiteTolerance: Int = 0,
    val scopedNudges: Map<Pair<Int, String>, Pair<Int, Int>> = emptyMap(),
) {
    /** Moved menu and case-report controls translate their input areas with the drawing. */
    val interactiveNudges: Map<Pair<Int, String>, Pair<Int, Int>>
        get() = scopedNudges.filterKeys { it.first == 19 || it.first in 189..203 }

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
    const val HUNTSVILLE_ID = "huntsville"
    const val PRIME_ID = "prime_suspects"
    const val RAVENHEARST_ID = "ravenhearst"
    const val GENERIC_ID = "director_generic"
    private const val PRIME_SOURCE = "210a54856729bf4db9a28ed85d1b41a9c08967fce50b4acca20b95f1ddff869b"
    private const val RAVENHEARST_SOURCE = "2ca01a3489399d7647c27f7ea312f33b83951f428f5c31b0f6a73ce213440174"
    private const val HUNTSVILLE_SOURCE = "0ef4a73bceae060970d2513f5f6132663843064d7106b724db46cac810a4b28d"

    /** Titles whose saves follow the original source, so a re-imported package keeps them. */
    private val stableSources = mapOf(PRIME_ID to PRIME_SOURCE, RAVENHEARST_ID to RAVENHEARST_SOURCE)

    fun supportsTitle(id: String) = id == HUNTSVILLE_ID || id in stableSources

    fun sessionStoreId(title: String, packageHash: String): String =
        storeId(title, stableSources[title] ?: "", packageHash)

    /** Content identity selects a library slot, never a visual calibration or game code. */
    fun titleId(movie: ByteArray, lingo: ByteArray): String {
        val source = (MiniJson.parse(String(movie, Charsets.UTF_8)) as? Map<*, *>)?.get("source_sha256")
        return when {
            source == PRIME_SOURCE -> PRIME_ID
            source == RAVENHEARST_SOURCE -> RAVENHEARST_ID
            source == HUNTSVILLE_SOURCE || select(BundleFingerprint.of(movie, lingo)) == HUNTSVILLE_ES -> HUNTSVILLE_ID
            else -> GENERIC_ID
        }
    }

    fun titleId(content: DirectorContent) = titleId(
        content.read("movie.json") ?: error("movie bundle missing"),
        content.read("lingo.json") ?: error("Lingo bundle missing"),
    )

    /** Keep existing Huntsville save keys. Prime and Ravenhearst updates share saves for the same original source. */
    fun storeId(title: String, source: String, packageHash: String): String {
        require(packageHash.matches(Regex("[a-f0-9]{64}")))
        val expected = stableSources[title] ?: return packageHash
        require(source == expected)
        return MessageDigest.getInstance("SHA-256").digest("$title/$source".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun storeId(content: DirectorContent, packageHash: String): String {
        val movie = content.read("movie.json") ?: error("movie bundle missing")
        val lingo = content.read("lingo.json") ?: error("Lingo bundle missing")
        val source = (MiniJson.parse(String(movie, Charsets.UTF_8)) as? Map<*, *>)?.get("source_sha256") as? String ?: ""
        return storeId(titleId(movie, lingo), source, packageHash)
    }

    val GENERIC = DirectorPresentationProfile("director-generic", 1, 1, DirectorTextPresentation())

    // Calibrations from 6c03811, 252ba53, cfeadd2 and 747d659. Values unchanged.
    // See docs/HUNTSVILLE_PRESENTATION_ISOLATION.md for evidence and retirement criteria.
    val HUNTSVILLE_ES = DirectorPresentationProfile(
        id = "huntsville-es-presentation", schemaVersion = 1, revision = 7,
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
        // Align the name and caret with the baked-in Agent caption. The same text member
        // is also used by the separately calibrated user list (sprites 277..281).
        scopedNudges = buildMap {
            putAll(mapOf(
                (33 to "enterNameField1") to (0 to 6),
                (31 to "cursor") to (12 to 0),
                (208 to "timeLimitField") to (0 to 5),
                (19 to "changeUserA") to (6 to 0),
                (19 to "changeUserB") to (6 to 0),
                (277 to "enterNameField1") to (0 to 6),
                (278 to "enterNameField2") to (0 to 6),
                (279 to "enterNameField3") to (0 to 6),
                (280 to "enterNameField4") to (0 to 6),
                (281 to "enterNameField5") to (0 to 6),
            ))
            for (case in 1..15) {
                for (state in listOf("A", "B")) put((188 + case) to "logButton$case$state", -4 to 0)
                for (icon in listOf("badge", "clock", "lock")) put((215 + case) to icon, -4 to 0)
            }
            for (case in 1..16) put(183 to "crimeCopy_$case", -4 to 0)
        },
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

    /**
     * Huntsville's name-entry offsets (field and caret, sprites 33/31) for titles sharing its
     * interface, plus the same field in their own change-user lists.
     */
    private fun nameEntryNudges(userList: IntRange): Map<Pair<Int, String>, Pair<Int, Int>> = buildMap {
        put(33 to "enterNameField1", 0 to 6)
        put(31 to "cursor", 12 to 0)
        for ((index, sprite) in userList.withIndex()) put(sprite to "enterNameField${index + 1}", 0 to 6)
    }

    val PRIME = DirectorPresentationProfile("prime-suspects-presentation", 1, 2, HUNTSVILLE_ES.text,
        matteWhiteTolerance = 12, scopedNudges = nameEntryNudges(587..591))

    // Same Director interface family as Huntsville: its general text calibration, none of its offsets.
    // Ravenhearst's own typewriter face (diary clocks, notes) is drawn a little larger.
    val RAVENHEARST = DirectorPresentationProfile("ravenhearst-presentation", 1, 3,
        HUNTSVILLE_ES.text.copy(fontRules = listOf(FontPresentationRule(listOf("americantypewriter"), sizeScale = 1.15f)) + HUNTSVILLE_ES.text.fontRules),
        scopedNudges = nameEntryNudges(772..776) + mapOf(
            // The caret sits on the name's baseline, lowered like the field.
            (31 to "cursor") to (12 to 6),
            // Side panel caption centred over its count and lowered; the count itself stays.
            (699 to "cluesPromptLabel") to (-5 to 4),
            // Leader board text below the panel's title strip.
            (788 to "highScoreField") to (0 to 18),
            // Diary clocks inside their paper strip; the limit value clear of its longer Spanish label.
            (152 to "timeLimitLabel") to (0 to 6),
            (153 to "timeLimitField") to (14 to 6),
            (154 to "timeCumulativeLabel") to (0 to 4),
            (155 to "timeCumulativeField") to (0 to 4),
        ))

    fun resolve(content: DirectorContent): DirectorPresentationProfile {
        val movie = content.read("movie.json") ?: error("movie bundle missing")
        val lingo = content.read("lingo.json") ?: error("Lingo bundle missing")
        return when (titleId(movie, lingo)) {
            PRIME_ID -> PRIME
            RAVENHEARST_ID -> RAVENHEARST
            else -> select(BundleFingerprint.of(movie, lingo))
        }
    }
}
