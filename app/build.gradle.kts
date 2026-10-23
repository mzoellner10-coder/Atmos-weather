plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }
android {
 namespace = "com.atmos.weather"
 compileSdk = 35
 defaultConfig { applicationId = "com.atmos.weather"; minSdk = 29; targetSdk = 35; versionCode = 3; versionName = "2.1" }
 buildFeatures { compose = true }
 testOptions { unitTests.isIncludeAndroidResources = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation("com.squareup.retrofit2:converter-gson:2.11.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("androidx.datastore:datastore-preferences:1.1.1")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 testImplementation("org.robolectric:robolectric:4.14.1")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Keep Android simulator downloads inside the build directory on restricted cloud machines.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
