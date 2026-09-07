package pl.lejdi.plannerkmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform