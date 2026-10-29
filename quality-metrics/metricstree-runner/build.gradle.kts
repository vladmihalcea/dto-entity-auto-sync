import org.jetbrains.intellij.platform.gradle.TestFrameworkType

/*
 * Runs the MetricsTree IntelliJ IDEA plugin without the IDE user interface: the test task opens the
 * sources of one quality-metrics module in a headless IntelliJ IDEA, calls the same MetricsTree code
 * as its "Export Package Metrics to CSV" action, and writes the package metrics of the module.
 * run-metricstree.bat (or .sh) in the parent folder runs it for every module.
 */
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val ideaLocalPath = providers.gradleProperty("ideaLocalPath").orNull?.takeIf { it.isNotBlank() }

dependencies {
    intellijPlatform {
        if (ideaLocalPath != null) {
            local(ideaLocalPath)
        } else {
            intellijIdea(providers.gradleProperty("ideaVersion"))
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("Git4Idea")
        plugin("org.b333vv.metricstree", providers.gradleProperty("metricsTreeVersion").get())
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

// IntelliJ IDEA 2026.1 and later are compiled for Java 25. The analyzed modules keep their own JDK
// (metricstree.jdk), against which their sources resolve.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
}

tasks.test {
    useJUnit()
    // Every run measures a different module, so the results of a previous run never apply.
    outputs.upToDateWhen { false }
    for (name in listOf("sources", "dependencies", "jdk", "output")) {
        providers.gradleProperty("metricstree.$name").orNull?.let { systemProperty("metricstree.$name", it) }
    }
    // The module sources, its dependency jars, and the JDK lie outside the test project.
    systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true")
    // MetricsTree formats decimals with the default locale, so a locale with a decimal comma would change the CSV.
    jvmArgs("-Duser.language=en", "-Duser.country=US")
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
