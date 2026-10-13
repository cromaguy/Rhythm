plugins { alias(libs.plugins.kotlin.jvm) }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.org.jetbrains.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(libs.com.squareup.okhttp3.okhttp)
    implementation("org.jsoup:jsoup:1.18.3")
    // Jsoup's optional nullability annotations are required by Kotlin 2.4 type inference.
    compileOnly("org.jspecify:jspecify:1.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test { useJUnitPlatform() }

// Explicit opt-in: ordinary unit tests never contact music services.
tasks.register<JavaExec>("smoke") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("kr.co.metadata.crawler.LiveSmokeKt")
    listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach {
        System.getProperty(it)?.let { value -> systemProperty(it, value) }
    }
}
