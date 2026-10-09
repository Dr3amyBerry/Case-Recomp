plugins { id("com.android.application") version "9.4.1" }
val recoveredFonts = providers.gradleProperty("recoveredFontsDir").orNull
    ?: error("Supply -PrecoveredFontsDir=<private directory containing both experimental OTFs>")
android {
    namespace = "org.rigorcore.caserecomp.fontlab"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.rigorcore.caserecomp.fontlab"
        minSdk = 26
        targetSdk = 32
        testInstrumentationRunner = "org.rigorcore.caserecomp.fontlab.FontLabProbe"
        versionCode = 1
        versionName = "0.1-experimental"
    }
    sourceSets.getByName("main").assets.directories.add(recoveredFonts)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.register("verifyPrivateFonts") {
    doLast {
        for (name in listOf("tekton-recovered.otf", "tekton-italic-recovered.otf")) {
            require(file(recoveredFonts).resolve(name).isFile) { "Missing private recovered font: $name" }
        }
        require(file(recoveredFonts).listFiles()?.map { it.name }?.toSet() ==
            setOf("tekton-recovered.otf", "tekton-italic-recovered.otf")) { "Use an assets directory containing only the two fonts" }
    }
}
tasks.named("preBuild").configure { dependsOn("verifyPrivateFonts") }
