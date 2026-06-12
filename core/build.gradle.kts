plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.ahocorasick:ahocorasick:0.6.3")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    implementation("ai.djl.huggingface:tokenizers:0.31.1")

    val sherpaLibs = file("libs")
    if (sherpaLibs.exists()) {
        implementation(fileTree(sherpaLibs) { include("*.jar") })
    }

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}
