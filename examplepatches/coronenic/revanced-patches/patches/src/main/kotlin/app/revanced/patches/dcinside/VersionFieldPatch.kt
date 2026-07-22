package app.revanced.patches.dcinside

import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Adds a "Revanced 패치 버전" row to Settings ("설정") > About ("정보"), directly below the
 * "현재 버전" (setting_now_version) and "최신 버전" (setting_last_version) fields in
 * res/layout/fragment_settings.xml.
 *
 * Pure ResourcePatch (DOM edit of a layout) — no bytecode, no fingerprints.
 * Also serves as the toolchain smoke test for this patch bundle.
 */
val addRevancedVersionFieldPatch = resourcePatch(
    name = "Add ReVanced patch version field",
    description = "Adds a 'Revanced 패치 버전' field below the current and latest version in Settings > About.",
    use = true,
) {
    compatibleWith("com.dcinside.app.android")

    apply {
        document("res/layout/fragment_settings.xml").use { doc ->
            // Locate the "최신 버전" title TextView.
            // Match by id suffix: apktool decodes ids as "@id/…", jadx shows "@+id/…".
            // The parser is not namespace-aware, so read the qualified attribute name directly.
            val textViews = doc.getElementsByTagName("TextView")
            var lastVersionTitle: Element? = null
            for (i in 0 until textViews.length) {
                val el = textViews.item(i) as Element
                if (el.getAttribute("android:id").endsWith("setting_last_version_title")) {
                    lastVersionTitle = el
                    break
                }
            }
            checkNotNull(lastVersionTitle) { "setting_last_version_title not found in fragment_settings.xml" }

            // TextView -> ConstraintLayout (last-version cell) -> horizontal LinearLayout (version row).
            val versionRow = lastVersionTitle.parentNode.parentNode as Element
            // The vertical LinearLayout that holds the whole "정보" section.
            val container = versionRow.parentNode as Element

            fun Element.androidAttr(name: String, value: String) = setAttribute("android:$name", value)

            // New sibling row, mirroring the layout style of the existing rows.
            val row = doc.createElement("LinearLayout").apply {
                androidAttr("orientation", "horizontal")
                androidAttr("gravity", "center_vertical")
                androidAttr("layout_width", "match_parent")
                androidAttr("layout_height", "50dp")
            }

            val title = doc.createElement("TextView").apply {
                androidAttr("textAppearance", "?attr/textTypeDefault")
                androidAttr("layout_gravity", "center_vertical")
                androidAttr("layout_width", "wrap_content")
                androidAttr("layout_height", "wrap_content")
                androidAttr("text", "Revanced 패치 버전")
                androidAttr("layout_marginStart", "10dp")
            }
            row.appendChild(title)

            val value = doc.createElement("TextView").apply {
                androidAttr("textAppearance", "?attr/textTypeDefault")
                androidAttr("textColor", "?attr/colorPrimaryText")
                androidAttr("gravity", "end")
                androidAttr("layout_gravity", "center_vertical")
                androidAttr("layout_width", "0dp")
                androidAttr("layout_height", "wrap_content")
                androidAttr("layout_weight", "1")
                androidAttr("text", "v1.3.1")
                androidAttr("layout_marginEnd", "20dp")
            }
            row.appendChild(value)

            // Insert directly after the current/latest version row.
            container.insertBefore(row, versionRow.nextSibling)
        }
    }
}
