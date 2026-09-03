package com.example.danmuapiapp.data.service

/** Keeps the notification text compact in the collapsed view and readable when expanded. */
internal object NotificationEndpointTextPolicy {

    fun compact(status: CharSequence, infoTitle: CharSequence, infoText: CharSequence): String {
        return listOf(statusText(status), infoLine(infoTitle, infoText))
            .filter(String::isNotBlank)
            .joinToString(" · ")
    }

    fun expanded(status: CharSequence, infoTitle: CharSequence, infoText: CharSequence): String {
        return listOf(statusText(status), infoLine(infoTitle, infoText))
            .filter(String::isNotBlank)
            .joinToString("\n")
    }

    private fun statusText(value: CharSequence): String = value.toString().trim()

    private fun infoLine(title: CharSequence, value: CharSequence): String {
        val titleText = title.toString().trim()
        val valueText = value.toString().trim()
        return when {
            titleText.isBlank() || valueText.isBlank() -> valueText
            else -> "$titleText：$valueText"
        }
    }
}
