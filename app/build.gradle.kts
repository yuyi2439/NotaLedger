import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing credentials.
 *
 * Read from the user-level Gradle properties first, then from a
 * `keystore.properties` file next to the project root. Neither location is
 * committed, so the keystore password never lands in version control.
 *
 * No properties at all is not an error: the debug build must keep working for
 * anyone who has no keystore. Only `assembleRelease` needs them, and if they are
 * missing the release APK stays unsigned with Gradle's own clear message.
 */
val signingProperties: Map<String, String> = run {
    val fromGradleHome = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .mapNotNull { key -> providers.gradleProperty(key).orNull?.let { key to it } }
        .toMap()
    if (fromGradleHome.size == 4) {
        fromGradleHome
    } else {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            val loaded = Properties()
            file.inputStream().use { loaded.load(it) }
            loaded.stringPropertyNames().associateWith { loaded.getProperty(it) }
        } else {
            emptyMap()
        }
    }
}

android {
    namespace = "cn.yausen.notaledger"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cn.yausen.notaledger"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingProperties.isNotEmpty()) {
            create("release") {
                // Absolute paths work as-is; relative ones resolve against the root module.
                val configured = signingProperties.getValue("storeFile")
                storeFile = rootProject.file(configured).takeIf { it.exists() }
                    ?: file(configured)
                storePassword = signingProperties["storePassword"]
                keyAlias = signingProperties["keyAlias"]
                keyPassword = signingProperties["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            // Only attach signing when credentials were actually found, so a
            // missing keystore cannot break unrelated tasks.
            if (signingProperties.isNotEmpty()) {
                signingConfig = signingConfigs.findByName("release")
            }
            // R8 shrinking and optimisation for the shipped build. Rules live in
            // src/main/keepRules/rules.keep; the AndroidX AARs carry their own.
            optimization {
                enable = true
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
