plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    application
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.dapr:dapr-sdk:1.16.0")
    implementation("io.dapr:dapr-sdk-actors:1.16.0")
    implementation("io.dapr:dapr-sdk-springboot:1.16.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

application { mainClass.set("ac.at.uibk.dps.dapr.bms.BMSKt") }
