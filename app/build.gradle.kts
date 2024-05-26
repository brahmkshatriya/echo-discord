plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "1.9.22"
}

val extensionClass = "DiscordRPC"
val id = "discord-rpc"
val name = "Discord RPC"
val version = "1.0.0"
val description = "Discord RPC Extension for Echo."
val author = "Echo"
val iconUrl =
    "https://yt3.googleusercontent.com/ytc/AIdro_lBFnWS0XsOc_PbUCpWpTTUtcUa3CJNO8oXqrjnc9IA_A4=s176-c-k-c0x00ffffff-no-rj-mo"

android {
    namespace = "dev.brahmkshatriya.echo.extension"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo.extension.discordrpc"
        minSdk = 24
        targetSdk = 34

        versionCode = 1
        versionName = version

        resValue("string", "app_name", "Echo : $name Extension")
        resValue("string", "class_path", "$namespace.$extensionClass")
        resValue("string", "name", name)
        resValue("string", "id", id)
        resValue("string", "version", version)
        resValue("string", "description", description)
        resValue("string", "author", author)
        resValue("string", "icon_url", iconUrl)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            this.isReturnDefaultValues = true
        }
    }


}



dependencies {
    val libVersion = "38e1df03f6"
    compileOnly("com.github.brahmkshatriya:echo:$libVersion")
    implementation("com.github.Blatzar:NiceHttp:0.4.4")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1-Beta")
    testImplementation("com.github.brahmkshatriya:echo:$libVersion")

    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1-Beta")
    androidTestImplementation("com.github.brahmkshatriya:echo:$libVersion")
}