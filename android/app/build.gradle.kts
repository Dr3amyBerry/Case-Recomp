plugins {
    id("com.android.application")
}

val phase8VersionCode = providers.gradleProperty("phase8VersionCode").orElse("8").get().toInt()

android {
    namespace = "org.rigorcore.caserecomp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rigorcore.caserecomp.synthetic"
        minSdk = 26
        targetSdk = 36
        versionCode = phase8VersionCode
        versionName = "0.5.0-phase8-debug"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        variantBuilder.enable = false
    }
}

dependencies {
    implementation(project(":engine"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
