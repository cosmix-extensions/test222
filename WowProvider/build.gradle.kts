version = 5

cloudstream {
    language = "en"
    authors = listOf("Cloudstream Extension")
    description = "Only NSFW Movies Avalibale"
    status = 1
    tvTypes = listOf(
        "Movie"
    )
    isCrossPlatform = false
    iconUrl = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://www.wowxxx.to/&size=128"
}

dependencies {
    val appcompatVersion = "1.6.1"
    compileOnly("androidx.appcompat:appcompat:$appcompatVersion")
    compileOnly("com.google.android.material:material:1.9.0")
    compileOnly("androidx.preference:preference-ktx:1.2.0")
}
