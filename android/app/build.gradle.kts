import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * Release signing stays on the tester's PC: `../private/signing/release.properties`
 * (storeFile, storePassword, keyAlias, keyPassword; git-ignored). Without it the
 * release APK is built unsigned.
 */
val releaseSigning = rootProject.file("../private/signing/release.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }.also { it["dir"] = file.parentFile }
}

val phase8VersionCode = providers.gradleProperty("phase8VersionCode").orElse("9").get().toInt()

android {
    namespace = "org.rigorcore.caserecomp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rigorcore.caserecomp.synthetic"
        minSdk = 26
        targetSdk = 36
        versionCode = phase8VersionCode
        versionName = "0.6.1-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { props ->
            create("release") {
                storeFile = File(props["dir"] as File, props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // No game content is packaged: the player imports their own Director ZIP.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    sourceSets {
        // Synthetic, repository-public fixtures only; packaged into the instrumentation test APK, not the app.
        getByName("androidTest").assets.srcDir("../../fixtures")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":engine"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
