plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.mwalczak.spelucky"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.mwalczak.spelucky"
        minSdk = 26
        targetSdk = 34
        // GitHub Actions builds are numbered (build-12 has versionCode 12), so the game can
        // tell when a newer release exists. Local builds are version 1 and never auto-update.
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.$buildNumber"

        // Online leaderboard (github.com/mwalczak/mobile-scores). Without a key, e.g. in a
        // local build, the game works normally and just hides the leaderboard.
        val scoresKey = System.getenv("SCORES_KEY") ?: (project.findProperty("scoresKey") as String?) ?: ""
        buildConfigField("String", "SCORES_URL", "\"https://scores.walczaki.com\"")
        buildConfigField("String", "SCORES_KEY", "\"$scoresKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        // A fixed debug key checked into the repo, so APKs built on any machine
        // (or by GitHub Actions) can be installed over each other on the tablet.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
