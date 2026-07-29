package app.revanced.patches.dcinside

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import org.w3c.dom.Element

/**
 * Turns the "ReVanced 패치 버전" row in Settings ("설정") > About ("정보") into the entry point of a
 * ReVanced settings page, and gives the other patches in this bundle a way to put their own settings
 * on it.
 *
 * The row is inserted below "현재 버전" / "최신 버전" in res/layout/fragment_settings.xml as an
 * extension view class ([SETTINGS_ENTRY_VIEW]), so it wires its own click when the app inflates the
 * layout — no hook into the app's obfuscated settings fragment, and no new layout resources.
 *
 * Registration is a bytecode hook: the extension ships an empty
 * `Settings.declarePatchSettings()`, and [addSwitchSetting] appends one `registerSwitch(...)` call to
 * it per setting. Only the settings of patches that were actually applied therefore exist at runtime.
 */

private const val PATCH_VERSION = "v1.5.0"

private const val EXTENSION_PACKAGE = "app.revanced.extension.dcinside.settings"
private const val SETTINGS_ENTRY_VIEW = "$EXTENSION_PACKAGE.SettingsEntryView"
private const val SETTINGS_ACTIVITY = "$EXTENSION_PACKAGE.SettingsActivity"

private const val SETTINGS_CLASS = "Lapp/revanced/extension/dcinside/settings/Settings;"
private const val DECLARE_METHOD = "declarePatchSettings"

/** `const-string v0..v2` + `const/4 v3` for one registerSwitch(String, String, String, boolean). */
private const val DECLARE_REGISTERS = 4

/** Adds the settings entry row and registers the settings page activity. */
val revancedSettingsResourcePatch = resourcePatch(
    name = "ReVanced settings resources",
    description = "Layout + manifest changes for the ReVanced settings page.",
    use = false,
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

            // New sibling row, mirroring the app's own clickable rows (e.g. setting_license).
            val row = doc.createElement(SETTINGS_ENTRY_VIEW).apply {
                androidAttr("orientation", "horizontal")
                androidAttr("gravity", "center_vertical")
                androidAttr("background", "?attr/selectableItemBackground")
                androidAttr("layout_width", "match_parent")
                androidAttr("layout_height", "50dp")
            }

            val title = doc.createElement("TextView").apply {
                androidAttr("textAppearance", "?attr/textTypeDefault")
                androidAttr("layout_gravity", "center_vertical")
                androidAttr("layout_width", "wrap_content")
                androidAttr("layout_height", "wrap_content")
                androidAttr("text", "ReVanced 패치 버전")
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
                androidAttr("text", PATCH_VERSION)
                androidAttr("layout_marginEnd", "20dp")
            }
            row.appendChild(value)

            // The "opens a page" affordance the app uses on its own navigating rows.
            val arrow = doc.createElement("ImageView").apply {
                androidAttr("padding", "10dp")
                androidAttr("layout_width", "wrap_content")
                androidAttr("layout_height", "wrap_content")
                androidAttr("src", "@drawable/list_arrow")
                androidAttr("scaleType", "centerInside")
                androidAttr("contentDescription", "@null")
                setAttribute("app:tint", "?attr/icTintNormal")
            }
            row.appendChild(arrow)

            // Insert directly after the current/latest version row.
            container.insertBefore(row, versionRow.nextSibling)
        }

        document("AndroidManifest.xml").use { doc ->
            val application = doc.getElementsByTagName("application").item(0) as Element
            val activity = doc.createElement("activity")
            activity.setAttribute("android:name", SETTINGS_ACTIVITY)
            activity.setAttribute("android:exported", "false")
            application.appendChild(activity)
        }
    }
}

val revancedSettingsPatch = bytecodePatch(
    name = "ReVanced settings",
    description = "Makes the 'ReVanced 패치 버전' field in Settings > About open a ReVanced settings " +
        "page, which lists the settings of the applied patches.",
    use = true,
) {
    compatibleWith("com.dcinside.app.android")

    dependsOn(revancedSettingsResourcePatch)

    extendWith("dcinside/settings.rve")

    apply {
        // The extension ships declarePatchSettings() as a bare `return-void`, so its implementation
        // has no registers. Give it the ones addSwitchSetting's appended calls need.
        val declare = settingsRegistry.methods.first { it.name == DECLARE_METHOD }
        declare.implementation = MutableMethodImplementation(DECLARE_REGISTERS)
        declare.addInstructions(0, "return-void")
    }
}

private val BytecodePatchContext.settingsRegistry
    get() = (classBy { it.type == SETTINGS_CLASS }
        ?: error("settings extension not merged — depend on revancedSettingsPatch")).mutableClass

/**
 * Puts an on/off setting on the ReVanced settings page. The patch calling this must
 * `dependsOn(revancedSettingsPatch)`; the value is read back with
 * `Settings.isEnabled(context, key)`, which falls back to [defaultValue] declared here.
 */
fun BytecodePatchContext.addSwitchSetting(
    key: String,
    title: String,
    summary: String,
    defaultValue: Boolean = false,
) {
    val declare = settingsRegistry.methods.first { it.name == DECLARE_METHOD }
    declare.addInstructions(
        declare.implementation!!.instructions.count() - 1,
        """
            const-string v0, "${key.toSmaliLiteral()}"
            const-string v1, "${title.toSmaliLiteral()}"
            const-string v2, "${summary.toSmaliLiteral()}"
            const/4 v3, ${if (defaultValue) "0x1" else "0x0"}
            invoke-static { v0, v1, v2, v3 }, $SETTINGS_CLASS->registerSwitch(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V
        """,
    )
}

/** Escapes to pure ASCII so no charset in the smali assembler path can mangle Korean text. */
private fun String.toSmaliLiteral() = buildString {
    for (character in this@toSmaliLiteral) when {
        character == '\\' || character == '"' -> append('\\').append(character)
        character == '\n' -> append("\\n")
        character.code in 0x20..0x7E -> append(character)
        else -> append("\\u%04x".format(character.code))
    }
}
