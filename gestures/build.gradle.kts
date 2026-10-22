// Pure Kotlin/JVM module: gesture logic must not depend on Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// Phantom-gesture bench on IPN landmark streams (see bench/ReplayBench.kt), not part of `test`.
tasks.register<JavaExec>("replayBench") {
    group = "verification"
    description = "Replays IPN landmark streams through the engines and reports phantom events."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.gesturecontrol.gestures.bench.ReplayBenchKt")
    workingDir = projectDir
    maxHeapSize = "2g"
    args = (project.findProperty("benchArgs") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}
