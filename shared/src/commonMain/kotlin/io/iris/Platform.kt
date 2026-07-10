package io.iris

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform