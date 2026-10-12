import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 서명 키는 저장소 밖에 둔다. 파일이 없으면 release는 서명 없이 빌드된다(CI용).
val keystoreProps = Properties().apply {
    val path = providers.gradleProperty("harugyeol.keystoreProperties").orNull
        ?: "${System.getProperty("user.home")}/harugyeol-keys/keystore.properties"
    file(path).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.bossxor.harugyeol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bossxor.harugyeol"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
}
