plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

// Gitタグからバージョンを取得する関数
fun getVersionFromTag(): String {
    return try {
        val tag = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
            .removePrefix("v")

        if (tag.isNotEmpty()) tag else "0.0.1-SNAPSHOT"
    } catch (e: Exception) {
        "0.0.1-SNAPSHOT"
    }
}

android {
    namespace = "com.segnities007.mvi"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ViewModel & Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.segnities007"
                artifactId = "mvi"
                version = getVersionFromTag()  // Gitタグから自動取得

                // POM情報（JitPackで表示される）
                pom {
                    name.set("MVI Library")
                    description.set("A Model-View-Intent library for Android")
                    url.set("https://github.com/segnities007/mvi")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("segnities007")
                            name.set("Segnities")
                        }
                    }

                    scm {
                        connection.set("scm:git:github.com/segnities007/mvi.git")
                        developerConnection.set("scm:git:ssh://github.com/segnities007/mvi.git")
                        url.set("https://github.com/segnities007/mvi/tree/main")
                    }
                }
            }
        }
    }
}