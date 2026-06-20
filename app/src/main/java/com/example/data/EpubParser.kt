package com.example.data

import android.util.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Stack
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class EpubParser {

    data class ParsedBook(
        val title: String,
        val author: String,
        val chapters: List<ParsedChapter>
    )

    data class ParsedChapter(
        val title: String,
        val index: Int,
        val text: String
    )

    fun parseEpub(inputStream: InputStream): ParsedBook {
        // Step 1: Read all files in ZIP in one single-pass to memory
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var len: Int
                    while (zis.read(buffer).also { len = it } != -1) {
                        out.write(buffer, 0, len)
                    }
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    entries[path] = out.toByteArray()
                }
                entry = zis.nextEntry
            }
        }

        if (entries.isEmpty()) {
            throw Exception("The file is empty or is not a valid ZIP/EPUB format.")
        }

        // Search for container.xml
        val containerPath = entries.keys.firstOrNull { it.endsWith("container.xml", ignoreCase = true) }
            ?: throw Exception("Invalid EPUB: META-INF/container.xml is missing.")

        val containerBytes = entries[containerPath] ?: throw Exception("Could not read container.xml")
        val containerDoc = parseXmlDoc(containerBytes)
        
        val rootfiles = getElementsByTagNameAny(containerDoc, "rootfile")
        if (rootfiles.isEmpty()) {
            throw Exception("No package root file found in container.xml")
        }
        val opfPath = rootfiles[0].getAttribute("full-path").replace('\\', '/').trimStart('/')
        val opfBytes = entries[opfPath] ?: throw Exception("Root OPF file not found at path: $opfPath")

        val opfDoc = parseXmlDoc(opfBytes)
        
        // Extract Metadata
        val titles = getElementsByTagNameAny(opfDoc, "title")
        val finalTitle = if (titles.isNotEmpty()) titles[0].textContent?.trim() ?: "Untitled Book" else "Untitled Book"

        val creators = getElementsByTagNameAny(opfDoc, "creator")
        val finalAuthor = if (creators.isNotEmpty()) creators[0].textContent?.trim() ?: "Unknown Author" else "Unknown Author"

        // OPF Base Directory
        val opfDir = getParentDirectory(opfPath)

        // Extract Manifest Items
        val manifestItems = getElementsByTagNameAny(opfDoc, "item")
        val manifestMap = mutableMapOf<String, String>() // id -> resolvedPath
        for (item in manifestItems) {
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            if (id.isNotEmpty() && href.isNotEmpty()) {
                val decodedHref = java.net.URLDecoder.decode(href, "UTF-8")
                manifestMap[id] = resolveRelativePath(opfDir, decodedHref)
            }
        }

        // Extract Spine Ordering
        val itemrefs = getElementsByTagNameAny(opfDoc, "itemref")
        val spinePaths = mutableListOf<String>()
        for (itemref in itemrefs) {
            val idref = itemref.getAttribute("idref")
            if (idref.isNotEmpty()) {
                val resolvedPath = manifestMap[idref]
                if (resolvedPath != null) {
                    spinePaths.add(resolvedPath)
                }
            }
        }

        if (spinePaths.isEmpty()) {
            throw Exception("EPUB spine reading order is completely empty.")
        }

        // Parse Chapters
        val chapters = mutableListOf<ParsedChapter>()
        var chapterIndex = 0
        for (path in spinePaths) {
            val chapterBytes = entries[path]
            if (chapterBytes != null) {
                val (chapterTitle, plainText) = extractChapterTitleAndText(chapterBytes, chapterIndex + 1)
                // Only keep chapters that actually have readable words
                if (plainText.isNotBlank()) {
                    chapters.add(ParsedChapter(chapterTitle, chapterIndex, plainText))
                    chapterIndex++
                }
            } else {
                Log.w("EpubParser", "Missing chapter file in zip: $path")
            }
        }

        if (chapters.isEmpty()) {
            throw Exception("No readable text found inside any EPUB chapter.")
        }

        return ParsedBook(finalTitle, finalAuthor, chapters)
    }

    private fun parseXmlDoc(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isCoalescing = true
            isNamespaceAware = false
            isValidating = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun getElementsByTagNameAny(doc: Document, tagName: String): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val localName = node.localName ?: ""
                val nodeName = node.nodeName ?: ""
                if (localName.equals(tagName, ignoreCase = true) ||
                    nodeName.equals(tagName, ignoreCase = true) ||
                    nodeName.endsWith(":$tagName", ignoreCase = true)) {
                    list.add(node)
                }
            }
        }
        return list
    }

    private fun getParentDirectory(path: String): String {
        val lastSlash = path.lastIndexOf('/')
        return if (lastSlash != -1) path.substring(0, lastSlash + 1) else ""
    }

    private fun resolveRelativePath(baseDir: String, relativePath: String): String {
        val fullPath = if (baseDir.isNotEmpty()) "$baseDir$relativePath" else relativePath
        val parts = fullPath.split("/")
        val resolvedParts = Stack<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) {
                continue
            } else if (part == "..") {
                if (resolvedParts.isNotEmpty()) {
                    resolvedParts.pop()
                }
            } else {
                resolvedParts.add(part)
            }
        }
        return resolvedParts.joinToString("/")
    }

    private fun extractChapterTitleAndText(htmlBytes: ByteArray, fallbackChapterIndex: Int): Pair<String, String> {
        val htmlStr = String(htmlBytes, Charsets.UTF_8)
        val plainText = stripHtml(htmlStr)
        
        var title = "Chapter $fallbackChapterIndex"
        try {
            val doc = parseXmlDoc(htmlBytes)
            val titleTags = listOf("title", "h1", "h2", "h3")
            for (tag in titleTags) {
                val elements = getElementsByTagNameAny(doc, tag)
                if (elements.isNotEmpty()) {
                    val text = elements[0].textContent?.trim()
                    if (!text.isNullOrBlank()) {
                        title = text
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Standard backup regex-based extractor
            val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            val h1Regex = Regex("<h1[^>]*?>(.*?)</h1>", RegexOption.IGNORE_CASE)
            val match = titleRegex.find(htmlStr) ?: h1Regex.find(htmlStr)
            if (match != null) {
                val text = stripHtml(match.groupValues[1]).trim()
                if (text.isNotBlank()) {
                    title = text
                }
            }
        }
        
        if (title.length > 80) {
            title = title.substring(0, 77) + "..."
        }
        
        return Pair(title, plainText)
    }

    private fun stripHtml(html: String): String {
        var text = html
        text = text.replace(Regex("<script[^>]*?>[\\s\\S]*?<\\/script>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("<style[^>]*?>[\\s\\S]*?<\\/style>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("<(p|div|br|li|h1|h2|h3|h4|h5|h6)[^>]*?>", RegexOption.IGNORE_CASE), " \n ")
        text = text.replace(Regex("<[^>]*?>"), " ")
        
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            
        text = text.replace(Regex("\\s+"), " ")
        return text.trim()
    }
}
