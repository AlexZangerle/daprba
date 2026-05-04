plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation("org.apache.fory:fory-core:0.15.0")
    implementation("org.apache.fory:fory-kotlin:0.15.0")
    implementation("io.javalin:javalin:6.5.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

application { mainClass.set("ac.at.uibk.dps.dapr.bms.benchmark.BuildingServiceKt") }

tasks.jar {
    manifest { attributes["Main-Class"] = "ac.at.uibk.dps.dapr.bms.benchmark.BuildingServiceKt" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
