package com.kyant.backdrop

import android.graphics.RuntimeShader as AndroidRuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

typealias RuntimeShader = AndroidRuntimeShader

interface RuntimeShaderCache {
    fun obtainRuntimeShader(key: String, string: String): RuntimeShader
    fun clear()
}

private object AndroidRuntimeShaderCache : RuntimeShaderCache {
    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) { AndroidRuntimeShader(string) }
    }

    override fun clear() {
        runtimeShaders.clear()
    }
}

fun getRuntimeShaderCache(): RuntimeShaderCache = AndroidRuntimeShaderCache
