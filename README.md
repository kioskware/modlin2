# Modlin

Kotlin JVM project with a Compose Multiplatform desktop UI. The Gradle build uses
a Java 17 toolchain, which can be provisioned automatically by the configured
Foojay resolver.

Run the desktop app on Windows:

```powershell
.\gradlew.bat run
```

On macOS or Linux, use `./gradlew run`.

Build and run available tests:

```powershell
.\gradlew.bat build
```

The desktop entry point is `src/main/kotlin/Main.kt`, and the starter composable
is `ModlinApp` in `src/main/kotlin/pl/modlin/ModlinUI.kt`.

The Compose compiler plugin matches the Kotlin JVM plugin version, as required
by the [Compose compiler documentation](https://kotlinlang.org/docs/multiplatform/compose-compiler.html).

`ModuleSwitch.get()` remains an unfinished framework method and throws
`NotImplementedError` if called.
