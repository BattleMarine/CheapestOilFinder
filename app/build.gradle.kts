import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val kakaoSecretPropertiesFile = rootProject.file("secrets/kakao.properties")
val kakaoSecretProperties = Properties()
if (kakaoSecretPropertiesFile.exists()) {
    kakaoSecretPropertiesFile.inputStream().use { kakaoSecretProperties.load(it) }
}

val kakaoSecretTextFile = rootProject.file("secrets/kakao_native_app_key.txt")
val kakaoNativeAppKey = when {
    kakaoSecretTextFile.exists() -> kakaoSecretTextFile.readText().trim()
    kakaoSecretProperties.isNotEmpty() -> kakaoSecretProperties.getProperty("KAKAO_NATIVE_APP_KEY", "")
    else -> ""
}

val backendBaseUrlTextFile = rootProject.file("secrets/backend_base_url.txt")
val defaultBackendBaseUrl = "http://10.0.2.2:8080/"
val backendBaseUrl = when {
    backendBaseUrlTextFile.exists() -> backendBaseUrlTextFile.readText().trim()
    else -> defaultBackendBaseUrl
}
val generatedBackendConfigAssetsDir = layout.buildDirectory.dir("generated/backendConfig/assets")
val generateBackendBaseUrlAsset by tasks.registering {
    val outputFile = generatedBackendConfigAssetsDir.map { it.file("backend_base_url.txt") }
    inputs.file(backendBaseUrlTextFile)
        .withPropertyName("backendBaseUrlTextFile")
        .optional()
    outputs.file(outputFile)

    doLast {
        val runtimeBackendBaseUrl = when {
            backendBaseUrlTextFile.exists() -> backendBaseUrlTextFile.readText().trim()
            else -> defaultBackendBaseUrl
        }
        val assetFile = outputFile.get().asFile
        assetFile.parentFile.mkdirs()
        assetFile.writeText(runtimeBackendBaseUrl)
    }
}

android {
    namespace = "com.example.cheapestoilfinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cheapestoilfinder"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("appSigning") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "KAKAO_NATIVE_APP_KEY",
                "\"$kakaoNativeAppKey\""
            )
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"$backendBaseUrl\""
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("appSigning")
            }
        }

        release {
            isMinifyEnabled = false
            buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"\"")
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"$backendBaseUrl\""
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("appSigning")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedBackendConfigAssetsDir.get().asFile)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.kakao.maps.open:android:2.13.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.matching { task ->
    task.name == "generateDebugBuildConfig" || task.name == "generateReleaseBuildConfig"
}.configureEach {
    inputs.property("kakaoNativeAppKey", kakaoNativeAppKey)
    inputs.property("backendBaseUrl", backendBaseUrl)
    if (kakaoSecretTextFile.exists()) {
        inputs.file(kakaoSecretTextFile)
    }
    if (backendBaseUrlTextFile.exists()) {
        inputs.file(backendBaseUrlTextFile)
    }
}

tasks.matching { task ->
    task.name == "mergeDebugAssets" || task.name == "mergeReleaseAssets"
}.configureEach {
    dependsOn(generateBackendBaseUrlAsset)
}
