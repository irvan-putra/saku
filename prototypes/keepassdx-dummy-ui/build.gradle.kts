// Disposable standalone harness module only, not the upstream app or a release.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.vanipy.saku.synthetic"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.vanipy.saku.synthetic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "synthetic-unverified"
        testApplicationId = "com.vanipy.saku.synthetic.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Requires an explicit disposable public-dummy visual-test build.
        buildConfigField("boolean", "DUMMY_PIXEL_CAPTURE",
            (providers.gradleProperty("dummyPixelCapture").orNull == "true").toString())
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":database"))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.junit)
}
