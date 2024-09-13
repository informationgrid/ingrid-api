@file:Suppress("ktlint:standard:no-wildcard-imports", "PropertyName")

import fr.brouillard.oss.gradle.plugins.JGitverPluginExtensionBranchPolicy
import io.ktor.plugin.features.*

val ktor_version: String by project
val kotlin_version: String by project
val koin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    id("io.ktor.plugin") version "2.3.12"
    id("fr.brouillard.oss.gradle.jgitver") version "0.9.1"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "de.ingrid.ingridapi"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

kotlin {
    jvmToolchain(21)
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)

        val branchName = System.getenv("BRANCH_NAME").orEmpty()
        val tagName = System.getenv("TAG_NAME").orEmpty()

        val tag =
            if (tagName.isNotEmpty()) {
                tagName
            } else if (branchName.isNotEmpty()) {
                if (branchName == "main") "latest" else branchName.replace("/", "-")
            } else {
                "???"
            }

        externalRegistry.set(
            DockerImageRegistry.externalRegistry(
                providers.environmentVariable("DOCKER_REGISTRY_CREDS_USR"),
                providers.environmentVariable("DOCKER_REGISTRY_CREDS_PSW"),
                provider { "docker-registry.wemove.com/ingrid-api" },
            ),
        )
        localImageName.set("docker-registry.wemove.com/ingrid-api")
        imageTag.set(tag)
    }
}

jgitver {

    policy(
        closureOf<JGitverPluginExtensionBranchPolicy> {
            pattern = "([main|develop|support].*)"
            transformations = listOf("IGNORE")
        },
    )
    policy(
        closureOf<JGitverPluginExtensionBranchPolicy> {
            pattern = "(\\d+\\.\\d+\\.\\d+)"
            transformations = listOf("IGNORE")
        },
    )
}

/*spotless {
    kotlin {
        ktfmt().kotlinlangStyle()
    }
}*/

repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}

dependencies {
    // ktor
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-compression-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-forwarded-header")
    implementation("io.ktor:ktor-server-forwarded-header-jvm")

    // support JSON export
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // swagger
    implementation("io.github.smiley4:ktor-swagger-ui:2.7.4")

    // elasticsearch-client
    implementation("com.jillesvangurp:search-client:2.1.20")

    // dependency injection
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    testImplementation("io.insert-koin:koin-test:$koin_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // tests
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
