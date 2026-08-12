plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "vasuki.istanpdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "vasuki.istanpdf"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.5-Mitsuba"
    }

    buildFeatures {
        viewBinding = true
    }

    sourceSets["main"].java.srcDirs("src/main/java")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.itext7.kernel)
    implementation(libs.itext7.io)
    implementation(libs.itext7.layout)
    implementation(libs.itext7.html2pdf)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.strikethrough)
    implementation(libs.commonmark.tasklist)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.ins)
    implementation(libs.androidx.viewpager2)
    implementation(libs.xz)
    implementation(libs.commons.compress)
}
