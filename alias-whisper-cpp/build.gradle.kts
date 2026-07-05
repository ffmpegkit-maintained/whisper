// Maven Central search alias. No code — it publishes an artifact named
// `whisper-cpp-android` whose POM simply depends on the real library
// `whisper-android`, so developers searching for "whisper cpp" also find it.
plugins {
    `java-library`
    alias(libs.plugins.vanniktech.publish)
}

val whisperVersion = providers.gradleProperty("VERSION").get()

mavenPublishing {
    coordinates("dev.ffmpegkit-maintained", "whisper-cpp-android", whisperVersion)

    // Sign only when a GPG key is configured (see library/build.gradle.kts).
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    publishToMavenCentral(automaticRelease = true)

    pom {
        name = "whisper.cpp for Android"
        description = "Maven Central search alias → dev.ffmpegkit-maintained:whisper-android. " +
            "Prebuilt whisper.cpp AAR for Android — on-device speech-to-text, no NDK required. arm64-v8a, API 24+."
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
                id = "lucquebec"
                name = "Luc Côté"
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

dependencies {
    api("dev.ffmpegkit-maintained:whisper-android:$whisperVersion")
}
