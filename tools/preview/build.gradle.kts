// Renders screenshots of the game on a normal computer (no phone needed) using Robolectric.
// Run with: gradle test   (images land in build/shots)
plugins { kotlin("jvm") version "2.0.21" }
repositories { mavenCentral(); google() }
dependencies {
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    // androidx.test ships as .aar files; the workflow unpacks their classes.jar into libs/
    testImplementation(fileTree("libs") { include("*.jar") })
    testImplementation("androidx.annotation:annotation-jvm:1.8.0")
    testImplementation("org.robolectric:android-all:14-robolectric-10818077")
}
kotlin { jvmToolchain(17) }
val copyGame by tasks.registering(Copy::class) {
    from("../../app/src/main/java") { exclude("**/GameView.kt", "**/MainActivity.kt") }
    into(layout.buildDirectory.dir("gamesrc"))
}
sourceSets { test { kotlin.srcDir(layout.buildDirectory.dir("gamesrc")) } }
tasks.named("compileTestKotlin") { dependsOn(copyGame) }
tasks.test {
    systemProperty("robolectric.graphicsMode", "NATIVE")
    systemProperty("shots.dir", layout.buildDirectory.dir("shots").get().asFile.absolutePath)
    testLogging { showStandardStreams = true; events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    maxHeapSize = "3g"
}
