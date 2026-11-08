plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.handyfotosync.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.handyfotosync.app"
        minSdk = 29          // Android 10 oder neuer
        targetSdk = 35
        versionCode = 9
        versionName = "1.8"
    }

    // Fester Schlüssel im (privaten) Repository, damit Updates über die alte Version installiert werden können
    signingConfigs {
        create("fest") {
            storeFile = file("fotosync.keystore")
            storePassword = "fotosync"
            keyAlias = "fotosync"
            keyPassword = "fotosync"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fest")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // SMB2/SMB3-Client für die FRITZ!NAS-Freigabe
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
}
