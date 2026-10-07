plugins {
    id("com.android.application")
}

android {
    namespace = "org.rigorcore.caserecomp.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.rigorcore.caserecomp.synthetic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase5-shell"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":engine"))
    testImplementation("junit:junit:4.13.2")
}
