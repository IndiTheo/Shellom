# Shellom!

**Elevation to shell identity for Android apps.**

Shellom! is a novel approach to adopting privileged shell (ADB) permissions at runtime, no root required.
Unlike implementations that spawn a separate app process, Shellom! utilizes an ADB session provided
to it to elevate the main process of the application to user 2000's identity.
This way, it's possible to use privileged system service wrappers without any hassle:

- No reflection ✅
- No manual AIDL communication, use managers directly ✅
- No parcelization problems ✅
- No manual swapping of binder references ✅

Shellom! can be used to launch regular Android Services that can directly run privileged code, without the need to
imitate headless AIDL services. Shellom! uses the [instrumentation testing framework](https://source.android.com/docs/core/tests/development/instrumentation) to adopt debugger permissions for the desired app.

> [!WARNING]
> Only one instrumented test is allowed to run in the system, so just one app can be elevated
> using Shellom! at once. Let your users choose who gets this superpower!

## Requirements
- **Android 12+ (API 31)**: Required for `--no-restart` instrumentation.
- **ADB Connection**: Required to trigger the elevation command.

> [!TIP]
> Any ADB transport works as Shellom! only needs a [`ShellExecutor`](#get-shell-executor).
> See [Integrations](#integrations) for supported elevation transports.

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
    implementation("com.indidevs.android:shellom:LATEST_VERSION")
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

### Get Shell Executor
The `ShellExecutor` routes the elevation command through your ADB channel.

> See [Integrations](#integrations) for ready ADB executors.

**Define a custom executor:**
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

### Monitor
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

### Reset
To clear the cached privileged context and reset the status to `IDLE`:
```kotlin
provider.reset()
```

The shell identity itself is process-wide and `awaitContext()` returns the privileged context
without triggering a new elevation. Resetting elevation is not supported yet.

---

### Custom Runner
If you use a custom `AndroidJUnitRunner` subclass, pass its name to the provider:
```kotlin
val provider = InstrumentationShellProvider(..., runnerClass = "com.myapp.MyRunner")
```

---

## Integrations

| Integration                    | Artifact                       |
|:-------------------------------|:-------------------------------|
| [ADBeesh](#adbeesh)            | `com.indidevs.android:adbeesh` |
| [Shizuku](#shizuku compatible) | `dev.rikka.shizuku:api`        |

### [ADBeesh](https://gitlab.com/IndiDevs/adbeesh)

```kotlin
val session = pairing.openSession(timeoutMs = 5000)

val executor = AdbeeshExecutor(
    session = session,
    elevationSatisfied = { provider.status.value == ShellStatus.READY },
    tag = "AdbeeshExecutor"
)
```

### [Shizuku](https://shizuku.rikka.app) Compatible

> [!NOTE]
> Shizuku is an optional `compileOnly` integration in Shellom!.
Applications opting to use Shizuku must include Shizuku's dependencies and manifest entries in their own project.

#### Add Shizuku Dependencies

[![Maven Central](https://img.shields.io/maven-central/v/dev.rikka.shizuku/api?label=Shizuku%20API)](https://mvnrepository.com/artifact/dev.rikka.shizuku/api) [![Maven Central](https://img.shields.io/maven-central/v/dev.rikka.shizuku/provider?label=Shizuku%20Provider)](https://mvnrepository.com/artifact/dev.rikka.shizuku/provider)

#### Target Application Manifest (`AndroidManifest.xml`)
Add Shizuku's permission, package visibility query, protocol metadata, and IPC `ShizukuProvider` to your app's `AndroidManifest.xml`:

```xml
<!-- Shizuku API Permission -->
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />

<!-- Android 11+ Package Visibility Query -->
<queries>
    <package android:name="moe.shizuku.privileged.api" />
</queries>

<application>
    <!-- Shizuku V3 Client Protocol Metadata -->
    <meta-data
        android:name="moe.shizuku.client.V3_SUPPORT"
        android:value="true" />

    <!-- Shizuku ContentProvider Handoff -->
    <provider
        android:name="rikka.shizuku.ShizukuProvider"
        android:authorities="${applicationId}.shizuku"
        android:exported="true"
        android:multiprocess="false"
        android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
</application>
```

#### Use Executor

```kotlin
val executor = ShizukuExecutor(
    binderTimeoutMs = 5000L,
    autoRequestPermission = true,
    requestCode = 1001 // Permission request code supplied to Shizuku
)
```
