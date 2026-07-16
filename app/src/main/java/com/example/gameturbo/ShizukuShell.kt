package com.example.gameturbo

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShell {

    fun run(command: Array<String>): String {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, command, null, null) as Process
        val output = BufferedReader(InputStreamReader(process.inputStream))
            .readText()
        process.waitFor()
        return output
    }
}
