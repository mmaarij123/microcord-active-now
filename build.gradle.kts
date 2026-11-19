import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("dev.mantikafasi.manticord.plugin")
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use(::load)
}

manticordPlugin {
    id.set("dev.mantikafasi.manticord.activenow")
    entrypoint.set("dev.mantikafasi.manticord.activenow.ActiveNowPlugin")
    description.set("Shows active friends above your DMs and live voice channels on profiles")
    manticordVersionMinimum.set(3)
    manticordVersionMaximumExclusive.set(4)
    async()
}

android {
    namespace = "dev.mantikafasi.manticord.activenow"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.mantikafasi.manticord.activenow"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) create("release") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val localPluginApi = file("../manticordrebuild/build/plugin-sdk/manticord-plugin-api-1.0.6.jar")
    val pluginApi = if (localPluginApi.exists()) files(localPluginApi)
        else "dev.mantikafasi.manticord:plugin-api:1.0.6"
    compileOnly(pluginApi)
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.rxjava)
    compileOnly(libs.androidx.appcompat)
    compileOnly(libs.androidx.fragment)
    compileOnly(libs.androidx.recyclerview)
    compileOnly(libs.material)

    testImplementation(pluginApi)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.junit)
}
