import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.microsoft.intune.mam")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

fun getConfigValue(key: String, defaultValue: String = ""): String {
    System.getenv(key.uppercase().replace(".", "_"))?.let { return it }
    localProperties.getProperty(key)?.let { return it }
    project.findProperty(key)?.toString()?.let { return it }
    return defaultValue
}

android {
    project.afterEvaluate {
        val generateMsalConfig by tasks.registering {
            doLast {
                val templateFile = File("$projectDir/src/main/res/raw/msal_config_template.json")
                val outputFile = File("$projectDir/src/main/res/raw/msal_config.json")

                // 必要な設定値の存在チェック
                val clientId = getConfigValue("msal.client.id")
                val redirectUri = getConfigValue("msal.redirect.uri")
                val tenantId = getConfigValue("msal.tenant.id")

                if (templateFile.exists()) {
                    val content = templateFile.readText()
                        .replace("{{CLIENT_ID}}", clientId)
                        .replace("{{REDIRECT_URI}}", redirectUri)
                        .replace("{{TENANT_ID}}", tenantId)

                    outputFile.writeText(content)
                    println("✅ Generated msal_config.json from template")
                } else {
                    throw GradleException("❌ Template file missing: msal_config_template.json")
                }
            }
        }

        tasks.matching { it.name.startsWith("preBuild") }.configureEach {
            dependsOn(generateMsalConfig)
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(getConfigValue("debug.store.file"))
            storePassword = getConfigValue("debug.store.password")
            keyAlias = getConfigValue("debug.key.alias")
            keyPassword = getConfigValue("debug.key.password")
        }
    }
    namespace = "com.yaso202508appproxy.intunetestapp"
    compileSdk = 36

    defaultConfig {
        applicationId = getConfigValue("application.id", "com.yaso202508appproxy.intunetestapp")
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "0.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("debug")

        buildConfigField("String", "PROXY_URL", "\"${getConfigValue("proxy.url")}\"")
        buildConfigField("String", "PROXY_ORIGIN", "\"${getConfigValue("proxy.origin")}\"")
        buildConfigField("String", "PROXY_SCOPE", "\"${getConfigValue("proxy.scope")}\"")

        manifestPlaceholders["msAuthHost"] = getConfigValue("application.id", "com.yaso202508appproxy.intunetestapp")
        manifestPlaceholders["msAuthPath"] = getConfigValue("msal.redirect.path")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")

        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    intunemam {
        report = true
        verify = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.microsoft.identity.client:msal:7.0.3")
    implementation(files("libs/Microsoft.Intune.MAM.SDK.aar"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}