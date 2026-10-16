# ftb-trading-platform

Cross-asset front-to-back trading platform inspired by Murex MX.3.
Spring Boot microservices, Gradle (Kotlin DSL). Part 1: FX.

## Commands

Run every command from the repo root (`ftb-trading-platform/`).
On Windows PowerShell use `.\gradlew`; on Linux, macOS or Git Bash use `./gradlew`.

### `.\gradlew build`

Compiles, tests and packages **every** service listed in `settings.gradle.kts`.

What happens when you run it:

```
1. gradlew.bat                                  launcher script
2. gradle/wrapper/gradle-wrapper.properties    "use Gradle 9.8.1"
3. gradle/wrapper/gradle-wrapper.jar           downloads Gradle 9.8.1 (first time only)
                                                into C:\Users\MSIs\.gradle\wrapper\dists
4. Gradle 9.8.1 starts, then reads YOUR build:
   ├─ gradle.properties                         how to run (cache, parallel, JVM)
   ├─ settings.gradle.kts                       which projects exist
   ├─ gradle/libs.versions.toml                 versions
   ├─ buildSrc/                                 convention plugin (later)
   └─ static-data-service/build.gradle.kts      how to build this service
5. runs the "build" task: compile → test → jar
```

Output, per service:

- runnable jar: `<service>/build/libs/<service>-<version>.jar`
- test report: `<service>/build/reports/tests/test/index.html`

### Other commands

| Command | What it does |
|---|---|
| `.\gradlew :static-data-service:build` | Build only one service |
| `.\gradlew test` | Compile and run tests, no packaging |
| `.\gradlew :static-data-service:test` | Test only one service |
| `.\gradlew :static-data-service:bootRun` | Start one service |
| `.\gradlew clean` | Delete all `build/` folders |
| `.\gradlew clean build` | Full rebuild (tasks show `FROM-CACHE` when the build cache is used) |
| `.\gradlew build -x test` | Build but skip tests (use rarely) |
| `.\gradlew projects` | List the subprojects Gradle can see |
| `.\gradlew tasks` | List all available tasks |
| `.\gradlew :static-data-service:dependencies` | Show the dependency tree of one service |
| `.\gradlew --stop` | Stop the background Gradle daemon (e.g. when files are locked) |
| `.\gradlew wrapper --gradle-version <x.y>` | Upgrade the Gradle version (never edit the wrapper files by hand) |
