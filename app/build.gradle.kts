plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.folio.reader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.folio.reader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    // ZIP 网页包本地资源加载(Phase 4)
    implementation(libs.androidx.webkit)
    // Markdown 原生渲染(Phase 2)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.image)
    implementation(libs.markwon.html)
    // Phase 2.5:数学公式 + 宽内容横向滚动
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.recycler)
    implementation(libs.markwon.recycler.table)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
