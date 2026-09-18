plugins {
    id("com.lagradost.cloudstream3.gradle")
}

version = 6

android {
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}

cloudstream {
    language = "en"
    authors = listOf("Cloudstream Extension")
    description = "Only NSFW Movies Available"
    status = 1
    tvTypes = listOf(
        "Movie"
    )
    isCrossPlatform = false
    iconUrl = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://www.wowxxx.to/&size=128"
}
