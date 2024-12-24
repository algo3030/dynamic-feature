plugins {
    alias(libs.plugins.android.dynamic.feature)
}
android {
    namespace = "com.example.feature.assets"
    compileSdk = 35

    defaultConfig{
        minSdk = 24
    }
}

dependencies {
    implementation(project(":app"))
}