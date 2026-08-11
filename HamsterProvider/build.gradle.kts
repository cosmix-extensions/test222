version = 9

cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/recloudstream/cloudstream-extensions")
    description = "xHamster - Free Porn Videos & XXX Movies"
    authors = listOf("Cloudstream Extension")
    language = "en"
    tvTypes = listOf("Movie")
    iconUrl = "https://static-ah.xhcdn.com/xh-desktop/images/favicon/favicon-v2-128x128.png"
    status = 1
    isCrossPlatform = true
}

android {
    namespace = "com.hamster"
}

