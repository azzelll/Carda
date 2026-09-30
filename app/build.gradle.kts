import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// AGP lint indexes generated Hilt test Java sources. Both KSP and Hilt compilation can
// replace those files, so finish both producers before any lint analysis variant starts.
// Do not suppress detectors or convert lint's missing-file error into a warning.
tasks.configureEach {
    if (name.startsWith("lintAnalyze"))
        dependsOn("kspDebugAndroidTestKotlin", "hiltJavaCompileDebugAndroidTest")
}

val releaseSecretNames = listOf("CARDA_KEYSTORE_PATH", "CARDA_STORE_PASSWORD", "CARDA_KEY_ALIAS", "CARDA_KEY_PASSWORD")
val releaseSecrets = releaseSecretNames.map { providers.environmentVariable(it).orNull }
require(releaseSecrets.all { it == null } || releaseSecrets.all { !it.isNullOrBlank() }) {
    "Release signing requires all four CARDA signing environment variables; secret values are not printed"
}

android {
    namespace = "id.carda.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "id.carda.app"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("cardaVersionCode").orElse("1").get().toInt().also { require(it > 0) }
        versionName = providers.gradleProperty("cardaVersionName").orElse("0.1.0").get().also {
            require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.]+)?")))
        }
        testInstrumentationRunner = "id.carda.app.CardaTestRunner"
        val suppliedAuthUrl = providers.gradleProperty("cardaAuthBaseUrl").orElse("").get()
        if (releaseSecrets.all { it != null }) {
            val endpoint = runCatching { URI(suppliedAuthUrl) }.getOrNull()
            require(endpoint?.scheme == "https" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null) {
                "A signed release requires a valid HTTPS identity endpoint"
            }
        }
        val authUrl = suppliedAuthUrl
            .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "AUTH_BASE_URL", "\"$authUrl\"")
    }

    if (releaseSecrets.all { it != null }) {
        signingConfigs.create("externalRelease") {
            val externalKeystore = file(releaseSecrets[0]!!).canonicalFile
            require(externalKeystore.isFile && !externalKeystore.toPath().startsWith(rootDir.canonicalFile.toPath())) {
                "Signing keystore must exist outside this repository"
            }
            storeFile = externalKeystore
            storePassword = releaseSecrets[1]
            keyAlias = releaseSecrets[2]
            keyPassword = releaseSecrets[3]
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSecrets.all { it != null }) signingConfig = signingConfigs.getByName("externalRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ppg"))
    implementation(project(":core:measurement"))
    implementation(project(":core:auth"))
    implementation(project(":core:data"))
    implementation(project(":core:export"))
    implementation(project(":feature:measurement"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:history"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:result"))
    implementation(project(":feature:onboarding"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
}
