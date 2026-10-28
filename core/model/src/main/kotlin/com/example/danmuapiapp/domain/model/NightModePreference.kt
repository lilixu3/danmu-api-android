package com.example.danmuapiapp.domain.model

enum class NightModePreference(val storageValue: Int) {
    FollowSystem(0),
    Light(1),
    Dark(2);

    companion object {
        fun fromStorageValue(value: Int): NightModePreference {
            return entries.firstOrNull { it.storageValue == value } ?: FollowSystem
        }
    }
}
