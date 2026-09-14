/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.core.content.ContextCompat
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.vibevoice.VoiceGlow
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import androidx.core.view.isGone
import helium314.keyboard.latin.utils.onClickToolbarKey
import helium314.keyboard.latin.utils.onLongClickToolbarKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("InflateParams")
class SuggestionStripView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle), View.OnClickListener, OnLongClickListener, OnSharedPreferenceChangeListener {

    /** Construct a [SuggestionStripView] for showing suggestions to be picked by the user. */
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.suggestionStripViewStyle)

    interface Listener {
        fun pickSuggestionManually(word: SuggestedWordInfo?)
        fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)
        fun removeSuggestion(word: String?)
        fun removeExternalSuggestions()
        fun onSwipeDownOnToolbar()
    }

    private val moreSuggestionsContainer: View
    private val wordViews = ArrayList<TextView>()
    private val debugInfoViews = ArrayList<TextView>()
    private val dividerViews = ArrayList<View>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.suggestions_strip, this)
        moreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null)

        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        repeat(SuggestedWords.MAX_SUGGESTIONS) {
            val word = TextView(context, null, R.attr.suggestionWordStyle)
            word.contentDescription = resources.getString(R.string.spoken_empty_suggestion)
            word.setOnClickListener(this)
            word.setOnLongClickListener(this)
            colors.setBackground(word, ColorType.STRIP_BACKGROUND)
            wordViews.add(word)
            val divider = inflater.inflate(R.layout.suggestion_divider, null)
            dividerViews.add(divider)
            val info = TextView(context, null, R.attr.suggestionWordStyle)
            info.setTextColor(colors.get(ColorType.KEY_TEXT))
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP)
            debugInfoViews.add(info)
        }

        DEBUG_SUGGESTIONS = context.prefs().getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, Defaults.PREF_SHOW_SUGGESTION_INFOS)
    }

    // toolbar views, drawables and setup
    private val toolbar: ViewGroup = findViewById(R.id.toolbar)
    private val toolbarContainer: ToolbarScrollView = findViewById(R.id.toolbar_container)
    private val pinnedKeys: ViewGroup = findViewById(R.id.pinned_keys)
    /** The fixed right edge. Holds the VibeVoice key, never scrolls, never shrinks. */
    private val voiceAnchor: ViewGroup = findViewById(R.id.voice_anchor)
    private val suggestionsStrip: ViewGroup = findViewById(R.id.suggestions_strip)
    private val toolbarExpandKey = findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val incognitoIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.INCOGNITO.name, context)
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private val defaultToolbarBackground: Drawable = toolbarExpandKey.background
    private val enabledToolKeyBackground = GradientDrawable()
    private var direction = 1 // 1 if LTR, -1 if RTL

    private val toolbarKeyWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)

    /**
     * Fresh params per key, never one instance shared between them.
     *
     * It used to be a single `val` handed to every key, and LinearLayout keeps the reference rather
     * than copying it -- so setting a width or a weight through any one key set it for all of them,
     * including the pinned row and the close button. That already produced one bug. It would
     * produce another immediately below, where [fitToolbarKeys] narrows the carousel's keys and
     * must not narrow anything else.
     */
    private fun newToolbarKeyParams() =
        LinearLayout.LayoutParams(toolbarKeyWidth, LinearLayout.LayoutParams.MATCH_PARENT)

    init {
        val colors = Settings.getValues().mColors

        // expand key
        // weird way of setting size (default is config_suggestions_strip_edge_key_width)
        // but better not change it or people will complain
        val toolbarHeight = min(toolbarExpandKey.layoutParams.height, resources.getDimension(R.dimen.config_suggestions_strip_height).toInt())
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight // we want it square
        colors.setBackground(toolbarExpandKey, ColorType.STRIP_BACKGROUND) // necessary because background is re-used for defaultToolbarBackground
        colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) / 2.1f

        val mToolbarMode = if (isGone) ToolbarMode.HIDDEN else Settings.getValues().mToolbarMode
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS) {
            setToolbarVisibility(true)
        }

        // toolbar keys setup (no need to hide them any more when locked, because then suggestion strip is gone anyway
        //
        // VOICE is filtered out of both lists and given its own anchor instead. In the carousel it
        // scrolled out of reach whenever the tools were open; among the pinned keys it sat inside
        // the region that shrinks, so a strip of long suggestions at a high display zoom pushed it
        // past the right edge. Neither is acceptable for the control that stops a recording.
        val enabledKeys = getEnabledToolbarKeys(context.prefs())
        val pinnedKeyList = getPinnedToolbarKeys(context.prefs())
        for (key in enabledKeys) {
            if (key == ToolbarKey.VOICE) continue
            val button = createToolbarKey(context, key)
            button.layoutParams = newToolbarKeyParams()
            setupKey(button, colors)
            toolbar.addView(button)
        }
        for (pinnedKey in pinnedKeyList) {
            if (pinnedKey == ToolbarKey.VOICE) continue
            val button = createToolbarKey(context, pinnedKey)
            button.layoutParams = newToolbarKeyParams()
            setupKey(button, colors)
            pinnedKeys.addView(button)
            val pinnedKeyInToolbar = toolbar.findViewWithTag<View>(pinnedKey)
            if (pinnedKeyInToolbar != null && Settings.getValues().mQuickPinToolbarKeys)
                pinnedKeyInToolbar.background = enabledToolKeyBackground
        }
        // Anchored, but still the user's to switch off. Creating it unconditionally made the
        // toolbar customiser lie: turning VOICE off there changed nothing, because this bypassed
        // the preference that screen writes. Anchoring is about where the key sits when it exists,
        // not about overruling somebody who does not want it.
        if (ToolbarKey.VOICE in enabledKeys || ToolbarKey.VOICE in pinnedKeyList) {
            val button = createToolbarKey(context, ToolbarKey.VOICE)
            button.layoutParams = newToolbarKeyParams()
            setupKey(button, colors)
            voiceAnchor.addView(button)
        }
        // Every layout, not just the first one. The carousel's width changes with the display zoom,
        // with a rotation, with one-handed mode, and it is zero for as long as the toolbar is
        // hidden -- which is the state it is inflated in. A single shot could therefore run against
        // a width that was never the real one and leave the fit stale for the life of the view.
        // Posted because the fit sets layout params, which must not happen during a layout pass.
        toolbarContainer.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft)
                toolbarContainer.post { fitToolbarKeys() }
        }

        updateKeys()
    }

    private lateinit var listener: Listener
    private var suggestedWords = SuggestedWords.getEmptyInstance()
    private var startIndexOfMoreSuggestions = 0
    private var isExternalSuggestionVisible = false // Required to disable the more suggestions if other suggestions are visible
    private val layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
    private val moreSuggestionsView = moreSuggestionsContainer.findViewById<MoreSuggestionsView>(R.id.more_suggestions_view).apply {
        val slidingListener = object : SimpleOnGestureListener() {
            override fun onScroll(down: MotionEvent?, me: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
                if (down == null) return false
                val dy = me.y - down.y
                val dx = me.x - down.x

                if (Settings.getValues().mToolbarSwipeDownToHide && dy > 50.dpToPx(resources) && abs(dy) > abs(dx)) {
                    listener.onSwipeDownOnToolbar()
                    return true
                }

                return if (!isExternalSuggestionVisible && toolbarContainer.visibility != VISIBLE && deltaY > 0 && dy < (-10).dpToPx(resources)) showMoreSuggestions()
                else false
            }
        }
        gestureDetector = GestureDetector(context, slidingListener)
    }

    // public stuff

    val isShowingMoreSuggestionPanel get() = moreSuggestionsView.isShowingInParent

    /** A connection back to the input method. */
    fun setListener(newListener: Listener, inputView: View) {
        listener = newListener
        moreSuggestionsView.listener = newListener
        moreSuggestionsView.mainKeyboardView = inputView.findViewById(R.id.keyboard_view)
    }

    fun setRtl(isRtlLanguage: Boolean) {
        val newLayoutDirection: Int
        if (!Settings.getValues().mVarToolbarDirection)
            newLayoutDirection = LAYOUT_DIRECTION_LOCALE
        else {
            newLayoutDirection = if (isRtlLanguage) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            direction = if (isRtlLanguage) -1 else 1
            toolbarExpandKey.scaleX = (if (toolbarContainer.visibility != VISIBLE) 1f else -1f) * direction
        }
        layoutDirection = newLayoutDirection
        suggestionsStrip.layoutDirection = newLayoutDirection
    }

    fun setToolbarVisibility(toolbarVisible: Boolean) {
        pinnedKeys.isVisible = !toolbarVisible
        suggestionsStrip.isVisible = !toolbarVisible
        toolbarContainer.isVisible = toolbarVisible

        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

        toolbarExpandKey.scaleX = (if (toolbarVisible) -1f else 1f) * direction
    }

    fun setSuggestions(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        clear()
        setRtl(isRtlLanguage)
        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
            context, suggestedWords, suggestionsStrip, this
        )
        isExternalSuggestionVisible = false
        updateKeys()
    }

    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean) {
        clear()
        isExternalSuggestionVisible = true

        if (addCloseButton) {
            val wrapper = LinearLayout(context)
            suggestionsStrip.doOnNextLayout {
                wrapper.layoutParams = LinearLayout.LayoutParams(suggestionsStrip.width - 30.dpToPx(resources), LayoutParams.MATCH_PARENT)
            }
            wrapper.addView(view)
            suggestionsStrip.addView(wrapper)

            val closeButton = createToolbarKey(context, ToolbarKey.CLOSE_HISTORY)
            closeButton.layoutParams = newToolbarKeyParams()
            setupKey(closeButton, Settings.getValues().mColors)
            closeButton.setOnClickListener {
                listener.removeExternalSuggestions()
            }
            suggestionsStrip.addView(closeButton)
        } else {
            suggestionsStrip.addView(view)
        }

        if (Settings.getValues().mAutoHideToolbar) setToolbarVisibility(false)
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        layoutHelper.setMoreSuggestionsHeight(remainingHeight)
    }

    fun dismissMoreSuggestionsPanel() {
        moreSuggestionsView.dismissPopupKeysPanel()
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
        if (key == Settings.PREF_ALWAYS_INCOGNITO_MODE)
            GlobalScope.launch { delay(10); withContext(Dispatchers.Main) { updateKeys() } }
        if (key == Settings.PREF_VOICE_KEY_PULSE)
            post { updateVoiceKey() }
    }

    override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/HeliBorg/HeliBoard/pull/386
        //
        // Guarded the way clear() guards the same invariant. Both the toolbar and the suggestion
        // strip carry weight now, so showing them together splits the row in half instead of one
        // simply covering the other. That state was reachable: open the toolbar, tap emoji or
        // clipboard and come back, and this line forced the strip visible underneath it.
        if (view === this && !toolbarContainer.isVisible)
            suggestionsStrip.visibility = visibility
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopVoicePulse(clearPref = true)
        dismissMoreSuggestionsPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overridden by showing suggestions later, if applicable.
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        // Don't populate accessibility event with suggested words and voice key.
        return true
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Detecting sliding up finger to show MoreSuggestionsView.
        return moreSuggestionsView.shouldInterceptTouchEvent(motionEvent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        moreSuggestionsView.touchEvent(motionEvent)
        return true
    }

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag is ToolbarKey) {
            onClickToolbarKey(view) { listener.onCodeInput(it, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false) }
            return
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
        if (view === toolbarExpandKey) {
            setToolbarVisibility(toolbarContainer.visibility != VISIBLE)
        }

        // tag for word views is set in SuggestionStripLayoutHelper (setupWordViewsTextAndColor, layoutPunctuationSuggestions)
        if (tag is Int) {
            if (tag >= suggestedWords.size()) {
                return
            }
            val wordInfo = suggestedWords.getInfo(tag)
            listener.pickSuggestionManually(wordInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (view.tag is ToolbarKey) {
            onLongClickToolbarKey(view)
            return true
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
        return if (view is TextView && wordViews.contains(view)) {
            onLongClickSuggestion(view)
        } else {
            showMoreSuggestions()
        }
    }

    // actually private stuff

    private fun onLongClickToolbarKey(view: View) {
        val tag = view.tag as? ToolbarKey ?: return
        // voiceAnchor belongs with pinnedKeys here: neither is a key you can pin or unpin, so a
        // long press on one is an ordinary long press. Without it the anchored voice key consumed
        // the gesture and did nothing -- no haptic, and any custom long-press code configured for
        // VOICE stopped firing -- but only when quick-pin was on, which is the default.
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys || view.parent === voiceAnchor) {
            onLongClickToolbarKey(view) { code, isRepeat -> listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, isRepeat) }
        } else if (view.parent === toolbar) {
            AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
            val pinnedKeyView = pinnedKeys.findViewWithTag<View>(tag)
            if (pinnedKeyView == null) {
                addKeyToPinnedKeys(tag)
                toolbar.findViewWithTag<View>(tag).background = enabledToolKeyBackground
                addPinnedKey(context.prefs(), tag)
            } else if (tag != ToolbarKey.VOICE) {
                removePinnedKey(context.prefs(), tag)
                toolbar.findViewWithTag<View>(tag).background = defaultToolbarBackground.constantState?.newDrawable(resources)
                pinnedKeys.removeView(pinnedKeyView)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            val type = suggestedWords.getInfo(index).mSourceDict
            if (type == Dictionary.DICTIONARY_USER_TYPED || type == Dictionary.DICTIONARY_HARDCODED)
                showIcon = false
        }
        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)!!
            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END
            val downOk = AtomicBoolean(false)
            wordView.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP && downOk.get()) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        removeSuggestion(wordView)
                        wordView.cancelLongPress()
                        wordView.isPressed = false
                        return@setOnTouchListener true
                    }
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        downOk.set(true)
                    }
                }
                false
            }
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel || !showMoreSuggestions())) {
            showSourceDict(wordView)
            return true
        }
        return showMoreSuggestions()
    }

    private fun showMoreSuggestions(): Boolean {
        if (suggestedWords.size() <= startIndexOfMoreSuggestions) {
            return false
        }
        if (!moreSuggestionsView.show(
                suggestedWords, startIndexOfMoreSuggestions, moreSuggestionsContainer, layoutHelper, this
        ))
            return false
        for (i in 0..<startIndexOfMoreSuggestions) {
            wordViews[i].isPressed = false
        }
        return true
    }

    private fun showSourceDict(wordView: TextView) {
        val word = wordView.text.toString()
        val index = wordView.tag as? Int ?: return
        if (index >= suggestedWords.size()) return
        val info = suggestedWords.getInfo(index)
        if (info.word != word) return

        val text = info.mSourceDict.mDictType + ":" + info.mSourceDict.mLocale
        if (isShowingMoreSuggestionPanel) {
            moreSuggestionsView.dismissPopupKeysPanel()
        }
        KeyboardSwitcher.getInstance().showToast(text, true)
    }

    private fun removeSuggestion(wordView: TextView) {
        val word = wordView.text.toString()
        listener.removeSuggestion(word)
        moreSuggestionsView.dismissPopupKeysPanel()
        // show suggestions, but without the removed word
        val suggestedWordInfos = ArrayList<SuggestedWordInfo>()
        for (i in 0..<suggestedWords.size()) {
            val info = suggestedWords.getInfo(i)
            if (info.word != word) suggestedWordInfos.add(info)
        }
        suggestedWords.mRawSuggestions?.removeFirst { it.word == word }

        val newSuggestedWords = SuggestedWords(
            suggestedWordInfos, suggestedWords.mRawSuggestions, suggestedWords.typedWordInfo, suggestedWords.mTypedWordValid,
            suggestedWords.mWillAutoCorrect, suggestedWords.mIsObsoleteSuggestions, suggestedWords.mInputStyle, suggestedWords.mSequenceNumber
        )
        setSuggestions(newSuggestedWords, direction != 1)
        suggestionsStrip.isVisible = true

        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (this.suggestedWords.isEmpty && Settings.getValues().mAutoShowToolbar) {
            setToolbarVisibility(true)
        }
    }

    private fun clear() {
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()
        for (word in wordViews) {
            word.setOnTouchListener(null)
        }
    }

    private fun removeAllDebugInfoViews() {
        for (debugInfoView in debugInfoViews) {
            val parent = debugInfoView.parent
            if (parent is ViewGroup) {
                parent.removeView(debugInfoView)
            }
        }
    }

    /**
     * The glow drawable that sits behind the active microphone key.
     *
     * Centred and not stretched: a BitmapDrawable used as a background is scaled to the view by
     * default, which would pull the light out of shape as the key's size changes.
     */
    private fun buildVoiceGlow(mark: Drawable, box: Int, accent: Int, prefs: SharedPreferences): Drawable? {
        val margin = IntArray(1)
        val bitmap = VoiceGlow.render(
            mark, box, accent,
            prefs.getFloat(Settings.PREF_GLOW_SIZE, Defaults.PREF_GLOW_SIZE),
            prefs.getFloat(Settings.PREF_GLOW_GAIN, Defaults.PREF_GLOW_GAIN),
            margin
        ) ?: return null
        return BitmapDrawable(resources, bitmap).apply { gravity = Gravity.CENTER }
    }

    /** The rendered pieces, and what they were rendered for. */
    private var voiceActiveMark: android.graphics.Bitmap? = null
    private var voiceActiveGlow: Drawable? = null
    private var voiceActiveKey: String? = null

    private var voicePulseRunnable: Runnable? = null
    private var voicePulseTimeoutRunnable: Runnable? = null
    private var voicePulseState = false

    private fun stopVoicePulse(clearPref: Boolean = false) {
        voicePulseRunnable?.let { removeCallbacks(it) }
        voicePulseRunnable = null
        voicePulseTimeoutRunnable?.let { removeCallbacks(it) }
        voicePulseTimeoutRunnable = null
        voicePulseState = false
        if (clearPref) {
            context.prefs().edit().putBoolean(Settings.PREF_VOICE_KEY_PULSE, false).apply()
        }
    }

    private fun startVoicePulse() {
        if (voicePulseRunnable != null) return
        val button = voiceAnchor.findViewWithTag<View>(ToolbarKey.VOICE) ?: return

        // Timeout: automatically stop pulsing and clear pref after 15 seconds
        val timeoutRunnable = Runnable {
            stopVoicePulse(clearPref = true)
            updateVoiceKeyButton(voiceAnchor.findViewWithTag(ToolbarKey.VOICE), true, isActivated = false)
        }
        voicePulseTimeoutRunnable = timeoutRunnable
        postDelayed(timeoutRunnable, 15000L)

        voicePulseState = true
        updateVoiceKeyButton(button, true, isActivated = true)

        val pulseRunnable = object : Runnable {
            override fun run() {
                val ime = KeyboardSwitcher.getInstance().latinIME
                if (ime?.isRecordingVoice == true ||
                    !context.prefs().getBoolean(Settings.PREF_VOICE_KEY_PULSE, Defaults.PREF_VOICE_KEY_PULSE)) {
                    stopVoicePulse(clearPref = true)
                    updateVoiceKeyButton(voiceAnchor.findViewWithTag(ToolbarKey.VOICE), true, isActivated = false)
                    return
                }
                voicePulseState = !voicePulseState
                updateVoiceKeyButton(voiceAnchor.findViewWithTag(ToolbarKey.VOICE), true, isActivated = voicePulseState)
                postDelayed(this, 500L)
            }
        }
        voicePulseRunnable = pulseRunnable
        postDelayed(pulseRunnable, 500L)
    }

    fun updateVoiceKey() {
        val isActivated = KeyboardSwitcher.getInstance().latinIME?.isRecordingVoice == true
        val shouldPulse = !isActivated && context.prefs().getBoolean(Settings.PREF_VOICE_KEY_PULSE, Defaults.PREF_VOICE_KEY_PULSE)
        val button = voiceAnchor.findViewWithTag<View>(ToolbarKey.VOICE)
        if (isActivated) {
            stopVoicePulse(clearPref = true)
            updateVoiceKeyButton(button, true, isActivated = true)
        } else if (shouldPulse) {
            startVoicePulse()
        } else {
            stopVoicePulse()
            updateVoiceKeyButton(button, true, isActivated = false)
        }
    }

    private fun updateVoiceKeyButton(view: View?, show: Boolean, isActivated: Boolean) {
        val button = view as? ImageButton ?: return
        button.isVisible = show
        button.isActivated = isActivated
        if (isActivated) {
            // The full VibeVoice logo while recording -- the two-tone one with the dark backing
            // shape, the same artwork the floating mark uses. Not tinted: TOOL_BAR_KEY would
            // flatten both tones into one and throw away the thing that makes it read as the logo
            // rather than as a glyph.
            val plain = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.VOICE.name, context)
            val inkPx = plain?.intrinsicWidth?.takeIf { it > 0 }
                ?: (VOICE_GLOW_BOX_DP * resources.displayMetrics.density).toInt()
            // Built once and kept. updateVoiceKey runs on every recording state change and on every
            // window show, and rebuilding here meant a software blur and several bitmap
            // allocations on the UI thread each time -- during a rotation, exactly when there is
            // least room for it.
            val prefs = context.prefs()
            val accent = Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL)
            val key = "$inkPx|$accent|" +
                    prefs.getFloat(Settings.PREF_GLOW_SIZE, Defaults.PREF_GLOW_SIZE) + "|" +
                    prefs.getFloat(Settings.PREF_GLOW_GAIN, Defaults.PREF_GLOW_GAIN)
            if (key != voiceActiveKey) {
                val logo = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
                // Sized by its ink, not its viewport: the launcher artwork carries an adaptive
                // icon's padding, so drawn at its own bounds it would sit a third smaller than the
                // key it replaces.
                voiceActiveMark = logo?.let { VoiceGlow.renderMark(it, inkPx) }
                voiceActiveGlow = voiceActiveMark?.let {
                    buildVoiceGlow(BitmapDrawable(resources, it), inkPx, accent, prefs)
                }
                voiceActiveKey = key
            }
            val mark = voiceActiveMark
            button.clearColorFilter()
            if (mark != null) button.setImageBitmap(mark) else button.setImageDrawable(plain)
            button.scaleType = ImageView.ScaleType.CENTER
            // The glow goes on the layer underneath as the view's background, which is also what
            // keeps it out of any tint: a colour filter on an ImageView applies to its image and
            // not to its background.
            button.background = voiceActiveGlow
        } else {
            button.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.VOICE.name, context))
            // No "pinned" highlight to preserve any more: the key is not in the toolbar and not in
            // the pinned row, it is the anchor's only child, so the plain background is always the
            // right one.
            button.background = defaultToolbarBackground.constantState?.newDrawable(resources)
            Settings.getValues().mColors.setColor(button, ColorType.TOOL_BAR_KEY)
            button.scaleType = ImageView.ScaleType.CENTER
            view.rotation = 0f // an older build left the key tilted; clear it once
        }
    }

    private fun updateKeys() {
        updateVoiceKey()
        val settingsValues = Settings.getValues()

        val toolbarIsExpandable = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
        if (settingsValues.mIncognitoModeEnabled) {
            toolbarExpandKey.setImageDrawable(incognitoIcon)
            toolbarExpandKey.isVisible = true
        } else {
            toolbarExpandKey.setImageDrawable(toolbarArrowIcon)
            toolbarExpandKey.isVisible = toolbarIsExpandable
        }

        toolbarExpandKey.setOnClickListener(if (!toolbarIsExpandable) null else this)
        pinnedKeys.visibility = suggestionsStrip.visibility
        isExternalSuggestionVisible = false
    }

    private fun addKeyToPinnedKeys(pinnedKey: ToolbarKey) {
        val original = toolbar.findViewWithTag<ImageButton>(pinnedKey) ?: return
        // copy the original key to a new ImageButton
        val copy = ImageButton(context, null, R.attr.suggestionWordStyle)
        copy.tag = pinnedKey
        copy.scaleType = original.scaleType
        copy.scaleX = original.scaleX
        copy.scaleY = original.scaleY
        copy.contentDescription = original.contentDescription
        copy.setImageDrawable(original.drawable)
        // Its own params. Sharing the original's instance would hand the pinned row whatever width
        // the fit pass gave the carousel -- the pinned keys have their own space and do not scroll,
        // so they are never the ones that need squeezing -- and setupKey below would write the copy's
        // weight straight back into the carousel key.
        copy.layoutParams = newToolbarKeyParams()
        copy.isActivated = original.isActivated
        setupKey(copy, Settings.getValues().mColors)
        pinnedKeys.addView(copy)
    }

    /**
     * Narrows the carousel's keys when a small overflow can be absorbed, so nothing has to scroll,
     * and decides whether the carousel may scroll at all.
     *
     * The best affordance for hidden content is not having any. One key over the edge is the common
     * case -- somebody enabled a tenth tool -- and taking a few dp off each key makes the whole set
     * visible, which beats any amount of signalling that there is more.
     *
     * It stops at [MIN_KEY_WIDTH_FRACTION]. Past that the icons get too small to hit reliably, and
     * a scrolling toolbar with a permanent scrollbar is the better trade: the bar then says both
     * that there is more and how much, which is more than a fading edge could.
     *
     * All or nothing. Shrinking as far as the floor and still overflowing would leave keys that are
     * both cramped and scrolling, which is the worst of each.
     *
     * The keys get the viewport minus the toolbar's own padding, which the first version of this
     * forgot. ?attr/suggestionWordStyle used to put 6dp on each side of the toolbar, so a set the
     * fit pass had declared to fit still ran 12dp long, and the result was the worst possible bar:
     * permanent, a few pixels of travel, and nothing at either end of it. The layout zeroes that
     * padding now; the pass still subtracts whatever is actually there rather than trusting it to
     * stay zero. Whatever is left over decides whether scrolling is allowed at all, so a bar
     * appears only when dragging it actually brings a key into view.
     */
    private fun fitToolbarKeys() {
        val count = toolbar.childCount
        if (count == 0) return
        val viewport = toolbarContainer.width - toolbarContainer.paddingLeft - toolbarContainer.paddingRight
        val forKeys = viewport - toolbar.paddingLeft - toolbar.paddingRight
        if (forKeys <= 0) return

        val target = if (count * toolbarKeyWidth <= forKeys) toolbarKeyWidth
            else (forKeys / count).takeIf { it >= toolbarKeyWidth * MIN_KEY_WIDTH_FRACTION }
                ?: toolbarKeyWidth

        for (i in 0 until count) {
            val child = toolbar.getChildAt(i)
            val params = child.layoutParams
            if (params.width != target) {
                params.width = target
                child.layoutParams = params
            }
        }

        // so the weight of the toolbar keys actually does something: with a minimum the width of
        // the viewport, keys that fit are stretched across it instead of huddling at the left.
        if (toolbar.minimumWidth != viewport)
            toolbar.minimumWidth = viewport
        toolbarContainer.isScrollingEnabled = count * target > forKeys
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        view.setOnClickListener(this)
        view.setOnLongClickListener(this)
        (view.layoutParams as LinearLayout.LayoutParams).weight = 1f
        colors.setColor(view, ColorType.TOOL_BAR_KEY)
        colors.setBackground(view, ColorType.STRIP_BACKGROUND)
    }

    companion object {
        @JvmField
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private val TAG = SuggestionStripView::class.java.simpleName
        /** The box the glowing mark is rendered at; FIT_CENTER scales it to whatever the key is. */
        /**
         * How far a toolbar key may be squeezed before scrolling is the better answer.
         *
         * 0.75 of 36dp is 27dp. Below that the touch target is smaller than anyone can reliably hit
         * on a strip this short, and a key too small to press is worse than one you have to scroll to.
         */
        private const val MIN_KEY_WIDTH_FRACTION = 0.75f

        private const val VOICE_GLOW_BOX_DP = 40f
    }
}
