// SPDX-License-Identifier: GPL-3.0-or-later

package chromahub.rhythm.app.features.local.presentation.metadata

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.data.metadata.SiteProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.metadata.crawler.*
import org.json.JSONTokener
import java.io.File
import java.net.URI
import java.util.UUID

/** Learn a site on two pages, persist verified rules, and preview a third page before import. */
class SelectorStudioActivity : ComponentActivity() {
    private lateinit var browser: SelectorWebView
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var store: SiteProfileStore
    private var kind = EntityKind.TRACK
    private var wizard: SelectorProfileWizard? = null
    private var profile: DynamicSiteProfile? = null
    private var pageReady = false
    private var extracting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SiteProfileStore(this)
        kind = runCatching { EntityKind.valueOf(savedInstanceState?.getString("kind")
            ?: intent.getStringExtra(EXTRA_KIND) ?: "TRACK") }.getOrDefault(EntityKind.TRACK)
        profile = savedInstanceState?.getString("profileId")?.let { id -> store.list().find { it.id == id } }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            fitsSystemWindows = true
        }
        status = TextView(this).apply { text = getString(R.string.catalog_studio_hint) }
        root.addView(status)
        val kinds = listOf(EntityKind.TRACK, EntityKind.ALBUM, EntityKind.ARTIST)
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@SelectorStudioActivity, android.R.layout.simple_spinner_dropdown_item,
                kinds.map { it.label(this@SelectorStudioActivity) })
            setSelection(kinds.indexOf(kind))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (kind != kinds[position]) { kind = kinds[position]; reset() }
                }
            }
        })
        address = EditText(this).apply {
            hint = "https://…"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        root.addView(address)
        fun row(vararg actions: Pair<Int, () -> Unit>) {
            val row = LinearLayout(this)
            actions.forEach { (label, action) -> row.addView(Button(this).apply {
                text = getString(label)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
            root.addView(row)
        }
        row(R.string.catalog_open to ::openAddress, R.string.catalog_profiles to ::showProfiles, R.string.catalog_restart to ::reset)
        row(R.string.catalog_verify to ::verify, R.string.catalog_save_profile to ::saveProfile, R.string.catalog_extract to ::extract)
        root.addView(Switch(this).apply {
            text = getString(R.string.catalog_pick_mode)
            isChecked = true
            setOnCheckedChangeListener { _, checked -> browser.picking = checked }
        })
        browser = SelectorWebView(this, ::chooseField,
            { toast(R.string.catalog_picker_error) },
            { url -> pageReady = true; address.setText(url) },
            { pageReady = false })
        root.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (browser.canGoBack()) browser.goBack() else finish()
            }
        })
        val initial = savedInstanceState?.getString("url") ?: intent.getStringExtra(EXTRA_URL)
        if (initial != null) { address.setText(initial); openAddress() }
        if (savedInstanceState != null && profile == null) status.setText(R.string.catalog_studio_restarted)
    }

    private fun reset() {
        wizard = null
        profile = null
        status.setText(R.string.catalog_studio_hint)
    }

    private fun openAddress() {
        val entered = address.text.toString().trim()
        val url = if (entered.contains("://")) entered else "https://$entered"
        if (!SelectorWebView.isAllowedUrl(url)) return toast(R.string.catalog_invalid_url)
        pageReady = false
        browser.loadUrl(url)
    }

    private fun chooseField(sample: ElementSelection) {
        if (!pageReady) return
        val current = wizard
        val fields = if (current?.isVerifyingSecondPage == true) current.selectedFields.toList()
            else CatalogField.entries.filter { it.kind == kind }
        AlertDialog.Builder(this).setTitle(R.string.catalog_choose_field)
            .setItems(fields.map { it.label(this) }.toTypedArray()) { _, index ->
                runCatching {
                    val session = wizard ?: SelectorProfileWizard(sample.pageUrl, kind, URI(sample.pageUrl).host).also {
                        wizard = it
                    }
                    if (session.isVerifyingSecondPage) session.recordSecondPage(fields[index], sample)
                    else session.recordFirstPage(fields[index], sample)
                    profile = null
                    status.text = getString(R.string.catalog_verification_progress,
                        session.selectedFields.size, session.verifiedFields.size)
                }.onFailure { toast(R.string.catalog_selection_failed) }
            }.show()
    }

    private fun verify() {
        if (!pageReady) return toast(R.string.catalog_wait_page)
        runCatching { checkNotNull(wizard).beginSecondPage(checkNotNull(browser.url)) }
            .onSuccess { profile = null; status.setText(R.string.catalog_verify_hint) }
            .onFailure { toast(R.string.catalog_verify_error) }
    }

    private fun saveProfile() {
        val result = runCatching { checkNotNull(wizard).buildProfile() }
            .getOrElse { return toast(R.string.catalog_incomplete_profile) }
        store.save(result)
        profile = result
        status.setText(R.string.catalog_profile_saved)
    }

    private fun showProfiles() {
        val profiles = store.list().filter { it.kind == kind }
        if (profiles.isEmpty()) return toast(R.string.catalog_no_profiles)
        AlertDialog.Builder(this).setTitle(R.string.catalog_profiles)
            .setItems(profiles.map { "${it.displayName} (${it.rules.size})" }.toTypedArray()) { _, index ->
                val saved = profiles[index]
                AlertDialog.Builder(this).setTitle(saved.displayName)
                    .setMessage(saved.siteOrigin)
                    .setPositiveButton(R.string.catalog_use_profile) { _, _ ->
                        wizard = null
                        profile = saved
                        status.setText(R.string.catalog_profile_loaded)
                        address.setText(saved.rules.first().verifiedPageUrls.first())
                        openAddress()
                    }
                    .setNegativeButton(R.string.catalog_delete_profile) { _, _ ->
                        store.delete(saved)
                        if (profile?.id == saved.id) reset()
                    }
                    .setNeutralButton(R.string.catalog_close, null).show()
            }.show()
    }

    private fun extract() {
        val saved = profile ?: return toast(R.string.catalog_incomplete_profile)
        val url = browser.url ?: return toast(R.string.catalog_wait_page)
        if (!pageReady || extracting) return toast(R.string.catalog_wait_page)
        extracting = true
        browser.evaluateJavascript("document.documentElement.outerHTML.length <= 5000000 ? document.documentElement.outerHTML : null") { encoded ->
            val html = runCatching { JSONTokener(encoded).nextValue() as String }.getOrNull()
            if (html == null) { extracting = false; toast(R.string.catalog_error); return@evaluateJavascript }
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        CatalogDocument.from(saved, DynamicProfileExtractor.extract(saved, url, html))
                    }
                    if (result.values.isEmpty()) toast(R.string.catalog_no_metadata)
                    else AlertDialog.Builder(this@SelectorStudioActivity)
                        .setTitle(R.string.catalog_preview)
                        .setMessage(result.values.joinToString("\n\n") { "${it.field.label(this@SelectorStudioActivity)}\n${it.value.take(500)}" })
                        .setPositiveButton(R.string.catalog_use_page) { _, _ -> returnDocument(result) }
                        .setNegativeButton(R.string.catalog_close, null).show()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { toast(R.string.catalog_error) }
                finally { extracting = false }
            }
        }
    }

    private fun returnDocument(document: CatalogDocument) {
        lifecycleScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    val json = CatalogDocumentCodec.encode(document)
                    require(json.length <= 1_000_000)
                    val file = File(cacheDir, "catalog-result-${UUID.randomUUID()}.json")
                    file.writeText(json)
                    file.name
                }
                setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_FILE, name))
                finish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { toast(R.string.catalog_error) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("url", browser.url)
        outState.putString("kind", kind.name)
        outState.putString("profileId", profile?.id)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() { browser.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::browser.isInitialized) browser.onResume() }
    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.destroy()
        }
        super.onDestroy()
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_KIND = "catalog.kind"
        const val EXTRA_URL = "catalog.url"
        const val EXTRA_RESULT_FILE = "catalog.result_file"
    }
}
