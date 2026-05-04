plugins { id("common-conventions") }

dependencies {
  implementation("org.apache.fory:fory-core:0.15.0")
  implementation("org.apache.fory:fory-kotlin:0.15.0")
}

application { mainClass.set("ac.at.uibk.dps.dapr.bms.BMSKt") }
