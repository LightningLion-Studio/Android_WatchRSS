import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("shot") version "6.1.0"
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreProperties = keystorePropertiesFile.exists()
if (hasKeystoreProperties) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val useIsolatedInstrumentation = providers
    .gradleProperty("watchrss.instrumentation.orchestrator")
    .orNull
    ?.toBoolean()
    ?: false
val clearPackageDataForInstrumentation = providers
    .gradleProperty("watchrss.instrumentation.clearPackageData")
    .orNull
    ?.toBoolean()
    ?: useIsolatedInstrumentation

android {
    namespace = "com.lightningstudio.watchrss"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.lightningstudio.watchrss"
        minSdk = 30
        targetSdk = 34
        versionCode = 39
        versionName = "1.3.2-12"
        buildConfigField("String", "WATCHRSS_OPENPANEL_CLIENT_ID", "\"3b151c92-b189-48a3-ae77-148db3235ca1\"")
        buildConfigField("String", "WATCHRSS_OPENPANEL_CLIENT_SECRET", "\"\"")
        buildConfigField("String", "WATCHRSS_OPENPANEL_API_URL", "\"http://10.0.2.2:3001\"")
        buildConfigField("boolean", "ENABLE_RUNTIME_PERF_MONITOR", "false")
        buildConfigField("boolean", "ENABLE_WATCH_DEBUG_MASK", "false")
        buildConfigField("String", "WATCHRSS_BACKEND_URL", "\"https://sly-data-plane.watchrss.cn\"")

        testInstrumentationRunner = "com.karumi.shot.ShotTestRunner"
        if (clearPackageDataForInstrumentation) {
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
        }
    }

    signingConfigs {
        if (hasKeystoreProperties) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }
    buildTypes {
        debug {
            manifestPlaceholders += mapOf("debugActivityExported" to "false")
            if (hasKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            manifestPlaceholders += mapOf("debugActivityExported" to "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("profileableRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            if (hasKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        animationsDisabled = true
        if (useIsolatedInstrumentation) {
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.profileinstaller)
    implementation(project(":sdk:bili"))
    implementation(project(":sdk:douyin"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.rssparser)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.jsoup)
    implementation(libs.jtransforms)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.swipe.reveal.layout) {
        exclude(group = "com.android.support", module = "support-v4")
    }
    implementation(libs.zxing.core)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc10")
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.nanohttpd)
    implementation(libs.androidx.security.crypto)
    ksp(libs.sqlite.jdbc)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.shot.android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    if (useIsolatedInstrumentation) {
        androidTestUtil("androidx.test:orchestrator:1.4.2")
    }
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.matching { it.name == "connectedDebugAndroidTest" }.configureEach {
    finalizedBy("installDebug")
}
