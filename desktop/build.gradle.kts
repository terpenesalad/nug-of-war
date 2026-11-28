plugins {
    kotlin("jvm") version "2.2.21"
    application
}

repositories { mavenCentral() }

val skikoVersion = "0.156.1"
// Native Skia runtime for the machine we package for (override with -PskikoTarget=windows-x64)
val skikoTarget = (findProperty("skikoTarget") as String?) ?: run {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch").contains("aarch64")) "arm64" else "x64"
    when {
        os.contains("win") -> "windows-$arch"
        os.contains("mac") -> "macos-$arch"
        else -> "linux-$arch"
    }
}

dependencies {
    implementation("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-$skikoTarget:$skikoVersion")
    implementation("com.badlogicgames.jamepad:jamepad:2.30.0.0")
}

kotlin { jvmToolchain(((findProperty("jdk") as String?) ?: "17").toInt()) }

// The game itself is shared with the Android app; only the platform layer differs.
sourceSets.main {
    kotlin.srcDir("../app/src/main/java")
    kotlin.exclude("**/GameView.kt", "**/MainActivity.kt", "**/InputHub.kt", "**/Sound.kt")
}

application { mainClass.set("com.terpenesalad.nugofwar.MainKt") }

// All jars in one folder, for jpackage
tasks.register<Copy>("stageJars") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("jars"))
}
