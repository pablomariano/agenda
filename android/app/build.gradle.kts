import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.application") version "8.11.1"
    id("org.jetbrains.kotlin.android") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

val agendaApiUrl = providers.gradleProperty("agendaApiUrl").orElse("").get().trim().trimEnd('/')
if (agendaApiUrl.isNotEmpty()) {
    val uri = URI(agendaApiUrl)
    require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
        uri.rawPath.isEmpty() && uri.rawQuery == null && uri.rawFragment == null) {
        "agendaApiUrl debe ser un origen HTTPS, por ejemplo https://agenda.vercel.app."
    }
}

android {
    namespace = "com.pablomariano.agenda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pablomariano.agenda"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "AGENDA_API_URL", "\"$agendaApiUrl\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = if (agendaApiUrl.isEmpty()) ".demo" else ".preview"
            versionNameSuffix = if (agendaApiUrl.isEmpty()) "-demo" else "-preview"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.1")
}
