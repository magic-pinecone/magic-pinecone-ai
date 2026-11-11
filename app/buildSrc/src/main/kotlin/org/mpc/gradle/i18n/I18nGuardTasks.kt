package org.mpc.gradle.i18n

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

private data class LocaleResources(
    val strings: Map<String, List<String>>,
    val plurals: Map<String, Map<String, List<String>>>,
)

@DisableCachingByDefault(because = "This verification task reads resources and produces no output.")
abstract class CheckI18nKeys : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceRoot: DirectoryProperty

    @TaskAction
    fun check() {
        val root = resourceRoot.get().asFile
        val requiredLocales = setOf("base", "zh-rTW", "vi")
        if (!root.isDirectory) {
            throw GradleException("Missing moko resource root: ${root.path}")
        }
        val localeNames = root.listFiles()
            // moko keeps bundled files beside the locales; that folder holds no translations.
            ?.filter { it.isDirectory && it.name != "files" }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        if (!localeNames.containsAll(requiredLocales)) {
            val missingLocales = requiredLocales - localeNames.toSet()
            throw GradleException(
                "Missing moko locale directories ${missingLocales.sorted().joinToString()}; add them under ${root.path}.",
            )
        }

        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val resourcesByLocale = localeNames.associateWith { locale ->
            val localeRoot = root.resolve(locale)
            val strings = linkedMapOf<String, List<String>>()
            val plurals = linkedMapOf<String, Map<String, List<String>>>()
            localeRoot.walkTopDown()
                .filter { file ->
                    file.isFile && file.extension == "xml" &&
                        (file.name.startsWith("strings") || file.name.startsWith("plurals"))
                }
                .sortedBy { it.invariantSeparatorsPath }
                .forEach { file ->
                    val document = factory.newDocumentBuilder().parse(file)
                    val stringNodes = document.getElementsByTagName("string")
                    for (index in 0 until stringNodes.length) {
                        val element = stringNodes.item(index) as Element
                        strings[element.getAttribute("name")] = formatArguments(element.textContent)
                    }

                    val pluralNodes = document.getElementsByTagName("plurals")
                    for (index in 0 until pluralNodes.length) {
                        val element = pluralNodes.item(index) as Element
                        val name = element.getAttribute("name")
                        val itemNodes = element.getElementsByTagName("item")
                        val quantityArguments = linkedMapOf<String, List<String>>()
                        for (itemIndex in 0 until itemNodes.length) {
                            val item = itemNodes.item(itemIndex) as Element
                            quantityArguments[item.getAttribute("quantity")] = formatArguments(item.textContent)
                        }
                        plurals[name] = quantityArguments
                    }
                }
            LocaleResources(strings, plurals)
        }

        val violations = mutableListOf<String>()
        checkKeyParity("String", localeNames, resourcesByLocale.mapValues { it.value.strings }, violations)
        checkKeyParity("Plural", localeNames, resourcesByLocale.mapValues { it.value.plurals }, violations)

        val stringKeys = resourcesByLocale.values.flatMap { it.strings.keys }.toSortedSet()
        for (key in stringKeys) {
            val expected = resourcesByLocale.getValue("base").strings[key]
            if (expected != null) {
                resourcesByLocale.forEach { (locale, resources) ->
                    val actual = resources.strings[key]
                    if (actual != null && actual != expected) {
                        violations +=
                            "String '$key' in locale '$locale' has different %s/%d placeholders; match the other translations (see docs/i18n-plan.md)."
                    }
                }
            }
        }

        val pluralKeys = resourcesByLocale.values.flatMap { it.plurals.keys }.toSortedSet()
        for (key in pluralKeys) {
            val expected = resourcesByLocale.getValue("base").plurals[key]?.get("other")
                ?: resourcesByLocale.values.firstNotNullOfOrNull { it.plurals[key]?.get("other") }
                ?: resourcesByLocale.values.firstNotNullOfOrNull { it.plurals[key]?.values?.firstOrNull() }
            if (expected != null) {
                resourcesByLocale.forEach { (locale, resources) ->
                    resources.plurals[key]?.forEach { (quantity, actual) ->
                        if (actual != expected) {
                            violations +=
                                "Plural '$key' quantity '$quantity' in locale '$locale' has different %s/%d placeholders; match the other translations (see docs/i18n-plan.md)."
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    separator = "\n",
                    prefix = "Moko resources must keep keys and format placeholders in parity:\n",
                ),
            )
        }
    }

    private fun <T> checkKeyParity(
        kind: String,
        localeNames: List<String>,
        keysets: Map<String, Map<String, T>>,
        violations: MutableList<String>,
    ) {
        val keys = keysets.values.flatMap { it.keys }.toSortedSet()
        for (key in keys) {
            val presentLocales = localeNames.filter { key in keysets.getValue(it) }
            if (presentLocales.size != localeNames.size) {
                val missingLocales = localeNames - presentLocales.toSet()
                violations +=
                    "$kind '$key' is missing from ${missingLocales.joinToString()}; add the translation to each locale's matching resource file (see docs/i18n-plan.md)."
            }
        }
    }

    private fun formatArguments(text: String): List<String> {
        val placeholder = Regex("""%(?:(\d+)\$)?[sd]""")
        var implicitIndex = 0
        var index = 0
        val arguments = mutableListOf<String>()
        while (index < text.length) {
            if (text[index] != '%') {
                index++
                continue
            }
            if (text.startsWith("%%", index)) {
                index += 2
                continue
            }
            val match = placeholder.matchAt(text, index)
            if (match == null) {
                index++
                continue
            }
            val argumentIndex = match.groups[1]?.value?.toInt() ?: ++implicitIndex
            arguments += "$argumentIndex:${match.value.last()}"
            index = match.range.last + 1
        }
        return arguments.sorted()
    }
}

@DisableCachingByDefault(because = "This verification task reads source files and produces no output.")
abstract class CheckHardcodedUiText : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedUiRoot: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidUiRoot: DirectoryProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowlistFile: RegularFileProperty

    @TaskAction
    fun check() {
        val sharedRoot = sharedUiRoot.get().asFile
        val androidRoot = androidUiRoot.get().asFile
        val repoRootPath = repositoryRoot.get().asFile.toPath()
        val file = allowlistFile.get().asFile
        val sourceFiles = (sharedRoot.walkTopDown() + androidRoot.walkTopDown())
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val literalLinesByPath = sourceFiles.associate { source ->
            repoRootPath.relativize(source.toPath()).toString().replace('\\', '/') to chineseStringLines(source.readText())
        }
        val entries = file.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val duplicateEntries = entries.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val allowlisted = entries.toSet()
        val staleEntries = allowlisted - literalLinesByPath.filterValues { it.isNotEmpty() }.keys
        val unlistedFiles = literalLinesByPath.filter { (path, lines) -> lines.isNotEmpty() && path !in allowlisted }
        val violations = buildList {
            if (duplicateEntries.isNotEmpty()) {
                add("Duplicate allowlist entries: ${duplicateEntries.sorted().joinToString()}")
            }
            if (staleEntries.isNotEmpty()) {
                add("Remove allowlist entries with no Chinese string literals: ${staleEntries.sorted().joinToString()}")
            }
            if (unlistedFiles.isNotEmpty()) {
                addAll(
                    unlistedFiles.toSortedMap().map { (path, lines) ->
                        "$path:${lines.joinToString()} has Chinese string literals; move UI text to moko-resources and see docs/i18n-plan.md."
                    },
                )
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    separator = "\n",
                    prefix = "Hardcoded Chinese UI text check failed; see docs/i18n-plan.md:\n",
                ),
            )
        }
    }

    private fun chineseStringLines(source: String): List<Int> {
        val chineseCharacter = Regex("[\\u4E00-\\u9FFF]")
        val matches = mutableListOf<Int>()
        var index = 0
        var line = 1
        while (index < source.length) {
            if (source.startsWith("//", index)) {
                index += 2
                while (index < source.length && source[index] != '\n') index++
                continue
            }
            if (source.startsWith("/*", index)) {
                var depth = 1
                index += 2
                while (index < source.length && depth > 0) {
                    when {
                        source.startsWith("/*", index) -> {
                            depth++
                            index += 2
                        }
                        source.startsWith("*/", index) -> {
                            depth--
                            index += 2
                        }
                        else -> {
                            if (source[index] == '\n') line++
                            index++
                        }
                    }
                }
                continue
            }
            if (source.startsWith("\"\"\"", index)) {
                val literalLine = line
                val start = index + 3
                index = start
                while (index < source.length && !source.startsWith("\"\"\"", index)) {
                    if (source[index] == '\n') line++
                    index++
                }
                if (chineseCharacter.containsMatchIn(source.substring(start, index))) matches += literalLine
                index = (index + 3).coerceAtMost(source.length)
                continue
            }
            if (source[index] == '"') {
                val literalLine = line
                val start = index + 1
                index = start
                while (index < source.length) {
                    when {
                        source[index] == '\\' -> {
                            if (index + 1 < source.length && source[index + 1] == '\n') line++
                            index = (index + 2).coerceAtMost(source.length)
                        }
                        source[index] == '"' -> break
                        else -> {
                            if (source[index] == '\n') line++
                            index++
                        }
                    }
                }
                if (chineseCharacter.containsMatchIn(source.substring(start, index))) matches += literalLine
                index = (index + 1).coerceAtMost(source.length)
                continue
            }
            if (source[index] == '\'') {
                index++
                while (index < source.length) {
                    if (source[index] == '\\') {
                        index = (index + 2).coerceAtMost(source.length)
                    } else if (source[index++] == '\'') {
                        break
                    }
                }
                continue
            }
            if (source[index] == '\n') line++
            index++
        }
        return matches
    }
}
