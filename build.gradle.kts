import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    idea
    java
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.names)
    alias(libs.plugins.jib)
}

group = "fr.rakambda"
description = "FileSecure"

dependencies {
    implementation(platform(libs.jacksonBom))
    implementation(platform(libs.log4j2Bom))

    implementation(libs.slf4j)
    implementation(libs.bundles.log4j2)

    implementation(libs.unirest)
    implementation(libs.pointLocation)
    implementation(libs.metadataExtractor)

    implementation(libs.picocli)
    implementation(libs.bundles.jackson)

    compileOnly(libs.lombok)
    compileOnly(libs.jetbrainsAnnotations)

    annotationProcessor(libs.lombok)

    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.junitEngine)

    testImplementation(libs.bundles.assertj)
    testImplementation(libs.bundles.mockito)

    testCompileOnly(libs.lombok)
    testCompileOnly(libs.jetbrainsAnnotations)

    testAnnotationProcessor(libs.lombok)
}

repositories {
    mavenCentral()
}

tasks {
    processResources {
        expand(project.properties)
    }

    compileJava {
        val moduleName: String by project
        inputs.property("moduleName", moduleName)

        options.encoding = "UTF-8"
        options.isDeprecation = true
    }

    compileTestJava {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }

    jar {
        manifest {
            attributes["Multi-Release"] = "true"
        }
    }

    shadowJar {
        archiveBaseName.set(project.name)
        archiveClassifier.set("shaded")
        archiveVersion.set("")

        transform(Log4j2PluginsCacheFileTransformer::class.java)
    }
}

application {
    val moduleName: String by project
    val className: String by project

    mainModule.set(moduleName)
    mainClass.set(className)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

jib {
    from {
        image = "eclipse-temurin:21-jdk"
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
