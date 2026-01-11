import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
    id("com.google.gms.google-services")
}



val properties = Properties()
val localPropertiesFile = project.rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    properties.load(FileInputStream(localPropertiesFile))
}
val apiKey = properties.getProperty("GEMINI_API_KEY") ?: "TODO_ENTER_KEY_IN_LOCAL_PROPERTIES"

android {
    namespace = "com.example.myaac"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myaac"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }


        buildConfigField("String", "GEMINI_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "GOOGLE_SEARCH_API_KEY", "\"${properties.getProperty("GOOGLE_SEARCH_API_KEY") ?: ""}\"")
        buildConfigField("String", "GOOGLE_SEARCH_ENGINE_ID", "\"${properties.getProperty("GOOGLE_SEARCH_ENGINE_ID") ?: ""}\"")
        buildConfigField("boolean", "DEBUG_MODE", "true")  // Set to false for production
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/plexus/components.xml"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module" 
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test> {
    testLogging {
        events("standardOut", "started", "passed", "skipped", "failed")
        showStandardStreams = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Coil for image loading (IconPath)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.google.android.material:material:1.10.0")

    // Drag and drop reordering
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    // Room components
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Gson for TypeConverters
    implementation("com.google.code.gson:gson:2.10.1")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // ML Kit Subject Segmentation (Temporarily disabled due to sync error)
    // implementation("com.google.mlkit:subject-segmentation:16.0.0-beta1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.accompanist:accompanist-permissions:0.35.0-alpha")
    
    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")
    // SimpleNLG for Grammar/Morphology
    implementation("uk.ac.abdn:SimpleNLG:4.5.0") {
        exclude(group = "gov.nih.nlm.nls.lexaccess")
        exclude(group = "javax.xml.bind")
        exclude(group = "com.sun.xml.bind")
    }

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    
    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")
}
