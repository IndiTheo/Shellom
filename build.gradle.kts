import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.indidevs.android.shellom"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Instrumentation
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test:monitor:1.8.0")
    implementation("androidx.test:core:1.7.0")
    implementation("junit:junit:4.13.2")
}

val internalRepoUrl = project.findProperty("INTERNAL_REPO_URL")?.toString()

mavenPublishing {
    val versionName = project.findProperty("VERSION_NAME")?.toString()
        ?: throw GradleException(
            "VERSION_NAME property is required for publishing." +
                "Pass it via -PVERSION_NAME or ORG_GRADLE_PROJECT_VERSION_NAME environment variable."
        )

    coordinates(
        groupId = "com.indidevs.android",
        artifactId = "shellom",
        version = versionName
    )

    pom {
        val projectUrl = project.findProperty("PROJECT_URL")?.toString()
        name.set("Shellom")
        description.set("Shell identity elevation for Android apps.")

        if (projectUrl != null) {
            url.set(projectUrl)
            scm {
                val scmUrl = projectUrl.removePrefix("https://")
                connection.set("scm:git:$scmUrl.git")
                developerConnection.set("scm:git:ssh://$scmUrl.git")
                url.set("$projectUrl/-/tree/main")
            }
        }

        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("indidevs")
                name.set("IndiDevs")
                email.set("incoming+indidevs-shellom-83615814-issue-@incoming.gitlab.com")
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

if (!internalRepoUrl.isNullOrBlank()) {
    publishing {
        repositories {
            maven {
                name = "internal"
                url = uri(internalRepoUrl)
                credentials {
                    username = project.findProperty("INTERNAL_REPO_USERNAME")?.toString()
                    password = project.findProperty("INTERNAL_REPO_PASSWORD")?.toString()
                }
            }
        }
    }
}
