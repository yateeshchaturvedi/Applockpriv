package com.yateeshpriv.applockpriv

enum class AuthMethod(val value: String, val label: String) {
    PASSCODE("passcode", "Passcode"),
    PATTERN("pattern", "Pattern"),
    ALPHANUMERIC("alphanumeric", "Alphanumeric"),
    FINGERPRINT("fingerprint", "Fingerprint");

    fun requiresSecret(): Boolean = this != FINGERPRINT

    companion object {
        fun fromValue(value: String?): AuthMethod {
            return entries.firstOrNull { it.value == value } ?: PASSCODE
        }
    }
}
