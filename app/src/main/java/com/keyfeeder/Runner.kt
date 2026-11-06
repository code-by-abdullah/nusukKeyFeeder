package com.keyfeeder

object Runner {
    @Volatile var keys: List<String> = emptyList()
    @Volatile var running = false
    @Volatile var index = 0
    @Volatile var delayMs = 2500L
    @Volatile var failed = mutableListOf<String>()
}
