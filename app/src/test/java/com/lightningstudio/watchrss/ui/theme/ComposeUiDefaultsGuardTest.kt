package com.lightningstudio.watchrss.ui.theme

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import org.junit.Assert.assertFalse
import org.junit.Test

class ComposeUiDefaultsGuardTest {

    @Test
    fun composeUiUsesBridgeForDimensionAndColorResources() {
        val uiRoot = projectRoot().resolve("app/src/main/java/com/lightningstudio/watchrss/ui")
        val allowedFiles = setOf(
            uiRoot.resolve("theme/WatchResources.kt").normalize()
        )
        val directResourceRegex = Regex("""\b(dimensionResource|colorResource)\(""")

        Files.walk(uiRoot).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .map { it.normalize() }
                .filter { it !in allowedFiles }
                .forEach { path ->
                    val source = String(Files.readAllBytes(path))
                    assertFalse(
                        "Direct Compose resource access is not allowed in ${path.invariantSeparatorsPathString}",
                        directResourceRegex.containsMatchIn(source)
                    )
                }
        }
    }

    @Test
    fun highRiskMaterialDefaultsGoThroughWatchWrappers() {
        val uiRoot = projectRoot().resolve("app/src/main/java/com/lightningstudio/watchrss/ui")
        val allowedFiles = setOf(
            uiRoot.resolve("components/WatchMaterial.kt").normalize()
        )
        val directMaterialImportRegex = Regex(
            """import androidx\.compose\.material3\.(Button|TextButton|IconButton|CircularProgressIndicator|Checkbox)\b"""
        )
        val fqcnRegex = Regex(
            """androidx\.compose\.material3\.(Button|TextButton|IconButton|CircularProgressIndicator|Checkbox)\("""
        )

        Files.walk(uiRoot).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .map { it.normalize() }
                .filter { it !in allowedFiles }
                .forEach { path ->
                    val source = String(Files.readAllBytes(path))
                    assertFalse(
                        "High-risk Material default components should use Watch wrappers in ${path.invariantSeparatorsPathString}",
                        directMaterialImportRegex.containsMatchIn(source) || fqcnRegex.containsMatchIn(source)
                    )
                }
        }
    }

    @Test
    fun roundWatchChecksStayInsideThemeLayer() {
        val uiRoot = projectRoot().resolve("app/src/main/java/com/lightningstudio/watchrss/ui")
        val allowedFiles = setOf(
            uiRoot.resolve("theme/WatchDesignTokens.kt").normalize()
        )
        val directRoundCheckRegex = Regex("""\b(isScreenRound|configuration\.isScreenRound)\b""")

        Files.walk(uiRoot).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .map { it.normalize() }
                .filter { it !in allowedFiles }
                .forEach { path ->
                    val source = String(Files.readAllBytes(path))
                    assertFalse(
                        "Direct round/square checks should stay in theme helpers: ${path.invariantSeparatorsPathString}",
                        directRoundCheckRegex.containsMatchIn(source)
                    )
                }
        }
    }

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath().normalize()
        repeat(6) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent ?: return@repeat
        }
        error("Could not locate project root from ${Paths.get("").toAbsolutePath()}")
    }
}
