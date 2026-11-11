import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keyProps = Properties().apply {
    // 签名信息：仓库根目录的 keystore.properties（已 gitignore），或环境变量 LP_KEYSTORE_PROPERTIES 指向的文件
    val f = System.getenv("LP_KEYSTORE_PROPERTIES")?.let { file(it) } ?: rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.yuxiximyxx.libraryplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.yuxiximyxx.libraryplus"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            val path = keyProps.getProperty("storeFile") ?: System.getenv("LP_STORE_FILE")
            if (path != null) {
                storeFile = file(path)
                storePassword = keyProps.getProperty("storePassword") ?: System.getenv("LP_STORE_PASSWORD")
                keyAlias = keyProps.getProperty("keyAlias") ?: System.getenv("LP_KEY_ALIAS")
                keyPassword = keyProps.getProperty("keyPassword") ?: System.getenv("LP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += listOf("META-INF/*.version", "META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*", "kotlin/**", "DebugProbesKt.bin")
        jniLibs.useLegacyPackaging = false
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "LibraryPlus-${versionName}-${buildType.name}.apk"
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("org.luckypray:dexkit:2.3.0")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
    implementation("androidx.activity:activity-compose:1.12.4")
}
