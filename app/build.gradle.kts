plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.microsoft.intune.mam")
}

android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("/Users/yaso28/Documents/apps/android/sample202506.jks")
            storePassword = "y28#Develop"
            keyAlias = "Sample202506"
            keyPassword = "y28#Develop"
        }
    }
    namespace = "com.yaso202508appproxy.intunetestapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yaso202508appproxy.intunetestapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("debug")
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