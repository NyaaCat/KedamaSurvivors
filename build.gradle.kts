plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "cat.nyaa"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")  // For Vault API
    maven("https://repo.codemc.org/repository/maven-public/")  // FastBoard
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")

    // Optional Vault API for economy integration
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // FastBoard for packet-based scoreboard (coexists with other plugins)
    implementation("fr.mrmicky:fastboard:2.1.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.yaml:snakeyaml:2.2")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "name" to project.name
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveClassifier.set("dev")
    }

    shadowJar {
        archiveClassifier.set("")

        // Relocate FastBoard to avoid conflicts with other plugins
        relocate("fr.mrmicky.fastboard", "cat.nyaa.survivors.lib.fastboard")

        // Minimize jar by removing unused classes
        minimize()
    }

    reobfJar {
        inputJar.set(shadowJar.flatMap { it.archiveFile })
    }

    assemble {
        dependsOn(reobfJar)
    }

    test {
        useJUnitPlatform()
    }
}
