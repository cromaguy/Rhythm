// SPDX-License-Identifier: GPL-3.0-or-later

package kr.co.metadata.crawler

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fields that can be learned by clicking matching elements on two different pages. */
enum class CatalogField(val kind: EntityKind, val image: Boolean = false) {
    ARTIST_NAME(EntityKind.ARTIST),
    ARTIST_DESCRIPTION(EntityKind.ARTIST),
    ARTIST_IMAGE(EntityKind.ARTIST, image = true),
    ARTIST_DEBUT_DATE(EntityKind.ARTIST),
    ARTIST_GENRES(EntityKind.ARTIST),
    ALBUM_TITLE(EntityKind.ALBUM),
    ALBUM_DESCRIPTION(EntityKind.ALBUM),
    ALBUM_ARTISTS(EntityKind.ALBUM),
    ALBUM_IMAGE(EntityKind.ALBUM, image = true),
    ALBUM_RELEASE_DATE(EntityKind.ALBUM),
    ALBUM_GENRES(EntityKind.ALBUM),
    TRACK_TITLE(EntityKind.TRACK),
    TRACK_ARTISTS(EntityKind.TRACK),
    TRACK_LYRICS(EntityKind.TRACK),
    TRACK_IMAGE(EntityKind.TRACK, image = true),
    TRACK_RELEASE_DATE(EntityKind.TRACK),
    TRACK_GENRES(EntityKind.TRACK),
    TRACK_ALBUM_TITLE(EntityKind.TRACK),
}

enum class ExtractionMode { TEXT, IMAGE_URL, HREF, ATTRIBUTE }

data class SelectorCandidate(
    val selector: String,
    val matchCount: Int,
    val selectsClickedElement: Boolean,
    /** Heuristic stability score from 0..100; two-page validation remains authoritative. */
    val stabilityScore: Int,
)

data class ElementSelection(
    val pageUrl: String,
    val tagName: String,
    val textSample: String,
    val attributes: Map<String, String>,
    val candidates: List<SelectorCandidate>,
)

object SelectionMessageCodec {
    fun decode(message: String): ElementSelection {
        require(message.length in 1..65_536) { "Selection message exceeds the allowed size." }
        val root = Json.parseToJsonElement(message).jsonObject
        fun value(key: String): String = root[key]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Selection message is missing '$key'.")
        val attributes = root["attributes"]?.jsonObject?.mapNotNull { (key, value) ->
            value.jsonPrimitive.contentOrNull?.let { key to it }
        }?.toMap().orEmpty()
        val rawCandidates = root["candidates"]?.jsonArray.orEmpty()
        require(rawCandidates.size <= 30) { "Selection contains too many selector candidates." }
        val candidates = rawCandidates.map { item ->
            val obj = item.jsonObject
            SelectorCandidate(
                selector = obj["selector"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalArgumentException("Candidate has no selector."),
                matchCount = obj["matchCount"]?.jsonPrimitive?.intOrNull ?: 0,
                selectsClickedElement = obj["selectsClickedElement"]?.jsonPrimitive?.booleanOrNull ?: false,
                stabilityScore = obj["stabilityScore"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }.orEmpty()
        return ElementSelection(
            pageUrl = value("pageUrl"),
            tagName = value("tagName"),
            textSample = value("textSample"),
            attributes = attributes,
            candidates = candidates,
        )
    }
}

data class SelectorRule(
    val field: CatalogField,
    val selector: String,
    val extraction: ExtractionMode,
    val attributeName: String? = null,
    val confidence: Double,
    val verifiedPageUrls: List<String>,
    val sampleValues: List<String> = emptyList(),
)

data class DynamicSiteProfile(
    val id: String,
    val siteOrigin: String,
    val displayName: String,
    val kind: EntityKind,
    val rules: List<SelectorRule>,
    val createdAt: String = Instant.now().toString(),
    val schemaVersion: Int = 1,
)

data class DynamicFieldValue(
    val field: CatalogField,
    val value: String,
    val sourceUrl: String,
    val profileId: String,
    val selector: String,
    val confidence: Double,
    val observedAt: String = Instant.now().toString(),
)

/**
 * A user-driven, two-page calibration session. Record every desired field on page 1,
 * then open a different item of the same type and select the matching fields again.
 */
class SelectorProfileWizard(
    firstPageUrl: String,
    private val kind: EntityKind,
    private val displayName: String,
) {
    private val firstOrigin = originOf(firstPageUrl)
    private val firstPage = canonicalPage(firstPageUrl)
    private val firstSelections = linkedMapOf<CatalogField, ElementSelection>()
    private val verifiedRules = linkedMapOf<CatalogField, SelectorRule>()
    private var secondPage: String? = null
    private var secondOrigin: String? = null

    val isVerifyingSecondPage: Boolean get() = secondPage != null
    val selectedFields: Set<CatalogField> get() = firstSelections.keys.toSet()
    val verifiedFields: Set<CatalogField> get() = verifiedRules.keys.toSet()

    fun recordFirstPage(field: CatalogField, selection: ElementSelection) {
        check(secondPage == null) { "First-page selection is closed; start a new wizard to add more fields." }
        require(field.kind == kind) { "$field does not belong to $kind" }
        require(canonicalPage(selection.pageUrl) == firstPage) { "Selection must come from the first page loaded in this session." }
        requireUsableSelection(selection)
        firstSelections[field] = selection
    }

    fun beginSecondPage(url: String) {
        require(firstSelections.isNotEmpty()) { "Select at least one field on the first page first." }
        val page = canonicalPage(url)
        val origin = originOf(url)
        require(origin == firstOrigin) { "Verification page must be on the same origin as the first page." }
        require(page != firstPage) { "Choose a different item page for cross-page verification." }
        verifiedRules.clear()
        secondPage = page
        secondOrigin = origin
    }

    /** Returns the verified rule; throws with an actionable message if no selector generalizes. */
    fun recordSecondPage(field: CatalogField, selection: ElementSelection): SelectorRule {
        require(field.kind == kind) { "$field does not belong to $kind" }
        val expectedPage = checkNotNull(secondPage) { "Call beginSecondPage() before recording verification selections." }
        require(canonicalPage(selection.pageUrl) == expectedPage) { "Selection must come from the second page loaded in this session." }
        require(originOf(selection.pageUrl) == secondOrigin) { "Verification page origin changed." }
        require(firstSelections.containsKey(field)) { "Select $field on the first page before verifying it." }
        requireUsableSelection(selection)

        verifiedRules.remove(field)
        val first = firstSelections.getValue(field)
        val firstBySelector = first.candidates.asSequence()
            .filter { it.isUniqueSelection() }
            .associateBy { it.selector }
        val common = selection.candidates.asSequence()
            .filter { it.isUniqueSelection() }
            .mapNotNull { second -> firstBySelector[second.selector]?.let { it to second } }
            .filter { (a, b) -> isSafeCssSelector(a.selector) && a.selector == b.selector }
            .toList()

        val best = common.maxWithOrNull(
            compareBy<Pair<SelectorCandidate, SelectorCandidate>> { minOf(it.first.stabilityScore, it.second.stabilityScore) }
                .thenBy { (it.first.stabilityScore + it.second.stabilityScore) / 2 }
                .thenBy { -it.first.selector.length },
        ) ?: throw IllegalArgumentException(
            "No CSS selector uniquely matched both selected elements. Pick a stable title/image node, " +
                "or restart with two pages that share the same layout."
        )

        val extraction = if (field.image) ExtractionMode.IMAGE_URL else ExtractionMode.TEXT
        val score = minOf(best.first.stabilityScore, best.second.stabilityScore).coerceIn(0, 100)
        val rule = SelectorRule(
            field = field,
            selector = best.first.selector,
            extraction = extraction,
            confidence = (0.50 + 0.49 * score / 100.0).coerceIn(0.5, 0.99),
            verifiedPageUrls = listOf(first.pageUrl, selection.pageUrl),
            // Do not persist selected text (which may be copyrighted lyrics or personal page content).
            sampleValues = emptyList(),
        )
        verifiedRules[field] = rule
        return rule
    }

    fun buildProfile(): DynamicSiteProfile {
        check(secondPage != null) { "Verify selections on a second page before saving the profile." }
        check(firstSelections.isNotEmpty()) { "No fields have been selected." }
        val missing = firstSelections.keys - verifiedRules.keys
        check(missing.isEmpty()) { "Verify these fields on the second page before saving: ${missing.joinToString()}" }
        return DynamicSiteProfile(
            id = UUID.randomUUID().toString(),
            siteOrigin = firstOrigin,
            displayName = displayName,
            kind = kind,
            rules = verifiedRules.values.toList(),
        )
    }

    private fun requireUsableSelection(selection: ElementSelection) {
        require(originOf(selection.pageUrl) == firstOrigin) { "Selection came from a different site origin." }
        require(selection.tagName.isNotBlank()) { "Selected DOM element has no tag name." }
        require(selection.candidates.any { it.isUniqueSelection() && isSafeCssSelector(it.selector) }) {
            "The clicked element has no unique, safe CSS selector. Pick a more specific visible element."
        }
    }
}

/** Applies a verified profile to HTML captured from the same site (HTTP or a rendered WebView DOM). */
object DynamicProfileExtractor {
    fun extract(profile: DynamicSiteProfile, pageUrl: String, html: String): List<DynamicFieldValue> {
        require(originOf(pageUrl) == profile.siteOrigin) { "Profile ${profile.id} is for ${profile.siteOrigin}, not ${originOf(pageUrl)}." }
        val document = Jsoup.parse(html, pageUrl)
        return profile.rules.mapNotNull { rule ->
            require(rule.field.kind == profile.kind) { "Profile field ${rule.field} does not match ${profile.kind}." }
            require(isSafeCssSelector(rule.selector)) { "Unsafe or unsupported CSS selector in profile." }
            val matches = runCatching { document.select(rule.selector) }.getOrNull() ?: return@mapNotNull null
            if (matches.size != 1) return@mapNotNull null
            val element = matches.firstOrNull() ?: return@mapNotNull null
            if ((element.parents().toList() + element).any {
                    it.tagName() in setOf("script", "style", "iframe", "input", "textarea", "select", "button") ||
                        it.hasAttr("contenteditable")
                }) return@mapNotNull null
            if (element.select("input, textarea, select, [contenteditable]").isNotEmpty()) return@mapNotNull null
            val value = extractValue(element, rule, pageUrl) ?: return@mapNotNull null
            DynamicFieldValue(rule.field, value, pageUrl, profile.id, rule.selector, rule.confidence)
        }
    }

    private fun extractValue(element: Element, rule: SelectorRule, pageUrl: String): String? {
        val raw = when (rule.extraction) {
            ExtractionMode.TEXT -> if (rule.field == CatalogField.TRACK_LYRICS) {
                element.wholeText().replace('\u00a0', ' ').replace(Regex("[ \\t]+"), " ")
                    .replace(Regex(" *\\n *"), "\n").trim()
            } else element.text().replace(Regex("\\s+"), " ").trim()
            ExtractionMode.HREF -> element.attrOrFirstChild("href")
            ExtractionMode.ATTRIBUTE -> rule.attributeName?.let { element.attrOrFirstChild(it) }
            ExtractionMode.IMAGE_URL -> imageUrl(element)
        }?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (rule.extraction) {
            ExtractionMode.IMAGE_URL, ExtractionMode.HREF -> runCatching { URI(pageUrl).resolve(raw).toString() }.getOrDefault(raw)
            else -> raw
        }
    }

    private fun Element.attrOrFirstChild(name: String): String? =
        attr(name).takeIf { it.isNotBlank() } ?: selectFirst("[$name]")?.attr(name)?.takeIf { it.isNotBlank() }

    private fun imageUrl(element: Element): String? {
        fun Element.candidate(): String? = listOf("src", "data-src", "data-original", "data-lazy-src", "content")
            .firstNotNullOfOrNull { attr(it).takeIf(String::isNotBlank) }
        element.candidate()?.let { return it }
        element.selectFirst("img[src], img[data-src], img[data-original], img[data-lazy-src], meta[content]")
            ?.candidate()?.let { return it }
        val style = element.attr("style") + " " + element.selectFirst("[style]")?.attr("style").orEmpty()
        return Regex("url\\(['\"]?(.*?)['\"]?\\)", RegexOption.IGNORE_CASE).find(style)?.groupValues?.getOrNull(1)
    }
}

/** Fetch-and-extract convenience API for ordinary public HTML pages. Use WebView outerHTML for JS-only pages. */
class DynamicProfileCrawler(private val pageClient: PageClient = PageClient()) {
    suspend fun crawl(profile: DynamicSiteProfile, pageUrl: String): List<DynamicFieldValue>? = withContext(Dispatchers.IO) {
        val html = pageClient.get(pageUrl) ?: return@withContext null
        DynamicProfileExtractor.extract(profile, pageUrl, html)
    }
}

/** JSON profile codec without a compiler-plugin requirement; profiles can be stored in Room/DataStore. */
object SiteProfileCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(profile: DynamicSiteProfile): String = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "schemaVersion" to JsonPrimitive(profile.schemaVersion),
                "id" to JsonPrimitive(profile.id),
                "siteOrigin" to JsonPrimitive(profile.siteOrigin),
                "displayName" to JsonPrimitive(profile.displayName),
                "kind" to JsonPrimitive(profile.kind.name),
                "createdAt" to JsonPrimitive(profile.createdAt),
                "rules" to JsonArray(profile.rules.map { rule ->
                    JsonObject(
                        mapOf(
                            "field" to JsonPrimitive(rule.field.name),
                            "selector" to JsonPrimitive(rule.selector),
                            "extraction" to JsonPrimitive(rule.extraction.name),
                            "attributeName" to (rule.attributeName?.let(::JsonPrimitive) ?: JsonPrimitive("")),
                            "confidence" to JsonPrimitive(rule.confidence),
                            "verifiedPageUrls" to JsonArray(rule.verifiedPageUrls.map(::JsonPrimitive)),
                            "sampleValues" to JsonArray(rule.sampleValues.map(::JsonPrimitive)),
                        )
                    )
                }),
            )
        ),
    )

    fun decode(source: String): DynamicSiteProfile {
        require(source.length <= 1_000_000) { "Profile is too large." }
        val root = Json.parseToJsonElement(source).jsonObject
        require((root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1) == 1) { "Unsupported profile version." }
        fun String.string(): String = root[this]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Profile is missing '$this'.")
        val kind = EntityKind.valueOf("kind".string())
        val origin = "siteOrigin".string()
        require(originOf(origin) == origin) { "Profile siteOrigin must be a normalized origin." }
        val rules = root["rules"]?.jsonArray?.map { element ->
            val item = element.jsonObject
            fun field(name: String): String = item[name]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Rule is missing '$name'.")
            val catalogField = CatalogField.valueOf(field("field"))
            require(catalogField.kind == kind) { "Rule ${catalogField.name} is invalid for $kind." }
            val urls = item["verifiedPageUrls"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val selector = field("selector")
            require(isSafeCssSelector(selector)) { "Unsafe CSS selector in stored profile." }
            require(urls.size >= 2 && canonicalPage(urls[0]) != canonicalPage(urls[1]) &&
                originOf(urls[0]) == origin && originOf(urls[1]) == origin) {
                "Profile rule lacks distinct, same-site two-page verification."
            }
            SelectorRule(
                field = catalogField,
                selector = selector,
                extraction = ExtractionMode.valueOf(field("extraction")),
                attributeName = item["attributeName"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank),
                confidence = item["confidence"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
                    ?: throw IllegalArgumentException("Rule is missing a valid confidence."),
                verifiedPageUrls = urls,
                sampleValues = item["sampleValues"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            )
        }.orEmpty()
        require(rules.map { it.field }.distinct().size == rules.size) { "Duplicate fields in profile." }
        require(rules.isNotEmpty()) { "Profile contains no rules." }
        return DynamicSiteProfile(
            id = "id".string(),
            siteOrigin = origin,
            displayName = "displayName".string(),
            kind = kind,
            rules = rules,
            createdAt = "createdAt".string(),
            schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1,
        )
    }
}

private fun SelectorCandidate.isUniqueSelection(): Boolean =
    selector.isNotBlank() && matchCount == 1 && selectsClickedElement && stabilityScore in 0..100

private fun isSafeCssSelector(selector: String): Boolean = selector.length in 1..512 &&
    !selector.contains(Regex("(?i)<|:has\\s*\\(|:contains\\s*\\(|:matches\\s*\\(")) &&
    !selector.contains(Regex("(?i)(^|[\\s,])(script|style|iframe|input|textarea|select|password)([\\s.#:\\[]|$)"))

private fun originOf(url: String): String {
    val uri = URI(url)
    require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "Only HTTP(S) pages are supported." }
    require(uri.host != null && uri.userInfo == null) { "Invalid page URL." }
    val port = if (uri.port == -1 || (uri.scheme.equals("https", true) && uri.port == 443) || (uri.scheme.equals("http", true) && uri.port == 80)) "" else ":${uri.port}"
    return "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port"
}

private fun canonicalPage(url: String): String {
    val uri = URI(url).normalize()
    val path = uri.rawPath?.ifBlank { "/" } ?: "/"
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "${originOf(url)}$path$query"
}
