import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
val appVersionName = "0.1.0"
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.goodtvplorer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goodtvplorer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = (keystoreProperties["storeFile"] as String?)?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val architecture = output.filters
                .firstOrNull { it.filterType.name == "ABI" }
                ?.identifier
                ?: "universal"
            output.outputFileName.set("good-tvplorer-${variant.name}-$appVersionName-$architecture.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-ui-compose:1.9.0")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("com.hierynomus:smbj:0.14.0")
    testImplementation(kotlin("test-junit"))
    androidTestImplementation("androidx.room:room-testing:2.8.3")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
