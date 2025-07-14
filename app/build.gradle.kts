import java.util.Properties
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY") ?: ""



android {
    namespace = "com.llucasandersen.lucasfbla2025bankingapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.llucasandersen.lucasfbla2025bankingapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}


dependencies {

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // Add Appwrite SDK
    implementation("io.appwrite:sdk-for-android:4.0.0")

    implementation(libs.mpandroidchart)
    // Add JitPack for loading button
    implementation("com.github.leandroborgesferreira:loading-button-android:2.3.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.github.lecho:hellocharts-library:1.5.8@aar")
    implementation(libs.generativeai)
    implementation(libs.filament.android)
    implementation(libs.androidx.recyclerview.v131)



    implementation("com.itextpdf:itextpdf:5.5.13")
    implementation(libs.androidx.ui.text.android)







    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
