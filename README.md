# Shellom!

**Elevation to shell identity for Android apps.**

Shellom allows an Android app to adopt privileged shell (ADB) permissions at runtime, no root required.

## Requirements
- **Android 12+ (API 31)**: Required for `--no-restart` instrumentation.
- **ADB Connection**: Required to trigger the elevation command.

## 📦 Add Dependency

[![Maven Central](https://img.shields.io/maven-central/v/com.indidevs.android/shellom)](https://central.sonatype.com/artifact/com.indidevs.android/shellom)

### Maven
```xml
<dependency>
  <groupId>com.indidevs.android</groupId>
  <artifactId>shellom</artifactId>
  <version>LATEST_VERSION</version>
  <type>aar</type>
</dependency>
```

### Gradle
```kotlin
dependencies {
    implementation("com.indidevs.android:shellom:0.1.0")
}
```

### Bazel
```python
maven_install(
    artifacts = [
        "com.indidevs.android:shellom:LATEST_VERSION",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)
```

## Usage

### Register Runner (`AndroidManifest.xml`)
Register the `ShellRunner` in your manifest to handle the instrumentation process.
```xml
<instrumentation
    android:name="com.indidevs.android.shellom.ShellRunner"
    android:targetPackage="your.package.id" />
```

### Define Shell Executor
The `ShellExecutor` routes the elevation command through your ADB channel.

**Simple connection:**
```kotlin
val executor = ShellExecutor { cmd -> 
    myAdb.execute(cmd) // Must return Result<Unit>
}
```

**Reactive connection:**
If your ADB session is dynamic (e.g. a `StateFlow`), use `fromFlow`. It suspends until an executor is available.
```kotlin
val executor = ShellExecutor.fromFlow(adbSessionFlow.map { session ->
    session?.let { s -> ShellExecutor { cmd -> s.execute(cmd) } }
})
```

### Elevate & Await Context
Initialize the provider and call `awaitContext()`. It will trigger elevation if needed and return a `Context` with the requested permissions.

```kotlin
val provider = InstrumentationShellProvider(context, scope, executor, listOf(
    "android.permission.WRITE_SECURE_SETTINGS"
))

// Suspend until ready
val privilegedContext = provider.awaitContext() 
```

### Monitoring Status
Monitor the elevation lifecycle (`IDLE`, `ELEVATING`, `READY`, `ERROR`) via the `status` StateFlow or the `observeStatus` helper.

```kotlin
provider.observeStatus(lifecycleScope) { status ->
    when(status) {
        ShellStatus.ELEVATING -> showLoading()
        ShellStatus.READY     -> proceed()
        ShellStatus.ERROR     -> showError()
        else -> Unit
    }
}
```

### Resetting
To clear the cached privileged context and allow a fresh elevation attempt:
```kotlin
provider.reset()
```

---

### Custom Runner
If you use a custom `AndroidJUnitRunner` subclass, pass its name to the provider:
```kotlin
val provider = InstrumentationShellProvider(..., runnerClass = "com.myapp.MyRunner")
```

---
