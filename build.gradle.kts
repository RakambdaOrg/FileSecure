import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    idea
    application
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.jib)
}

group = "fr.rakambda"
description = "FileSecure"

val javaVersion = providers.gradleProperty("javaVersion").get()

dependencies {
    implementation(platform(libs.jacksonBom))
    implementation(platform(libs.log4j2Bom))

    implementation(libs.coroutines)

    implementation(libs.slf4j)
    implementation(libs.bundles.log4j2)
    implementation(libs.kotlinLogging)

    implementation(libs.clikt)
    implementation(libs.bundles.jackson)

    implementation(libs.pointLocation)
    implementation(libs.timeshape)
    implementation(libs.metadataExtractor)

    implementation(libs.jaffree)
    implementation(libs.progressbar)
    implementation(libs.h2)
    implementation(libs.bundles.jna)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.junitPlatformLauncher)
    testImplementation(libs.bundles.assertj)
    testImplementation(libs.jsonSchemaValidator)
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion))
    }
}

tasks {
    test {
        useJUnitPlatform()
        inputs.files("config.schema.json", "README.md").withPathSensitivity(PathSensitivity.RELATIVE)
    }

    jar {
        manifest {
            attributes["Multi-Release"] = "true"
        }
    }

    register<JavaExec>("migrateConfig") {
        group = "application"
        description = "Converts FileSecure 12 and MediaConverter configurations, pass the files with --args"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("fr.rakambda.filesecure.migration.MigrateConfigCommandKt")
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    shadowJar {
        archiveBaseName.set(project.name)
        archiveClassifier.set("shaded")
        archiveVersion.set("")

        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        transform<Log4j2PluginsCacheFileTransformer>()
    }
}

application {
    mainClass.set(providers.gradleProperty("className"))
}

jib {
    from {
        image = "eclipse-temurin:26-jdk-alpine"
        platforms {
            platform {
                os = "linux"
                architecture = "arm64"
            }
            platform {
                os = "linux"
                architecture = "amd64"
            }
        }
    }
    container {
        creationTime.set("USE_CURRENT_TIMESTAMP")
        user = "1027:100"
    }
}
