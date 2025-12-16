import fr.brouillard.oss.gradle.plugins.JGitverPluginExtensionBranchPolicy
import io.ktor.plugin.features.DockerImageRegistry

val koinVersion: String by project
val logbackVersion: String by project
val mockkVersion = "1.13.12"

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.3.3"
    id("fr.brouillard.oss.gradle.jgitver") version "0.9.1"
    id("com.diffplug.spotless") version "7.0.3"
    id("org.cyclonedx.bom") version "2.3.1"
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
            if (tagName.isNotEmpty() && !tagName.startsWith("RPM-")) {
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
                provider { "docker-registry.wemove.com/ingrid-api:$tag" },
            ),
        )
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
    // actually useful for tests
    implementation("io.ktor:ktor-client-content-negotiation")

    // swagger
    implementation("io.github.smiley4:ktor-swagger-ui:5.1.0")
    implementation("io.github.smiley4:ktor-openapi:5.1.0")

    // elasticsearch-client
    implementation("com.jillesvangurp:search-client:2.3.13")

    // dependency injection
    implementation(project.dependencies.platform("io.insert-koin:koin-bom:$koinVersion"))
    implementation("io.insert-koin:koin-core")
    implementation("io.insert-koin:koin-ktor")
    implementation("io.insert-koin:koin-logger-slf4j")
//    testImplementation("io.insert-koin:koin-test")
    testImplementation("io.insert-koin:koin-test-junit4")
    testImplementation("io.mockk:mockk:$mockkVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.4")

    // tests
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.13")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
//    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.12")
//    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.cyclonedxBom {
    // includeConfigs is the list of configuration names to include when generating the BOM (leave empty to include every configuration), regex is supported
    setIncludeConfigs(listOf("runtimeClasspath"))
    // skipConfigs is a list of configuration names to exclude when generating the BOM, regex is supported
    setSkipConfigs(listOf("compileClasspath", "testCompileClasspath"))
    // Specified the type of project being built. Defaults to 'library'
    setProjectType("application")
    // Specified the version of the CycloneDX specification to use. Defaults to '1.5'
    setSchemaVersion("1.5")
    // Boms destination directory. Defaults to 'build/reports'
    setDestination(file("build/reports"))
    // The file name for the generated BOMs (before the file format suffix). Defaults to 'bom'
    setOutputName("bom")
    // The file format generated, can be xml, json or all for generating both. Defaults to 'all'
    setOutputFormat("json")
    setComponentVersion(rootProject.version.toString())
}

tasks {
    test {
        ignoreFailures = true
    }
}
