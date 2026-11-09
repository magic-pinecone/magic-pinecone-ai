import co.touchlab.skie.configuration.EnumInterop
import co.touchlab.skie.configuration.FlowInterop
import co.touchlab.skie.configuration.SealedInterop
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.testBalloon)
    alias(libs.plugins.skie)
}

val ktorVersion: String by project

skie {
    features {
        group {
            EnumInterop.Enabled(false)
            SealedInterop.Enabled(false)
            SuspendInterop.Enabled(false)
            FlowInterop.Enabled(false)
        }
        group("org.mpc.domain.model") {
            EnumInterop.Enabled(true)
        }
        group("org.mpc.presentation.model") {
            EnumInterop.Enabled(true)
        }
        group("org.mpc.bridge") {
            FlowInterop.Enabled(true)
        }
        group("org.mpc.IosSharedHost") {
            FlowInterop.Enabled(true)
            SuspendInterop.Enabled(true)
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = buildType != NativeBuildType.DEBUG
            export("com.mohamedrejeb.calf:calf-ui:${libs.versions.calf.get()}")
        }
    }

    androidLibrary {
        namespace = "org.mpc.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.jsoup)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            api(libs.calf.ui)
            api(libs.calf.webview)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastorePreferences)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.metrox.viewmodel.compose)
            implementation(libs.kermit)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.testBalloon.frameworkCore)
            implementation(libs.androidx.testRunner)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.testBalloon.frameworkCore)
            implementation(libs.ktor.client.mock)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexport-kdoc")
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    toolVersion = "2.0.0-alpha.5"
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

// The Android-typed pass cannot resolve iOS types, so it misreads every iosMain call; :shared:detekt still covers iosMain.
tasks.named<dev.detekt.gradle.Detekt>("detektMainAndroid") {
    exclude { "/iosMain/" in it.file.invariantSeparatorsPath }
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude {
            "/build/generated/" in it.file.invariantSeparatorsPath
        }
    }
}

val checkDesignTokens = tasks.register("checkDesignTokens") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails when shared UI outside presentation/theme reads MaterialTheme values or writes hex colors."
    val sources =
        fileTree("src/commonMain/kotlin") {
            include("**/*.kt")
            exclude("**/presentation/theme/**")
        }
    val rootDirectory = rootDir
    inputs.files(sources)
    doLast {
        val forbidden =
            Regex("""\bMaterialTheme\s*\.\s*(colorScheme|typography|shapes)\b|\bColor\(\s*(?:color\s*=\s*)?0x""")
        val whitespace = Regex("""\s+""")
        val violations =
            sources.files.sorted().flatMap { file ->
                val text = file.readText()
                forbidden.findAll(text).map { match ->
                    val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                    "${file.relativeTo(rootDirectory)}:$line: ${match.value.replace(whitespace, " ")}"
                }.toList()
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    separator = "\n",
                    prefix = "Shared UI must read design tokens, not Material theme values or hex colors:\n",
                    postfix =
                    "\nUse MpcTheme.colors, MpcTheme.typography, MpcShapes, or MpcSpacing " +
                        "(org.mpc.presentation.theme). Platform values belong in presentation/theme.",
                ),
            )
        }
    }
}

tasks.named("check") { dependsOn(checkDesignTokens) }
