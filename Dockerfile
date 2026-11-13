FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -q dependency:go-offline

COPY src ./src
COPY index.html ./index.html
COPY sample-data ./sample-data
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/credit-bureau-simulator-1.0.0.jar ./app.jar
COPY --from=build /app/index.html ./index.html
COPY --from=build /app/sample-data ./sample-data

EXPOSE 10000
CMD ["sh", "-c", "java -jar app.jar serve --data ./sample-data"]
