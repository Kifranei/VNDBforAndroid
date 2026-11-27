package com.kyant.backdrop.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object PlatformCapabilities {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun hasShaderCapability(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun hasAdvancedShaderCapability(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}