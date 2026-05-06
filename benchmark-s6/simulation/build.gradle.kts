plugins {
    kotlin("jvm") version "2.3.0"
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation("io.javalin:javalin:6.5.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ac.at.uibk.dps.dapr.bms.simulation.SimulationKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
