package org.example.desktop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform