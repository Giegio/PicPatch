plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sidekick.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sidekick.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    // Include marker images as assets without compression
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Material Design
    implementation(libs.material)

    // AR
    implementation(libs.arsceneview)
    implementation(libs.arcore)
    implementation("androidx.lifecycle:lifecycle-common-java8:2.6.2")

    // GPS (FusedLocationProviderClient in ARActivity)
    implementation(libs.play.location)

    // JSON serialization
    implementation(libs.gson)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.fragment:fragment-testing:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
}

tasks.withType<Test> {
    useJUnit()
    reports.html.required.set(true)
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            if (result.resultType == TestResult.ResultType.FAILURE) {
                println("FAILED: ${testDescriptor.className}.${testDescriptor.name}")
                result.exception?.let { e ->
                    println("Reason: ${e.message}")
                    val trace = e.stackTrace.firstOrNull { 
                        it.className.startsWith("com.sidekick") && !it.className.contains("Test") 
                    }
                    if (trace != null) {
                        println("  at $trace")
                    } else {
                        val testTrace = e.stackTrace.firstOrNull { it.className.contains("Test") }
                        if (testTrace != null) println("  at $testTrace")
                    }
                }
            }
        }
        
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) { // Solo la suite root
                println("\n=======================================")
                println("Test Results: ${result.resultType}")
                println("Total: ${result.testCount}, Passed: ${result.successfulTestCount}, Failed: ${result.failedTestCount}, Skipped: ${result.skippedTestCount}")
                println("=======================================\n")
            }
        }
    })
}
