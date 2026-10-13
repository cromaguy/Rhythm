// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CatalogExport {
    suspend fun export(context: Context, tree: Uri): String = withContext(Dispatchers.IO) {
        val root = CatalogEnrichmentService.get(context).root
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, tree))
        val name = "T2Rhythm-metadata-${System.currentTimeMillis()}.zip"
        val target = requireNotNull(folder.createFile("application/zip", name))
        try {
            context.contentResolver.openOutputStream(target.uri)?.use { output -> ZipOutputStream(output).use { zip ->
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".new") && !it.name.endsWith(".bak") &&
                    !it.name.endsWith(".tmp") && !it.name.startsWith("batch") }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            } } ?: error("Export destination could not be opened")
            name
        } catch (error: Exception) { target.delete(); throw error }
    }
}
