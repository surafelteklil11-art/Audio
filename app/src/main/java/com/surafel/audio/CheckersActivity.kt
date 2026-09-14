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
    private var clock: TextView? = null
    private var info: TextView? = null
    private var room: TextView? = null
    private var undo: TextView? = null
    private var hint: TextView? = null
    private var gameScreen = false
    private var renderedRevision = -1
    private var renderedMatch: CheckersMatch? = null
    private var dialog: AlertDialog? = null
    private val gameMusic by lazy { CheckersMusic(this) }
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
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply { hide(androidx.core.view.WindowInsetsCompat.Type.statusBars()); systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; background = CheckersWood() }
        setContentView(root)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = column().apply { setPadding(dp(5), dp(12), dp(5), dp(12)) }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2)); root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        onBackPressedDispatcher.addCallback(this) { if (model.match != null) leaveGame() else finish() }
        renderHome()
    }
    override fun onStart() { super.onStart(); model.attach { render() }; handler.post(ticker); gameMusic.setEnabled(model.music) }
    override fun onStop() { gameMusic.setEnabled(false); handler.removeCallbacks(ticker); model.detach(); board?.stopMotion(); super.onStop() }
    override fun onDestroy() { dialog?.dismiss(); dialog = null; gameMusic.close(); super.onDestroy() }
    private fun render() {
        if (model.match == null) { renderHome(); return }
        if (!gameScreen) buildGame()
        val game = model.match!!; val result = game.result
        val sameRevision = renderedRevision == model.revision
        val b = board!!
        b.flipped = model.mode != PlayMode.TWO_PLAYERS && model.mySide == -1
        b.design = model.design; b.tokenStyle = model.tokenStyle; b.showHints = model.hints
        b.inputEnabled = model.canMove; b.legal = CheckersEngine.legal(game.position, game.rules)
        b.hint = model.hintPath; b.last = model.lastMove?.path.orEmpty()
        if (!sameRevision) {
            val move = if (renderedMatch === game) model.lastMove else null
            // Set before invoking the view, whose animation callbacks also render status.
            renderedRevision = model.revision; renderedMatch = game
            b.showPosition(game.rules, game.position, move)
        } else b.refresh()
        renderedRevision = model.revision
        info?.text = model.turnLabel
        info?.contentDescription = "${model.turnLabel}. ${game.rules.name}. ${model.difficulty.label}"
        room?.apply {
            text = if (model.roomCode.isNotEmpty() && !model.connected) "Tap to copy room code\n${model.roomCode}" else ""
            visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        undo?.apply { isEnabled = !model.network && result == null && game.history.size > (if (model.mode == PlayMode.SOLO && game.position.turn == model.human) 2 else 1); alpha = if (isEnabled) 1f else .45f }
        hint?.apply { isEnabled = !model.network && model.canMove && !b.isAnimating && b.selected.size < 2; alpha = if (isEnabled) 1f else .45f }
    }
    private fun play() {
        if (!model.hasUnfinishedSaved) { model.start(PlayMode.SOLO); return }
        val body = LinearLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        body.addView(button("No") { dialog?.dismiss(); model.start(PlayMode.SOLO) }.apply { tag = "checkers-resume-no" }, weight())
        body.addView(button("Yes") { dialog?.dismiss(); if (!model.resumeSaved()) model.start(PlayMode.SOLO) }.apply { tag = "checkers-resume-yes" }, weight())
        showPanel("Continue saved game?", body)
    }
    private fun renderHome() {
        gameScreen = false; board?.stopMotion(); board = null; clock = null; renderedRevision = -1; renderedMatch = null
        content.removeAllViews(); content.setPadding(dp(24), dp(12), dp(24), dp(12))
        content.addView(CheckersLogoView(this), LinearLayout.LayoutParams(-1, -2))
        content.addView(label("Checkers", 38f, true).apply {
            gravity = Gravity.CENTER; setTextColor(0xFFE8D7AD.toInt()); typeface = Typeface.create("serif", Typeface.BOLD_ITALIC)
            setPadding(0, 0, 0, dp(18))
        }, spaced())
        content.addView(button("PLAY") { play() }.apply {
            tag = "checkers-solo"; background = GradientDrawable().apply { setColor(0xFFCCDA75.toInt()); cornerRadius = dp(8).toFloat() }; textSize = 23f
        }, spaced())
        content.addView(button("Rules: ${model.rules.name}") { chooseRules() }.apply { tag = "checkers-rules" }, spaced())
        content.addView(button("Difficulty: ${model.difficulty.label}") { chooseDifficulty() }.apply { tag = "checkers-difficulty" }, spaced())
        content.addView(button("DAILY TOURNAMENT") { tournamentPanel() }.apply {
            tag = "checkers-tournament"; background = GradientDrawable().apply { setColor(0xFF337D70.toInt()); cornerRadius = dp(8).toFloat() }; setTextColor(Color.WHITE)
        }, spaced())
        if (model.notice.isNotEmpty()) content.addView(cardText(model.notice), spaced())
        content.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER; tag = "checkers-home-bottom" }
        fun item(name: String, tagName: String, task: () -> Unit) {
            bottom.addView(iconButton(name, task).apply {
                text = name; textSize = 11f; setTextColor(0xFFD6C6A4.toInt()); compoundDrawablePadding = dp(5); tag = tagName
            }, LinearLayout.LayoutParams(0, dp(76), 1f))
        }
        item("Settings", "checkers-settings") { settings() }
        item("Stats", "checkers-stats") { statsPanel() }
        item("Nearby", "checkers-nearby") { nearby() }
        item("2 Players", "checkers-local") { model.start(PlayMode.TWO_PLAYERS) }
        item("Design", "checkers-design") { designs() }
        content.addView(bottom, LinearLayout.LayoutParams(-1, dp(88)))
    }
    private fun buildGame() {
        gameScreen = true; content.removeAllViews(); content.setPadding(dp(5), dp(12), dp(5), dp(12))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(iconButton("Home") { leaveGame() }, LinearLayout.LayoutParams(dp(64), dp(56)))
        clock = label("00:00", 21f, true).apply { gravity = Gravity.CENTER; setTextColor(cream); typeface = Typeface.MONOSPACE }
        header.addView(clock, LinearLayout.LayoutParams(0, dp(40), 1f))
        header.addView(iconButton("New") { confirm("Start a new game?", "The current match will end.") {
            if (model.tournamentDay != null) tournamentPanel() else if (model.network) { model.home(); nearby() } else model.start(model.mode)
        } }, LinearLayout.LayoutParams(dp(64), dp(56)))
        content.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
        info = label("", 28f, true).apply {
            tag = "checkers-game-info"; gravity = Gravity.CENTER; setTextColor(cream)
            typeface = Typeface.create("serif", Typeface.BOLD_ITALIC); setSingleLine(); includeFontPadding = false
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        // Exact reserved height: only glyphs change on a turn, never board geometry.
        content.addView(info, LinearLayout.LayoutParams(-1, dp(48)))
        room = cardText("").apply { tag = "checkers-room-code"; setOnClickListener { if (model.roomCode.isNotEmpty()) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Checkers room", model.roomCode))
            Toast.makeText(this@CheckersActivity, "Room code copied", Toast.LENGTH_SHORT).show()
        } } }; content.addView(room, spaced())
        content.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        board = CheckersBoardView(this).apply {
            tag = "checkers-board"; onMove = { model.play(it) }; onStep = { render() }
            onMotionChanged = { if (model.sound && isAnimating) playSoundEffect(SoundEffectConstants.CLICK); render() }
        }
        content.addView(board, LinearLayout.LayoutParams(-1, -2))
        content.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(iconButton("Settings") { settings() }, LinearLayout.LayoutParams(0, dp(64), 1f))
        controls.addView(iconButton("Nearby") { nearby() }, LinearLayout.LayoutParams(0, dp(64), 1f))
        hint = iconButton("Hint") { model.requestHint() }.apply { tag = "checkers-hint" }; controls.addView(hint, LinearLayout.LayoutParams(0, dp(64), 1f))
        undo = iconButton("Undo") { model.undo() }.apply { tag = "checkers-undo" }; controls.addView(undo, LinearLayout.LayoutParams(0, dp(64), 1f))
        controls.addView(iconButton("Design") { designs() }, LinearLayout.LayoutParams(0, dp(64), 1f))
        content.addView(controls, LinearLayout.LayoutParams(-1, dp(76)))
        if (model.tournamentDay != null) content.addView(button("Tournament progress / Next round") { tournamentPanel() }, spaced())
    }
    private fun iconButton(name: String, action: () -> Unit) = label("", 12f).apply {
        contentDescription = name; isClickable = true; isFocusable = true; gravity = Gravity.CENTER
        val icon = CheckersIcon(name, if (name == "Delete") 0xFF39281A.toInt() else 0xFFD6C6A4.toInt()).apply { setBounds(0, 0, dp(34), dp(34)) }
        setCompoundDrawables(null, icon, null, null); setOnClickListener { action() }
    }
    private fun leaveGame() {
        if (model.network) confirm("Leave nearby match?", "Your friend's connection will close.") { model.home() }
        else model.home()
    }
    private fun chooseDifficulty() {
        val body = column()
        Difficulty.entries.reversed().forEach { level ->
            val item = column().apply { setPadding(dp(10), dp(10), dp(10), dp(10)); contentDescription = level.label; isClickable = true; isFocusable = true }
            item.addView(label(level.label, 22f, true))
            item.addView(label("★".repeat(level.stars) + if (model.difficulty == level) "    ◉" else "    ○", 24f).apply { setTextColor(0xFF8C6500.toInt()) })
            item.setOnClickListener { model.difficulty = level; model.saveOptions(); dialog?.dismiss() }
            body.addView(item, spaced())
        }
        showPanel("Difficulty", body)
    }
    private fun chooseRules() {
        val body = column()
        Rules.presets.forEach { r -> body.addView(button("${if (model.rules.name == r.name) "● " else ""}${r.name}") {
            model.rules = r.copy(size = model.boardSize); model.saveOptions(); dialog?.dismiss()
        }, spaced()) }
        body.addView(button("Customize rules…") { customRules() }, spaced())
        body.addView(label("Presets use their standard movement and capture patterns. Casual draw rule: three repetitions or 40 moves each without a capture or a man moving. Tournament endgame exceptions are not applied.", 13f), spaced())
        showPanel("Rules", body)
    }
    private fun customRules() {
        val r = model.rules
        val body = column()
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
    private fun settingsChoice(title: String, choices: List<String>, selected: Int, apply: (Int) -> Unit) {
        val body = column()
        choices.forEachIndexed { index, name ->
            val option = RadioButton(this).apply {
                text = name; textSize = 23f; typeface = Typeface.DEFAULT_BOLD; setTextColor(brown)
                buttonTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF92AF3B.toInt(), 0xFF8B826D.toInt()))
                isChecked = index == selected; minHeight = dp(64); setPadding(dp(12), dp(10), dp(12), dp(10)); compoundDrawablePadding = dp(12)
                setOnClickListener { apply(index); model.saveOptions(); settings() }
            }
            body.addView(option, LinearLayout.LayoutParams(-1, dp(68)))
        }
        showPanel(title, body)
    }
    private fun settings() {
        val body = column()
        fun setting(title: String, subtitle: String = "", checked: Boolean? = null, change: ((Boolean) -> Unit)? = null, task: (() -> Unit)? = null) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); minimumHeight = dp(76) }
            val text = column(); text.addView(label(title, 23f, true))
            if (subtitle.isNotEmpty()) text.addView(label(subtitle, 15f).apply { setTextColor(0xFF8B826D.toInt()) })
            row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            if (checked != null) row.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                contentDescription = title; isChecked = checked; minWidth = dp(52)
                thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFFE7EDB7.toInt(), 0xFFC5C3B7.toInt()))
                trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(0xFF92AD38.toInt(), 0xFF8D8878.toInt()))
                setOnCheckedChangeListener { _, value -> change?.invoke(value); model.saveOptions() }
            }, LinearLayout.LayoutParams(dp(58), dp(48)))
            if (task != null) { row.isClickable = true; row.isFocusable = true; row.contentDescription = title; row.setOnClickListener { task() } }
            body.addView(row, LinearLayout.LayoutParams(-1, dp(78)))
        }
        setting("Board Size", "${model.boardSize}x${model.boardSize}", task = { settingsChoice("Board Size", listOf("6x6", "8x8", "10x10"), listOf(6, 8, 10).indexOf(model.boardSize)) { model.boardSize = listOf(6, 8, 10)[it]; model.rules = model.rules.copy(size = model.boardSize) } })
        setting("Play as", when (model.playAs) { 1 -> "White"; -1 -> "Black"; else -> "Random" }, task = { settingsChoice("Play as", listOf("White", "Black", "Random"), listOf(1, -1, 0).indexOf(model.playAs)) { model.playAs = listOf(1, -1, 0)[it] } })
        setting("Help", "Highlight available moves", model.hints, { model.hints = it })
        setting("Sound", checked = model.sound, change = { model.sound = it })
        setting("Music", checked = model.music, change = { model.music = it; gameMusic.setEnabled(it) })
        setting("Tutorial", "Learn the basics", task = { tutorial() })
        showPanel("Settings", body)
    }
    private fun designs() {
        val body = column()
        body.addView(label("${model.progress.stars} ★", 42f, true).apply {
            gravity = Gravity.CENTER
            text = android.text.SpannableString(text).apply { setSpan(android.text.style.ForegroundColorSpan(0xFFFFC72C.toInt()), length - 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }, spaced())
        body.addView(label("Collect stars by winning games", 16f).apply { gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif", Typeface.ITALIC) }, spaced())
        fun grid(title: String, names: List<String>, selected: Int, token: Boolean) {
            body.addView(label(title, 23f, true).apply { gravity = Gravity.CENTER }, spaced())
            names.chunked(4).forEachIndexed { rowIndex, chunk ->
                val line = LinearLayout(this)
                chunk.forEachIndexed { columnIndex, name ->
                    val index = rowIndex * 4 + columnIndex
                    val cell = column().apply { gravity = Gravity.CENTER; setPadding(dp(3), dp(3), dp(3), dp(3)); contentDescription = name; isClickable = true; isFocusable = true
                        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); if (index == selected) setStroke(dp(2), 0xFF91E6A7.toInt()) }
                        setOnClickListener { if (token) model.tokenStyle = index else model.design = index; model.saveOptions(); designs() }
                    }
                    cell.addView(CheckersPreview(this, index.coerceAtMost(7), if (token) index else null), LinearLayout.LayoutParams(-1, dp(68)))
                    androidx.appcompat.widget.TooltipCompat.setTooltipText(cell, name)
                    line.addView(cell, LinearLayout.LayoutParams(0, dp(74), 1f))
                }; body.addView(line, spaced())
            }
        }
        grid("Board", CheckersBoardView.names, model.design, false)
        grid("Tokens", listOf("Classic rings", "Spiral rings", "Single ring", "Octagon", "Recessed", "Smooth", "Flower", "Sunburst"), model.tokenStyle, true)
        showPanel("You have", body)
    }
    private fun statsPanel() {
        val body = column()
        fun section(title: String, group: String, headings: List<String>, rows: List<List<String>>) {
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(label(title, 24f, true), LinearLayout.LayoutParams(0, dp(56), 1f))
            header.addView(iconButton("Delete") { confirm("Reset $title statistics?", "Your stars and daily tournament entry will be kept.") { model.progress.reset(group); statsPanel() } }, LinearLayout.LayoutParams(dp(44), dp(48)))
            body.addView(header, spaced())
            val table = TableLayout(this).apply { isStretchAllColumns = true }
            (listOf(headings) + rows).forEachIndexed { index, values ->
                val row = TableRow(this)
                values.forEach { value -> row.addView(label(value, 17f, index == 0).apply {
                    gravity = Gravity.CENTER; setPadding(dp(5), dp(8), dp(5), dp(8))
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(1), brown) }
                }, TableRow.LayoutParams(0, dp(42), 1f)) }; table.addView(row)
            }; body.addView(table, spaced())
        }
        section("Single Player", "solo", listOf("", "WIN", "LOSE", "DRAW"), Difficulty.entries.reversed().map { d -> listOf(d.opponent) + listOf("win", "loss", "draw").map { model.progress.count("solo_${d.name}_$it").toString() } })
        section("Tournament", "tournament", listOf("", "WIN", "2ND", "PLAYED"), listOf(listOf("You") + listOf("win", "second", "played").map { model.progress.count("tournament_$it").toString() }))
        section("Nearby", "nearby", listOf("", "WIN", "LOSE", "DRAW"), listOf(listOf("You") + listOf("win", "loss", "draw").map { model.progress.count("nearby_$it").toString() }))
        showPanel("Stats", body)
    }
    private fun tournamentPanel() {
        val body = column(); val daily = model.progress.daily()?.takeIf { it.day == model.progress.today() }
        body.addView(label(model.progress.today(), 16f, true), spaced())
        body.addView(label("Daily local tournament · 5 rounds\nBeat Ben, Joe, Sophia, Lisa and Alpha in order. Each win earns 1–5 stars. A draw replays the round; a loss ends today's entry. Reach the final for second place, or win it for the trophy.", 15f), spaced())
        Difficulty.entries.forEachIndexed { index, d -> body.addView(label("${if (daily != null && (index < daily.round || daily.status == "won")) "✓" else "${index + 1}."} ${d.label}   ${"★".repeat(d.stars)}", 17f, true), spaced()) }
        val finished = daily != null && daily.status != "playing"
        body.addView(label(when { daily?.status == "won" -> "Tournament champion!"; finished -> "Today's tournament is complete. Come back tomorrow."; daily == null -> "One entry each day. Progress is saved on this phone."; else -> "Round ${daily.round + 1} of 5" }, 16f, true), spaced())
        if (!finished) body.addView(button(if (daily == null) "Enter tournament" else "Continue tournament") { dialog?.dismiss(); model.startDaily() }, spaced())
        showPanel("Daily Tournament", body)
    }
    private fun nearby() {
        val body = column()
        body.addView(label("1. Connect both phones to the same Wi-Fi or phone hotspot.\n2. Open this Audio version on both phones.\n3. One player chooses Create room; the other chooses Join room.\n\nMobile data is not required. If a guest Wi-Fi blocks the connection, use a phone hotspot.", 14f), spaced())
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
        showPanel("Nearby · Offline", body)
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
        val root = column().apply { background = CheckersPaper(); setPadding(dp(16), dp(14), dp(16), dp(14)) }
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
    private fun cardText(text: String) = label(text, 14f).apply { background = CheckersPaper(); setPadding(dp(12), dp(10), dp(12), dp(10)) }
    private fun button(text: String, action: () -> Unit) = label(text, 16f, true).apply {
        gravity = Gravity.CENTER; background = CheckersPaper(); minHeight = dp(52)
        setPadding(dp(10), dp(12), dp(10), dp(12)); isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    private fun weight() = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
