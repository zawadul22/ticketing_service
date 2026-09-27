# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

The git root is this directory, but the Gradle project lives in `ticketing_service/`. Run all Gradle commands from there.

## Commands

Run from `ticketing_service/`:

- Build: `./gradlew build`
- Run the app: `./gradlew bootRun`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests 'com.example.ticketing_service.TicketingServiceApplicationTests'`
- Single test method: `./gradlew test --tests 'com.example.ticketing_service.TicketingServiceApplicationTests.contextLoads'`

## Stack

- Spring Boot 4.1.1 on Java 21 (Gradle toolchain), Gradle 9.7.1 wrapper, Groovy DSL (`build.gradle`).
- Spring Boot 4 uses modular starters: web is `spring-boot-starter-webmvc`, and its test support is `spring-boot-starter-webmvc-test`, not the Boot 3 names (`spring-boot-starter-web`, `spring-boot-starter-test`).
- Lombok is set up for both main and test sources.
- The PostgreSQL driver is a runtime dependency, but there is no JPA/JDBC starter and no datasource config yet. When adding a JPA/JDBC starter, also add `spring.datasource.*` to `src/main/resources/application.properties`, or the `@SpringBootTest` context-load test will fail without a reachable database.

## Code

- Base package: `com.example.ticketing_service`. Components must live under it for component scanning to find them.
- Configuration goes in `application.properties` (not YAML).
- Source files use tab indentation.
