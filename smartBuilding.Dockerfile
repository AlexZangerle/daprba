FROM gradle:9.3.0-jdk25 AS build

COPY --chown=gradle:gradle . /usr/src/daprba

WORKDIR /usr/src/daprba

RUN gradle :smartBuilding:distZip

RUN unzip smartBuilding/build/distributions/smartBuilding.zip -d /tmp

FROM gcr.io/distroless/java25-debian13 AS runtime

COPY --from=build /tmp/smartBuilding /opt/smartBuilding

ENTRYPOINT ["java", "-cp", "/opt/smartBuilding/lib/*", "ac.at.uibk.dps.dapr.bms.BMSKt"]
