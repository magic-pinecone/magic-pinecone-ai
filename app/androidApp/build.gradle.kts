import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.testBalloon)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastorePreferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.compose.materialIconsCore)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3AdaptiveNavigationSuite)
    implementation(libs.androidx.material3AdaptiveLayout)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.testBalloon.frameworkCore)
}

android {
    namespace = "org.mpc"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "org.mpc"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        // kotlinx-datetime runs on java.time, which minSdk 24 lacks without desugaring.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude {
            "/build/generated/" in it.file.invariantSeparatorsPath
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit {
        includeCategories("de.infix.testBalloon.framework.core.internal.integration.TestBalloonJUnit4Runner")
    }
}
