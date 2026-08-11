package dev.simnple.revanced.patches.goondori

import org.w3c.dom.Element

internal const val ANDROID_NAME = "android:name"
internal const val ANDROID_VALUE = "android:value"

internal fun Element.setMetaData(name: String, value: String) {
    val nodes = getElementsByTagName("meta-data")
    val existing = (0 until nodes.length)
        .mapNotNull { nodes.item(it) as? Element }
        .firstOrNull { it.getAttribute(ANDROID_NAME) == name }

    (existing ?: ownerDocument.createElement("meta-data").also { appendChild(it) }).apply {
        setAttribute(ANDROID_NAME, name)
        setAttribute(ANDROID_VALUE, value)
    }
}
