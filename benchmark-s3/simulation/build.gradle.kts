plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {}

application { mainClass.set("ac.at.uibk.dps.dapr.bms.benchmark.SimulationKt") }

tasks.jar {
    manifest { attributes["Main-Class"] = "ac.at.uibk.dps.dapr.bms.benchmark.SimulationKt" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
