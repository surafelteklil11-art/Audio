package com.surafel.audio

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.surafel.audio.checkers.*

class CheckersActivity : AppCompatActivity() {
    private lateinit var model: CheckersModel
    private lateinit var content: LinearLayout
    private var board: CheckersBoardView? = null
    private var status: TextView? = null
    private var clock: TextView? = null
    private var info: TextView? = null
    private var room: TextView? = null
    private var undo: TextView? = null
    private var hint: TextView? = null
    private var gameScreen = false
    private var renderedRevision = -1
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val brown = Color.rgb(66, 39, 23)
    private val cream = Color.rgb(250, 235, 198)
    private val ticker = object : Runnable {
        override fun run() {
            val seconds = model.elapsedSeconds
            clock?.text = "%02d:%02d".format(seconds / 60, seconds % 60)
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[CheckersModel::class.java]
        window.statusBarColor = Color.rgb(52, 29, 20); window.navigationBarColor = Color.rgb(52, 29, 20)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; background = CalendarSurface(true) }
        setContentView(root)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = column().apply { setPadding(dp(14), dp(12), dp(14), dp(20)) }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2)); root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        onBackPressedDispatcher.addCallback(this) { if (model.match != null) leaveGame() else finish() }
        renderHome()
    }
    override fun onStart() { super.onStart(); model.attach { render() }; handler.post(ticker) }
    override fun onStop() { handler.removeCallbacks(ticker); model.detach(); super.onStop() }
    override fun onDestroy() { dialog?.dismiss(); dialog = null; super.onDestroy() }
    private fun render() {
        if (model.match == null) { renderHome(); return }
        if (!gameScreen) buildGame()
        val game = model.match!!; val result = game.result
        val sameRevision = renderedRevision == model.revision
        val b = board!!
        b.rules = game.rules; b.position = game.position; b.flipped = model.mode != PlayMode.TWO_PLAYERS && model.mySide == -1
        b.design = model.design; b.tokenStyle = model.tokenStyle; b.showHints = model.hints
        b.inputEnabled = model.canMove; b.legal = CheckersEngine.legal(game.position, game.rules)
        b.hint = model.hintPath; b.last = model.lastMove?.path.orEmpty()
        if (!sameRevision) {
            b.resetSelection()
            if (renderedRevision >= 0 && model.lastMove != null && model.sound) b.playSoundEffect(SoundEffectConstants.CLICK)
        } else b.refresh()
        renderedRevision = model.revision
        status?.text = when {
            result == 0 -> "Draw · አቻ"
            result != null -> if (result == 1) "White wins · ነጭ አሸነፈ" else "Black wins · ጥቁር አሸነፈ"
            model.network && !model.connected -> model.notice
            model.waiting -> "Waiting for move confirmation…"
            model.thinking -> if (game.position.turn == model.human) "Finding a hint…" else "${model.difficulty.label} is thinking…"
            else -> (if (game.position.turn == 1) "White's turn · የነጭ ተራ" else "Black's turn · የጥቁር ተራ") +
                if (model.network) (if (model.canMove) " · Your move" else " · Your friend's move") else ""
        }
        info?.text = "${game.rules.name} · ${game.rules.size}×${game.rules.size} · " + when (model.mode) {
            PlayMode.SOLO -> "${model.difficulty.label} · You: ${if (model.human == 1) "White" else "Black"}"
            PlayMode.TWO_PLAYERS -> "2 players · One phone"
            else -> "Nearby · ${if (model.mySide == 1) "You: White" else "You: Black"}"
        }
        room?.apply {
            text = if (model.roomCode.isNotEmpty() && !model.connected) "Room code · ኮዱን ንካና copy አድርግ\n${model.roomCode}" else ""
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        undo?.apply { isEnabled = !model.network && result == null && game.history.size > (if (model.mode == PlayMode.SOLO && game.position.turn == model.human) 2 else 1); alpha = if (isEnabled) 1f else .45f }
        hint?.apply { isEnabled = !model.network && model.canMove; alpha = if (isEnabled) 1f else .45f }
    }
    private fun renderHome() {
        gameScreen = false; board = null; clock = null; renderedRevision = -1
        content.removeAllViews()
        content.addView(button("‹  Audio") { finish() }, spaced())
        content.addView(CheckersLogoView(this), LinearLayout.LayoutParams(-1, -2))
        content.addView(label("Checkers", 38f, true).apply {
            gravity = Gravity.CENTER; setTextColor(0xFFA8E0EA.toInt()); typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
            setPadding(0, 0, 0, dp(12))
        }, spaced())
        content.addView(button("PLAY · ከአፑ ጋር") { model.start(PlayMode.SOLO) }.apply {
            tag = "checkers-solo"; background = GradientDrawable().apply { setColor(0xFFCCDA75.toInt()); cornerRadius = dp(12).toFloat() }; textSize = 23f
        }, spaced())
        if (model.hasSaved) content.addView(button("Continue saved game · ቀጥል") { model.resumeSaved() }.apply { tag = "checkers-continue" }, spaced())
        content.addView(button("Rules: ${model.rules.name} · ${model.rules.size}×${model.rules.size}") { chooseRules() }.apply { tag = "checkers-rules" }, spaced())
        content.addView(button("Difficulty: ${model.difficulty.label}") { chooseDifficulty() }.apply { tag = "checkers-difficulty" }, spaced())
        content.addView(button("2 PLAYERS · በአንድ ስልክ") { model.start(PlayMode.TWO_PLAYERS) }.apply { tag = "checkers-local" }, spaced())
        content.addView(button("NEARBY · ከሌላ ስልክ ጋር") { nearby() }.apply {
            tag = "checkers-nearby"; background = GradientDrawable().apply { setColor(0xFF337D70.toInt()); cornerRadius = dp(12).toFloat() }; setTextColor(Color.WHITE)
        }, spaced())
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(button("Settings") { settings() }, weight())
        row.addView(button("Stats") { showText("Stats", model.stats()) }, weight())
        row.addView(button("Design") { designs() }, weight())
        content.addView(row, spaced())
        if (model.notice.isNotEmpty()) content.addView(cardText(model.notice), spaced())
        content.addView(cardText("Nearby: ሁለቱንም ስልኮች በአንድ Wi-Fi ወይም Hotspot አገናኝ። ኢንተርኔት አያስፈልግም።"), spaced())
    }
    private fun buildGame() {
        gameScreen = true; content.removeAllViews()
        val header = LinearLayout(this)
        header.addView(button("⌂ Home") { leaveGame() }, weight())
        clock = label("00:00", 21f, true).apply { gravity = Gravity.CENTER; setTextColor(cream) }
        header.addView(clock, weight())
        header.addView(button("↻ New") { confirm("Start a new game?", "The current match will end.") {
            if (model.network) { model.home(); nearby() } else model.start(model.mode)
        } }, weight())
        content.addView(header, spaced())
        status = cardText("").apply { tag = "checkers-status"; gravity = Gravity.CENTER; textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        content.addView(status, spaced())
        info = cardText("").apply { tag = "checkers-game-info"; textSize = 12f; gravity = Gravity.CENTER }
        content.addView(info, spaced())
        room = cardText("").apply { tag = "checkers-room-code"; isClickable = true; isFocusable = true; setOnClickListener {
            if (model.roomCode.isNotEmpty()) {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Checkers room", model.roomCode))
                Toast.makeText(this@CheckersActivity, "Room code copied", Toast.LENGTH_SHORT).show()
            }
        } }
        content.addView(room, spaced())
        board = CheckersBoardView(this).apply {
            tag = "checkers-board"; onMove = { model.play(it) }
            onStep = { status?.text = "Continue capturing · መብላቱን ቀጥል" }
        }
        content.addView(board, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val controls = LinearLayout(this)
        controls.addView(button("Rules") { tutorial() }, weight())
        hint = button("Hint") { model.requestHint() }.apply { tag = "checkers-hint" }; controls.addView(hint, weight())
        undo = button("Undo") { model.undo() }.apply { tag = "checkers-undo" }; controls.addView(undo, weight())
        controls.addView(button("Design") { designs() }, weight())
        content.addView(controls, spaced())
        content.addView(cardText("አንድ ድንጋይ ምረጥ፣ ከዚያ የሚሄድበትን ቦታ ንካ። ተከታታይ መብላት ሲኖር እያንዳንዱን ማረፊያ በተራ ንካ። K = King።"), spaced())
    }
    private fun leaveGame() {
        if (model.network) confirm("Leave nearby match?", "Your friend's connection will close.") { model.home() }
        else model.home()
    }
    private fun chooseDifficulty() = choose("Difficulty", Difficulty.entries.map { it.label }, model.difficulty.ordinal) {
        model.difficulty = Difficulty.entries[it]; model.saveOptions()
    }
    private fun chooseRules() {
        val body = column()
        Rules.presets.forEach { r -> body.addView(button("${if (model.rules == r) "● " else ""}${r.name} · ${r.size}×${r.size}") {
            model.rules = r; model.saveOptions(); dialog?.dismiss()
        }, spaced()) }
        body.addView(button("Customize rules…") { customRules() }, spaced())
        body.addView(label("Presets use their standard movement and capture patterns. Casual draw rule: three repetitions or 40 moves each without a capture or a man moving. Tournament endgame exceptions are not applied.", 13f), spaced())
        showPanel("Rules", body)
    }
    private fun customRules() {
        val r = model.rules
        val body = column()
        if (!r.orthogonal) body.addView(button("Board size: ${r.size}×${r.size}") {
            choose("Board size", listOf("8×8", "10×10"), if (r.size == 8) 0 else 1) {
                model.rules = r.copy(name = "Custom", size = if (it == 0) 8 else 10); model.saveOptions(); customRules()
            }
        }, spaced())
        body.addView(button("Kings: ${r.kings.name.replace('_', ' ')}") {
            choose("Kings", listOf("Flying kings", "Short kings", "Flying: land directly after capture"), r.kings.ordinal) {
                model.rules = r.copy(name = "Custom", kings = Kings.entries[it]); model.saveOptions(); customRules()
            }
        }, spaced())
        body.addView(button("Capture: ${r.capture.name}") {
            choose("Capture", listOf("Mandatory · Maximum pieces", "Mandatory · Any complete sequence", "Optional"), r.capture.ordinal) {
                model.rules = r.copy(name = "Custom", capture = Capture.entries[it]); model.saveOptions(); customRules()
            }
        }, spaced())
        if (!r.orthogonal) body.addView(toggle("Men can capture backwards", r.backwards) {
            model.rules = model.rules.copy(name = "Custom", backwards = it); model.saveOptions()
        }, spaced())
        body.addView(label("Promotion: ${r.crown.name}. Movement: ${if (r.orthogonal) "orthogonal" else "diagonal"}. Choose a preset to reset all rules.", 14f), spaced())
        showPanel("Custom rules", body)
    }
    private fun settings() {
        val body = column()
        body.addView(toggle("Play as Black against the app", model.human == -1) { model.human = if (it) -1 else 1; model.saveOptions() }, spaced())
        body.addView(toggle("Help · Highlight available moves", model.hints) { model.hints = it; model.saveOptions() }, spaced())
        body.addView(toggle("Sound · Use system touch sounds", model.sound) { model.sound = it; model.saveOptions() }, spaced())
        body.addView(button("Tutorial") { tutorial() }, spaced())
        body.addView(label("Music continues through Audio's player. All board and token designs are available.", 14f), spaced())
        showPanel("Settings", body)
    }
    private fun designs() {
        val body = column()
        CheckersBoardView.names.forEachIndexed { index, name ->
            body.addView(button("${if (model.design == index) "● " else ""}$name") {
                model.design = index; model.saveOptions(); designs()
            }, spaced())
        }
        body.addView(label("Tokens", 20f, true), spaced())
        listOf("Classic rings", "Spiral rings", "Single ring", "Octagon engraving").forEachIndexed { index, name ->
            body.addView(button("${if (model.tokenStyle == index) "● " else ""}$name") {
                model.tokenStyle = index; model.saveOptions(); designs()
            }, spaced())
        }
        showPanel("Design · All unlocked", body)
    }
    private fun nearby() {
        val body = column()
        body.addView(label("1. በአንዱ ስልክ Hotspot ክፈትና ሌላውን አገናኝ፤ ወይም ሁለቱም በአንድ Wi-Fi ይሁኑ።\n2. በሁለቱም ስልኮች ይህን Audio ስሪት ክፈት።\n3. አንዱ Create room፣ ሌላው Join room ይምረጥ።\n\nMobile data መጥፋት ይችላል። በእንግዳ Wi-Fi ላይ የስልኮች ግንኙነት ሊከለከል ይችላል፤ ካልተገናኘ Hotspot ተጠቀም።", 14f), spaced())
        body.addView(button("Wi-Fi / Hotspot Settings") {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "Open Wi-Fi / Hotspot from your phone's Settings", Toast.LENGTH_LONG).show() }
        }, spaced())
        body.addView(button("Create room · Host") {
            val addresses = LanAddress.localAddresses()
            if (addresses.isEmpty()) { Toast.makeText(this, "Connect Wi-Fi or enable Hotspot first", Toast.LENGTH_LONG).show() }
            else if (addresses.size == 1) { dialog?.dismiss(); model.host(addresses.first()) }
            else choose("Choose your Wi-Fi / Hotspot address", addresses, -1) { model.host(addresses[it]) }
        }.apply { tag = "checkers-host" }, spaced())
        body.addView(button("Join room") { joinRoom() }.apply { tag = "checkers-join" }, spaced())
        body.addView(label("Host plays White and chooses the rules. The guest plays Black. Keep Audio open on both phones. If disconnected, return home and create a new room. Undo and hints are disabled for nearby matches.", 13f), spaced())
        showPanel("Nearby · ያለኢንተርኔት", body)
    }
    private fun joinRoom() {
        val body = column()
        body.addView(label("Enter the room code shown on your friend's phone.", 15f), spaced())
        val input = EditText(this).apply {
            tag = "checkers-join-code"; hint = "192.168.43.1:47290/123456"; setSingleLine(); setTextColor(brown); setHintTextColor(0xFF756550.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(android.text.InputFilter.LengthFilter(40))
        }
        body.addView(input, spaced())
        body.addView(button("Connect") { if (model.join(input.text.toString())) dialog?.dismiss() else input.error = "Check the full room code" }, spaced())
        showPanel("Join room", body)
    }
    private fun tutorial() {
        val r = model.match?.rules ?: model.rules
        showText("${r.name} · Rules", "Move ${if (r.orthogonal) "forward or sideways" else "diagonally forward"}. Capture by jumping an opponent into an empty square.\n\n" +
            "Kings: ${r.kings.name.replace('_', ' ')}. Men ${if (r.backwards) "can" else "cannot"} capture backwards.\n\n" +
            "Capture: ${r.capture.name}. A capture sequence must be completed. ${if (r.capture == Capture.MAXIMUM) "Choose a sequence taking the most pieces." else ""}\n\n" +
            "Promotion: ${when (r.crown) { Crown.CONTINUE -> "Reaching the far row crowns immediately; continue capturing as a king."; Crown.STOP -> "Reaching the far row crowns and ends the turn."; Crown.END -> "A man becomes king only if it finishes the turn on the far row." }}\n\n" +
            "Win by capturing or blocking all opposing pieces. Casual draw: the same position three times, or 40 moves by each side without a capture or a man moving.\n\nTap a piece, then each landing square. Green squares show available moves. K marks a king. Undo reverses a full turn (both turns against the app).")
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        val body = column(); body.addView(label(message, 16f), spaced())
        body.addView(button("Continue") { dialog?.dismiss(); action() }, spaced())
        body.addView(button("Cancel") { dialog?.dismiss() }, spaced()); showPanel(title, body)
    }
    private fun choose(title: String, names: List<String>, selected: Int, action: (Int) -> Unit) {
        val body = column(); names.forEachIndexed { index, name -> body.addView(button("${if (index == selected) "● " else ""}$name") {
            dialog?.dismiss(); action(index)
        }, spaced()) }; showPanel(title, body)
    }
    private fun showText(title: String, text: String) { showPanel(title, column().apply { addView(label(text, 16f)) }) }
    private fun showPanel(title: String, body: View) {
        dialog?.dismiss()
        val root = column().apply { background = CalendarSurface(false); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title, 22f, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("×") { dialog?.dismiss() }.apply { contentDescription = "Close" }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(row, spaced())
        val scroll = object : ScrollView(this) {
            override fun onMeasure(width: Int, height: Int) {
                super.onMeasure(width, View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * .68).toInt(), View.MeasureSpec.AT_MOST))
            }
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2)); root.addView(scroll)
        dialog = AlertDialog.Builder(this).setView(root).create().also {
            it.show(); it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
    private fun toggle(text: String, checked: Boolean, action: (Boolean) -> Unit) = CheckBox(this).apply {
        this.text = text; isChecked = checked; textSize = 15f; setTextColor(brown); minHeight = dp(52)
        setOnCheckedChangeListener { _, value -> action(value) }
    }
    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(brown); if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun cardText(text: String) = label(text, 14f).apply { background = CalendarSurface(false); setPadding(dp(12), dp(10), dp(12), dp(10)) }
    private fun button(text: String, action: () -> Unit) = label(text, 16f, true).apply {
        gravity = Gravity.CENTER; background = CalendarSurface(false); minHeight = dp(52)
        setPadding(dp(10), dp(12), dp(10), dp(12)); isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
