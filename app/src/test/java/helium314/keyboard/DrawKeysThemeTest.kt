package helium314.keyboard

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DrawKeysThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.prefs().edit().clear().commit()
    }

    @Test
    fun defaultsContract() {
        assertFalse("PREF_DRAW_KEYS default must remain false", Defaults.PREF_DRAW_KEYS)
        assertEquals("Preference key must be draw_keys", "draw_keys", Settings.PREF_DRAW_KEYS)
    }

    @Test
    fun settingsReadDrawKeys_defaultIsFalse() {
        val prefs = context.prefs()
        assertFalse(Settings.readDrawKeys(prefs))
    }

    @Test
    fun settingsReadDrawKeys_legacyKeyBordersFallback() {
        val prefs = context.prefs()
        prefs.edit {
            putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
        }
        assertTrue(Settings.readDrawKeys(prefs))
    }

    @Test
    fun settingsReadDrawKeys_explicitValuePrecedence() {
        val prefs = context.prefs()
        // When draw_keys is true, returns true
        prefs.edit {
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }
        assertTrue(Settings.readDrawKeys(prefs))

        // When draw_keys is false, returns false even if legacy key borders was true
        prefs.edit {
            putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
            putBoolean(Settings.PREF_DRAW_KEYS, false)
        }
        assertFalse(Settings.readDrawKeys(prefs))
    }

    @Test
    fun keyboardTheme_switchesOnDrawKeys() {
        val prefs = context.prefs()

        // Default: draw_keys is false -> THEME_ID_LXX_BASE
        val defaultTheme = KeyboardTheme.getKeyboardTheme(context)
        assertEquals(1, defaultTheme.themeId)

        // draw_keys is true -> THEME_ID_LXX_BASE_BORDER
        prefs.edit {
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }
        val borderTheme = KeyboardTheme.getKeyboardTheme(context)
        assertEquals(2, borderTheme.themeId)
    }

    @Test
    fun keyboardTheme_roundedStyleSwitchesOnDrawKeys() {
        val prefs = context.prefs()
        prefs.edit {
            putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_ROUNDED)
        }

        val roundedDefaultTheme = KeyboardTheme.getKeyboardTheme(context)
        assertEquals(3, roundedDefaultTheme.themeId) // THEME_ID_ROUNDED_BASE

        prefs.edit {
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }
        val roundedBorderTheme = KeyboardTheme.getKeyboardTheme(context)
        assertEquals(4, roundedBorderTheme.themeId) // THEME_ID_ROUNDED_BASE_BORDER
    }

    @Test
    fun colors_hasKeyBordersReflectsDrawKeys() {
        val prefs = context.prefs()

        val defaultColors = KeyboardTheme.getColorsForCurrentTheme(context)
        assertFalse(defaultColors.hasKeyBorders)

        prefs.edit {
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }
        val borderColors = KeyboardTheme.getColorsForCurrentTheme(context)
        assertTrue(borderColors.hasKeyBorders)
    }

    @Test
    fun keyCaps_defaultOff_alphaIsOpaque() {
        val defaultColors = KeyboardTheme.getColorsForCurrentTheme(context)
        assertFalse("Default must have hasKeyBorders=false", defaultColors.hasKeyBorders)
        assertEquals("Default letter key color must have full alpha 255", 255, android.graphics.Color.alpha(defaultColors.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)))
        assertEquals("Default functional key color must have full alpha 255", 255, android.graphics.Color.alpha(defaultColors.get(helium314.keyboard.latin.common.ColorType.FUNCTIONAL_KEY_BACKGROUND)))
    }

    @Test
    fun keyCaps_drawKeysOn_translucentAlphas() {
        val prefs = context.prefs()
        prefs.edit {
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }

        val borderColors = KeyboardTheme.getColorsForCurrentTheme(context)
        assertTrue(borderColors.hasKeyBorders)

        val keyBg = borderColors.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)
        val functionalBg = borderColors.get(helium314.keyboard.latin.common.ColorType.FUNCTIONAL_KEY_BACKGROUND)
        val spaceBarBg = borderColors.get(helium314.keyboard.latin.common.ColorType.SPACE_BAR_BACKGROUND)

        assertEquals("Letter keys must have ~60% alpha (153)", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_LETTER, android.graphics.Color.alpha(keyBg))
        assertEquals("Functional keys must have ~72% alpha (184)", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_FUNCTIONAL, android.graphics.Color.alpha(functionalBg))
        assertEquals("Spacebar must have ~60% alpha (153)", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_LETTER, android.graphics.Color.alpha(spaceBarBg))
    }

    @Test
    fun keyCaps_darkTheme_translucentAndReadable() {
        val prefs = context.prefs()
        prefs.edit {
            putBoolean(Settings.PREF_DRAW_KEYS, true)
            putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_DARK)
        }

        val darkColors = KeyboardTheme.getColorsForCurrentTheme(context)
        val keyBg = darkColors.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)
        val funcBg = darkColors.get(helium314.keyboard.latin.common.ColorType.FUNCTIONAL_KEY_BACKGROUND)

        assertEquals(helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_LETTER, android.graphics.Color.alpha(keyBg))
        assertEquals(helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_FUNCTIONAL, android.graphics.Color.alpha(funcBg))

        val keyText = darkColors.get(helium314.keyboard.latin.common.ColorType.KEY_TEXT)
        assertEquals("Dark theme text is white", android.graphics.Color.WHITE, keyText)
    }

    @Test
    fun keyCaps_allColors_translucentAlphas() {
        val colorMap = java.util.EnumMap<helium314.keyboard.latin.common.ColorType, Int>(helium314.keyboard.latin.common.ColorType::class.java)
        colorMap[helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND] = android.graphics.Color.WHITE
        colorMap[helium314.keyboard.latin.common.ColorType.FUNCTIONAL_KEY_BACKGROUND] = android.graphics.Color.LTGRAY

        val allColors = helium314.keyboard.latin.common.AllColors(
            colorMap,
            KeyboardTheme.STYLE_MATERIAL,
            hasKeyBorders = true,
            backgroundImage = null
        )
        val keyBg = allColors.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)
        val funcBg = allColors.get(helium314.keyboard.latin.common.ColorType.FUNCTIONAL_KEY_BACKGROUND)
        assertEquals("AllColors letter keys must have ~60% alpha", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_LETTER, android.graphics.Color.alpha(keyBg))
        assertEquals("AllColors functional keys must have ~72% alpha", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_FUNCTIONAL, android.graphics.Color.alpha(funcBg))

        val allColorsNoBorders = helium314.keyboard.latin.common.AllColors(
            colorMap,
            KeyboardTheme.STYLE_MATERIAL,
            hasKeyBorders = false,
            backgroundImage = null
        )
        assertEquals(255, android.graphics.Color.alpha(allColorsNoBorders.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)))
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [31])
    fun keyCaps_dynamicColors_translucentAlphas() {
        val dynamicColors = helium314.keyboard.latin.common.DynamicColors(
            context,
            KeyboardTheme.STYLE_MATERIAL,
            hasKeyBorders = true,
            keyboardBackground = null
        )
        val keyBg = dynamicColors.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)
        val funcBg = dynamicColors.get(helium314.keyboard.latin.common.ColorType.FUNCTIONAL_KEY_BACKGROUND)
        val spaceBarBg = dynamicColors.get(helium314.keyboard.latin.common.ColorType.SPACE_BAR_BACKGROUND)

        assertEquals("Dynamic letter keys must have ~60% alpha", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_LETTER, android.graphics.Color.alpha(keyBg))
        assertEquals("Dynamic functional keys must have ~72% alpha", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_FUNCTIONAL, android.graphics.Color.alpha(funcBg))
        assertEquals("Dynamic spacebar must have ~60% alpha", helium314.keyboard.latin.common.DRAW_KEYS_ALPHA_LETTER, android.graphics.Color.alpha(spaceBarBg))
    }
}
