package com.example.huzz.ui

/**
 * UI constants/utilities for nickname rendering.
 */
fun truncateNickname(name: String, maxLen: Int = com.example.huzz.util.AppConstants.UI.MAX_NICKNAME_LENGTH): String {
    return if (name.length <= maxLen) name else name.take(maxLen)
}

