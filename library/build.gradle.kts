plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.vanniktech.publish)
}

android {
    namespace = "dev.ffmpegkit.whisper"
    compileSdk = 35
    ndkVersion = "27.2.12479018" // NDK r27c — same as FFmpegKit 8.1

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("proguard-rules.pro")

        // arm64-v8a only for the Free tier (Pro adds x86_64).
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // Mirrors the FFmpegKit 8.1 reference build for whisper.cpp.
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DGGML_NEON=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_METAL=OFF",
                )
                cppFlags += "-O3"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Package the prebuilt .so with 16 KB page alignment (Android 15 ready).
    // The linker flag is set in CMakeLists.txt; AGP packages the aligned .so as-is.
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}

mavenPublishing {
    // artifactId = whisper-android (Free tier). Pro uses whisper-android-pro.
    coordinates("dev.ffmpegkit-maintained", "whisper-android", providers.gradleProperty("VERSION").get())

    // Sign only when a GPG key is configured (Maven Central publish). JitPack and
    // local publishToMavenLocal have no key → skip signing so the build succeeds.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    // vanniktech 0.34.0: host defaults to CENTRAL_PORTAL. Do NOT pass
    // SonatypeHost.CENTRAL_PORTAL. automaticRelease = true → publishes to Maven
    // Central without the manual "Publish" click on the portal.
    publishToMavenCentral(automaticRelease = true)

    pom {
        name = "whisper-android"
        description = "Whisper for Android — prebuilt whisper.cpp AAR, on-device speech-to-text and audio transcription, no NDK required, no cloud, no API key. Drop-in AAR, arm64-v8a, API 24+. jokobee.com"
        inceptionYear = "2026"
        url = "https://github.com/ffmpegkit-maintained/whisper"

        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/ffmpegkit-maintained/whisper/blob/main/LICENSE"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "jokobee"
                name = "Jokobee"
                url = "https://www.jokobee.com"
                email = "contact@jokobee.com"
                organization = "Jokobee"
                organizationUrl = "https://www.jokobee.com"
            }
        }

        scm {
            url = "https://github.com/ffmpegkit-maintained/whisper"
            connection = "scm:git:git://github.com/ffmpegkit-maintained/whisper.git"
            developerConnection = "scm:git:ssh://git@github.com/ffmpegkit-maintained/whisper.git"
        }
    }
}

// --- THIRD-PARTY-NOTICES : bundle automatique dans les assets de l'AAR ---
tasks.register<Copy>("copyThirdPartyNotices") {
    from(rootProject.file("THIRD-PARTY-NOTICES.txt"))
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.named("preBuild") { dependsOn("copyThirdPartyNotices") }
