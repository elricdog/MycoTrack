package com.mikotrack.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Paint
import android.graphics.Typeface
import com.mikotrack.R
import com.mikotrack.data.prefs.GameId
import kotlin.math.min
import kotlin.random.Random

/**
 * Mushroom solitaire (Klondike rules, touch only): the four suits are the mushroom
 * colours, warm (red cep, gold chanterelle) against cold (green bolete, purple
 * amatista). Tap a card to pick its face-up run up, tap a pile to drop it there;
 * tapping an empty foundation sends the best available card. Build each foundation
 * from the ace to the king of its kind to win.
 */
private const val WARM_1 = 0 // cep: red
private const val WARM_2 = 1 // chanterelle: gold
private const val COLD_1 = 2 // bolete: green
private const val COLD_2 = 3 // amatista: purple

private fun isWarm(suit: Int) = suit == WARM_1 || suit == WARM_2

private val FlashRed = Color(0xFFFF3B30)

private data class SCard(val suit: Int, val rank: Int)
private data class TabCard(val card: SCard, val up: Boolean)

/** Selection: pile kind (0 waste, 1 tableau), pile index, run start position. */
private data class Sel(val kind: Int, val pile: Int, val pos: Int)

private class SolitaireState {
    var score by mutableIntStateOf(0)
    var seconds by mutableIntStateOf(0)
    var gameOver by mutableStateOf(false)
    var stock by mutableStateOf(listOf<SCard>())
    var waste by mutableStateOf(listOf<SCard>())
    var foundations by mutableStateOf(List(4) { emptyList<SCard>() })
    var tableau by mutableStateOf(List(7) { emptyList<TabCard>() })
    var sel by mutableStateOf<Sel?>(null)
    /** Bumped on every rejected move; drives the red "nope" flash on the target pile. */
    var flash by mutableIntStateOf(0)
        private set
    /** 0 = stock/waste, 1 = tableau, 2 = foundation; -1 = no flash. */
    var flashKind by mutableIntStateOf(-1)
        private set
    var flashPile by mutableIntStateOf(-1)
        private set

    private fun rejectPile(kind: Int, pile: Int) {
        flashKind = kind
        flashPile = pile
        flash += 1
    }

    val cleared get() = foundations.sumOf { it.size }

    fun clearFlash() {
        flashKind = -1
        flashPile = -1
    }

    fun start() {
        val deck = (0 until 4).flatMap { suit -> (1..13).map { SCard(suit, it) } }.toMutableList()
        deck.shuffle(Random)
        val cols = ArrayList<List<TabCard>>(7)
        var taken = 0
        for (i in 0 until 7) {
            val col = (0..i).map { k ->
                val c = deck[taken++]
                TabCard(c, up = k == i)
            }
            cols += col
        }
        tableau = cols
        stock = deck.subList(taken, deck.size).reversed()
        waste = emptyList()
        foundations = List(4) { emptyList() }
        score = 0; seconds = 0; sel = null; gameOver = false
    }

    fun tickSecond() {
        if (!gameOver) seconds += 1
    }

    /** Draws one card from the stock to the waste; recycles when the stock runs out. */
    fun drawStock() {
        if (gameOver) return
        sel = null
        if (stock.isEmpty()) {
            if (waste.isEmpty()) return
            stock = waste.reversed()
            waste = emptyList()
            score = (score - 5).coerceAtLeast(0)
        } else {
            waste = waste + stock.last()
            stock = stock.dropLast(1)
        }
    }

    /** True when [run] is a valid Klondike sequence: descending ranks, warm/cold alternating. */
    private fun validRun(run: List<SCard>): Boolean =
        run.zipWithNext().all { (a, b) ->
            a.rank == b.rank + 1 && isWarm(a.suit) != isWarm(b.suit)
        }

    /** Foundation pile that accepts [card] right now, or -1. */
    private fun foundationFor(card: SCard): Int {
        val f = foundations[card.suit]
        return if (f.size == card.rank - 1) card.suit else -1
    }

    /** Tries to send [card] (removing it from [from]) to its foundation. */
    private fun sendToFoundation(card: SCard, from: Sel): Boolean {
        val f = foundationFor(card)
        if (f < 0) return false
        foundations = foundations.toMutableList().also { it[f] = it[f] + card }
        removeAt(from)
        score += 10
        afterRemove(from)
        checkWin()
        return true
    }

    private fun removeAt(s: Sel) {
        if (s.kind == 0) {
            waste = waste.dropLast(1)
        } else {
            val col = tableau[s.pile].toMutableList()
            repeat(col.size - s.pos) { col.removeAt(col.size - 1) }
            tableau = tableau.toMutableList().also { it[s.pile] = col }
        }
    }

    /** Flips the new top card of a tableau column after a removal. */
    private fun afterRemove(s: Sel) {
        if (s.kind != 1) return
        val col = tableau[s.pile]
        if (col.isNotEmpty() && !col.last().up) {
            val flipped = col.toMutableList().also { it[col.size - 1] = it.last().copy(up = true) }
            tableau = tableau.toMutableList().also { it[s.pile] = flipped }
            score += 5
        }
    }

    /** Can [run] (top-down list, first = bottom of the moving stack) land on [pile]? */
    private fun canLandOnTableau(run: List<SCard>, pile: Int): Boolean {
        val head = run.first()
        val col = tableau[pile]
        if (col.isEmpty()) return head.rank == 13
        val top = col.last().card
        val opposite = isWarm(top.suit) != isWarm(head.suit)
        return top.rank == head.rank + 1 && opposite
    }

    /** Tap on a tableau column: select the run, drop the current selection, or flip. */
    fun tapTableau(pile: Int, pos: Int) {
        if (gameOver) return
        val col = tableau[pile]
        if (col.isEmpty()) {
            if (sel != null) {
                if (!tryDropTableau(pile)) rejectPile(1, pile)
            }
            return
        }
        val idx = pos.coerceIn(0, col.size - 1)
        val current = sel
        if (current != null && current.kind == 1 && current.pile == pile) {
            sel = null
            return
        }
        if (current != null) {
            if (tryDropTableau(pile)) return
            rejectPile(1, pile)
        }
        val target = col[idx]
        if (!target.up) {
            // Only the top card can be flipped.
            if (idx == col.size - 1) {
                val flipped = col.toMutableList().also { it[idx] = it[idx].copy(up = true) }
                tableau = tableau.toMutableList().also { it[pile] = flipped }
                score += 5
            } else {
                rejectPile(1, pile)
            }
            return
        }
        val run = col.drop(idx).map { it.card }
        if (!validRun(run)) {
            rejectPile(1, pile)
            return
        }
        sel = Sel(1, pile, idx)
    }

    private fun selectedRun(): List<SCard>? {
        val s = sel ?: return null
        return if (s.kind == 0) waste.takeLast(1)
        else tableau[s.pile].drop(s.pos).map { it.card }
    }

    private fun tryDropTableau(pile: Int): Boolean {
        val s = sel ?: return false
        val run = selectedRun() ?: return false
        if (run.any { !isFaceUpInRun(it, s) }) return false
        if (!canLandOnTableau(run, pile)) return false
        val col = tableau[pile].toMutableList()
        col += run.map { TabCard(it, up = true) }
        tableau = tableau.toMutableList().also { it[pile] = col }
        removeAt(s)
        score += if (s.kind == 0) 5 else 2
        afterRemove(s)
        sel = null
        return true
    }

    private fun isFaceUpInRun(card: SCard, s: Sel): Boolean =
        s.kind == 0 || tableau[s.pile].drop(s.pos).any { it.card == card && it.up }

    /** Tap on the waste: select its top card (or drop the current selection there: illegal). */
    fun tapWaste() {
        if (gameOver) return
        if (sel != null) { sel = null; return }
        if (waste.isEmpty()) { drawStock(); return }
        sel = Sel(0, 0, waste.size - 1)
    }

    /** Tap on a foundation: drop the selection there, else auto-send the best card. */
    fun tapFoundation(pile: Int) {
        if (gameOver) return
        val s = sel
        if (s != null) {
            val run = selectedRun()
            if (run != null && run.size == 1) {
                if (sendToFoundation(run.first(), s)) {
                    sel = null
                    return
                }
            }
            rejectPile(2, pile)
            return
        }
        // Auto: top of the waste, then the top card of every tableau column.
        if (waste.isNotEmpty() && sendToFoundation(waste.last(), Sel(0, 0, waste.size - 1))) return
        for (p in 0 until 7) {
            val col = tableau[p]
            val top = col.lastOrNull() ?: continue
            if (top.up && sendToFoundation(top.card, Sel(1, p, col.size - 1))) return
        }
        rejectPile(2, pile)
    }

    /**
     * Double-tap: send the tapped card (waste top, tableau top, foundation top)
     * straight to its destination. Returns false if it cannot move.
     */
    fun doubleTap(kind: Int, pile: Int): Boolean {
        if (gameOver) return false
        return when (kind) {
            // Foundation top → back to the tableau (standard Klondike undo gesture).
            2 -> {
                val card = foundations.getOrNull(pile)?.lastOrNull() ?: return false
                val placed = tableau.map { it.toMutableList() }
                val target = placed.firstOrNull { col ->
                    col.isNotEmpty() &&
                        col.last().up &&
                        col.last().card.rank == card.rank + 1 &&
                        isWarm(col.last().card.suit) != isWarm(card.suit)
                } ?: placed.firstOrNull { col -> col.isEmpty() && card.rank == 13 }
                if (target == null) {
                    rejectPile(2, pile)
                    return false
                }
                target += TabCard(card, up = true)
                tableau = placed
                foundations = foundations.toMutableList().also { it[pile] = it[pile].dropLast(1) }
                sel = null
                true
            }
            else -> {
                val s: Sel
                val card: SCard
                if (kind == 0) {
                    val w = waste.lastOrNull() ?: return false
                    s = Sel(0, -1, waste.size - 1)
                    card = w
                } else {
                    val col = tableau.getOrNull(pile) ?: return false
                    val t = col.lastOrNull()?.takeIf { it.up } ?: return false
                    s = Sel(1, pile, col.size - 1)
                    card = t.card
                }
                if (sendToFoundation(card, s)) true
                else {
                    rejectPile(if (kind == 0) 0 else 1, pile)
                    false
                }
            }
        }
    }

    private fun checkWin() {
        if (foundations.all { it.size == 13 }) {
            score += 2000 + (3000 - seconds * 2).coerceAtLeast(0)
            gameOver = true
        }
    }

    fun restart() = start()
}

@Composable
fun SolitaireScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    viewModel.selectGame(GameId.SOLITAIRE)
    val ranking by viewModel.currentRanking.collectAsStateWithLifecycle()
    val state = remember { SolitaireState().apply { start() } }
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
    }

    LaunchedEffect(state.gameOver) {
        if (state.gameOver) viewModel.recordScore(GameId.SOLITAIRE, state.score, state.cleared / 13 + 1)
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            state.tickSecond()
        }
    }

    LaunchedEffect(state.flash) {
        if (state.flash > 0) {
            kotlinx.coroutines.delay(320)
            state.clearFlash()
        }
    }

    GameScaffold(
        title = stringResource(R.string.game_solitaire),
        score = state.score,
        level = state.cleared,
        lives = null,
        timer = state.seconds,
        hint = stringResource(R.string.game_solitaire_hint),
        gameOver = state.gameOver,
        ranking = ranking,
        onBack = onBack,
        onRestart = { state.restart() },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.gameOver) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val layout = solitaireLayout(size.width.toFloat(), size.height.toFloat())
                            val hit = layout.hit(offset.x, offset.y)
                            when {
                                hit.waste -> state.doubleTap(0, -1)
                                hit.foundation >= 0 -> state.doubleTap(2, hit.foundation)
                                hit.column >= 0 -> state.doubleTap(1, hit.column)
                            }
                        },
                        onTap = { offset ->
                            val layout = solitaireLayout(size.width.toFloat(), size.height.toFloat())
                            val hit = layout.hit(offset.x, offset.y)
                            when {
                                hit.stock -> state.drawStock()
                                hit.waste -> state.tapWaste()
                                hit.foundation >= 0 -> state.tapFoundation(hit.foundation)
                                hit.column >= 0 -> state.tapTableau(hit.column, hit.pos)
                            }
                        },
                    )
                },
        ) {
            drawRect(PixelArt.FOREST_DARK)
            val layout = solitaireLayout(size.width, size.height)
            val sprites = listOf(PixelArt.CEP, PixelArt.CHANTERELLE, PixelArt.GREEN_BOLETE, PixelArt.AMATISTA)
            val suitColors = listOf(
                android.graphics.Color.rgb(198, 40, 40),
                android.graphics.Color.rgb(255, 193, 7),
                android.graphics.Color.rgb(46, 125, 50),
                android.graphics.Color.rgb(171, 71, 188),
            )
            val rankLabel = { r: Int ->
                when (r) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> r.toString() }
            }

            fun cardOrigin(pileX: Float, pileY: Float) = Offset(pileX, pileY)

            fun drawCardBack(o: Offset) {
                drawRect(PixelArt.NIGHT_CELL, o, Size(layout.cardW, layout.cardH))
                drawRect(PixelArt.CREAM.copy(alpha = 0.5f), o, Size(layout.cardW, layout.cardH), style = Stroke(width = 2f))
                val sc = layout.cardW / 14f
                drawSprite(PixelArt.PUFFBALL_GHOST, o + Offset(layout.cardW / 2f - 4f * sc, layout.cardH / 2f - 4f * sc), sc)
            }

            fun drawCard(card: SCard, o: Offset, selected: Boolean) {
                drawRect(PixelArt.CREAM, o, Size(layout.cardW, layout.cardH))
                if (selected) {
                    drawRect(PixelArt.CAP_GOLD, o, Size(layout.cardW, layout.cardH), style = Stroke(width = 4f))
                } else {
                    drawRect(PixelArt.NIGHT.copy(alpha = 0.35f), o, Size(layout.cardW, layout.cardH), style = Stroke(width = 1f))
                }
                val sprite = sprites[card.suit]
                val sc = layout.cardW / 12f
                drawSprite(
                    sprite,
                    o + Offset(layout.cardW / 2f - 4f * sc, layout.cardH * 0.36f),
                    sc,
                )
                val paint = textPaint.apply { color = suitColors[card.suit] }
                drawContext.canvas.nativeCanvas.drawText(
                    rankLabel(card.rank),
                    o.x + layout.cardW * 0.12f,
                    o.y + layout.cardH * 0.22f,
                    paint.apply { textSize = layout.cardW * 0.42f },
                )
            }

            fun drawSlot(o: Offset, label: String?) {
                drawRect(PixelArt.NIGHT_CELL.copy(alpha = 0.6f), o, Size(layout.cardW, layout.cardH))
                drawRect(PixelArt.CREAM.copy(alpha = 0.25f), o, Size(layout.cardW, layout.cardH), style = Stroke(width = 2f))
                if (label != null) {
                    val paint = textPaint.apply {
                        color = android.graphics.Color.argb(120, 255, 248, 225)
                        textSize = layout.cardW * 0.5f
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        o.x + layout.cardW / 2f - layout.cardW * 0.18f,
                        o.y + layout.cardH * 0.62f,
                        paint,
                    )
                }
            }

            // Stock and waste.
            val stockO = cardOrigin(layout.margin, layout.top)
            if (state.stock.isEmpty()) drawSlot(stockO, "+") else drawCardBack(stockO)
            if (state.flashKind == 0 && state.flash > 0) {
                drawRect(FlashRed, stockO, Size(layout.cardW, layout.cardH), style = Stroke(width = 6f))
            }
            val wasteO = cardOrigin(layout.margin + layout.cardW + layout.gap, layout.top)
            val wasteTop = state.waste.lastOrNull()
            if (wasteTop == null) drawSlot(wasteO, null)
            else drawCard(wasteTop, wasteO, selected = state.sel?.kind == 0)
            if (state.flashKind == 0 && state.flash > 0) {
                drawRect(FlashRed, wasteO, Size(layout.cardW, layout.cardH), style = Stroke(width = 6f))
            }

            // Foundations on the right of the top row.
            for (f in 0 until 4) {
                val o = cardOrigin(
                    layout.margin + (2 + f) * (layout.cardW + layout.gap),
                    layout.top,
                )
                val top = state.foundations[f].lastOrNull()
                if (top == null) drawSlot(o, null) else drawCard(top, o, selected = false)
                if (state.flashKind == 2 && state.flashPile == f && state.flash > 0) {
                    drawRect(FlashRed, o, Size(layout.cardW, layout.cardH), style = Stroke(width = 6f))
                }
            }

            // Tableau with cascading face-up cards.
            for (p in 0 until 7) {
                val o = cardOrigin(layout.margin + p * (layout.cardW + layout.gap), layout.tabTop)
                val col = state.tableau[p]
                if (col.isEmpty()) {
                    drawSlot(o, null)
                    if (state.flashKind == 1 && state.flashPile == p && state.flash > 0) {
                        drawRect(FlashRed, o, Size(layout.cardW, layout.cardH), style = Stroke(width = 6f))
                    }
                    continue
                }
                col.forEachIndexed { i, tc ->
                    val cardO = o + Offset(0f, i * layout.step)
                    if (tc.up) {
                        val s = state.sel
                        val isSel = s != null && s.kind == 1 && s.pile == p && i >= s.pos
                        drawCard(tc.card, cardO, selected = isSel)
                    } else {
                        drawCardBack(cardO)
                    }
                }
            }
        }
    }
}

    /** Portrait layout for the table; shared by the draw and the tap hit-testing. */
    private class SolitaireLayout(val width: Float, val height: Float) {
        val margin = width * 0.012f
        val gap = width * 0.012f
        val cardW = (width - 2 * margin - 6 * gap) / 7f
        val cardH = cardW * 1.45f
        val top = height * 0.015f
        val tabTop = top + cardH + height * 0.035f
        /**
         * Cascade step between tableau cards: as tall a strip as the screen allows
         * (a column can grow to ~14 cards during play) so cards barely overlap and
         * taps land on the right card.
         */
        val step = min(cardH * 0.62f, (height - tabTop - cardH) / 13f)

    class Hit {
        var stock = false
        var waste = false
        var foundation = -1
        var column = -1
        var pos = 0
    }

    fun hit(x: Float, y: Float): Hit {
        val h = Hit()
        fun colOf(x: Float): Int {
            val c = ((x - margin) / (cardW + gap)).toInt()
            return if (c in 0 until 7) c else -1
        }
        if (y < top + cardH) {
            val c = colOf(x)
            when (c) {
                0 -> h.stock = true
                1 -> h.waste = true
                in 2..5 -> h.foundation = c - 2
            }
            return h
        }
        if (y >= tabTop) {
            val c = colOf(x)
            if (c >= 0) {
                h.column = c
                h.pos = ((y - tabTop) / step).toInt().coerceAtLeast(0)
            }
        }
        return h
    }
}

private fun solitaireLayout(width: Float, height: Float) = SolitaireLayout(width, height)
