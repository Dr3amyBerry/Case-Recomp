plugins {
    id("com.android.application")
}

android {
    namespace = "org.rigorcore.caserecomp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rigorcore.caserecomp.synthetic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0-phase7-qa"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
