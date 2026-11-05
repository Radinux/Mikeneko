import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin est intégré à AGP 9 : pas besoin du plugin org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
}

// Version automatique : app/version.properties garde la version de la prochaine compilation release.
// Chaque assembleRelease / installRelease / bundleRelease prend cette version puis prépare la
// suivante (patch +1, versionCode +1). Les compilations debug réutilisent la version sans la changer.
// Pour une nouvelle fonction : minor +1 et patch = 0 à la main dans version.properties.
val versionFile = file("version.properties")
val versionProps = Properties().apply { versionFile.inputStream().use { load(it) } }
fun versionNumber(key: String) = versionProps.getProperty(key).trim().toInt()
val appVersionCode = versionNumber("code")
val appVersionName = "${versionNumber("major")}.${versionNumber("minor")}.${versionNumber("patch")}"
val releaseBuild = gradle.startParameter.taskNames.any { task ->
    val name = task.substringAfterLast(':')
    name.endsWith("Release") && listOf("assemble", "install", "bundle").any { name.startsWith(it) }
}
if (releaseBuild) {
    val text = versionFile.readText()
        .replace(Regex("(?m)^patch=\\d+"), "patch=${versionNumber("patch") + 1}")
        .replace(Regex("(?m)^code=\\d+"), "code=${appVersionCode + 1}")
    versionFile.writeText(text)
    println("Mikeneko $appVersionName ($appVersionCode)")
}

// Clé de publication : hors du projet (pour ne jamais la partager avec lui), dans
// ~/.config/mikeneko/signing.properties (ou le fichier indiqué par MIKENEKO_SIGNING).
// Sans elle, la release est signée avec la clé de debug, comme avant.
val releaseKeyFile = File(System.getenv("MIKENEKO_SIGNING") ?: "${System.getProperty("user.home")}/.config/mikeneko/signing.properties")
val releaseKey = Properties().apply { if (releaseKeyFile.exists()) releaseKeyFile.inputStream().use { load(it) } }
val hasReleaseKey = releaseKey.getProperty("storeFile")?.let { File(it).exists() } == true

android {
    namespace = "app.mikeneko"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.mikeneko"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode // voir version.properties
        versionName = appVersionName
    }

    // yt-dlp embarque python et ffmpeg en natif (~50 Mo par processeur) : un APK par processeur
    // plutôt qu'un seul qui les contient tous. arm64 = les téléphones (64 bits, depuis 2019),
    // x86_64 = l'émulateur. Pas de 32 bits (armeabi-v7a) : +45 Mo pour de très vieux téléphones.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
        // Une seule fois, pour passer un téléphone de la clé de debug à la clé de publication sans
        // perdre ses données : une version debug (donc lisible par adb) signée avec la clé de publication.
        // ./gradlew assembleDebug -Pmikeneko.debugWithReleaseKey
        if (hasReleaseKey && project.hasProperty("mikeneko.debugWithReleaseKey")) {
            getByName("debug") { signingConfig = signingConfigs.getByName("release") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor a besoin de java.nio sous Android 13
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // youtubedl-android doit pouvoir extraire ses binaires
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.newpipe.extractor)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)

    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
}
