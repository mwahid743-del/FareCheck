package com.farecheck

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

fun interface Measure { fun width(text: String, px: Float): Float }

/** One piece of text in a box row. px is final pixel size. */
data class Part(val text: String, val px: Float, val color: Int, val chip: Boolean = false)
data class Row(val parts: List<Part>)
data class Box(val rect: Bounds, val bg: Int, val rows: List<Row>)
data class Plan(val outline: Bounds?, val outlineColor: Int, val boxes: List<Box>)

/** Wanted text: size in dp-like units before shrinking. */
data class Spec(val text: String, val sp: Float, val color: Int, val chip: Boolean = false)

/**
 * Pure placement logic (no Android classes) so it can be tested off-device.
 * Boxes are sized to their content, placed from the offer card's text positions,
 * shrink (down to MIN_SP) when the gap is tight, and are left out when nothing fits.
 */
object Layout {
    const val MIN_SP = 12f
    const val PAD_X = 5f
    const val PAD_Y = 4f
    const val GAP = 10f
    const val PART_GAP = 5f
    const val CHIP_PAD = 5f
    const val ROW_H = 1.3f
    const val ROW_GAP = 2f
    const val MARGIN = 3f

    val WHITE = 0xFFFFFFFF.toInt()
    private val CHIP_GOOD = 0xFF7CFFA0.toInt()
    private val CHIP_BAD = 0xFFFF9A94.toInt()

    fun levelColor(l: Level, alpha: Int): Int {
        val rgb = when (l) {
            Level.ACCEPT -> 0x2E7D32
            Level.MAYBE -> 0xEF6C00
            Level.DECLINE -> 0xC62828
            Level.UNKNOWN -> 0x455A64
        }
        return (alpha shl 24) or rgb
    }

    // ---------- text position estimate ----------

    data class Extent(val endX: Int, val cy: Int)

    /** Where the text of a node really ends (nodes are often wider than their text) and its last row's centre. */
    fun extent(l: Line, m: Measure): Extent {
        val nodeW = max(1, l.right - l.left)
        val h = max(1, l.bottom - l.top)
        for (n in 1..5) {
            val px = h / (n * ROW_H)
            val w = m.width(l.text.trim(), px)
            // n == 1 is tolerant: nodes usually hug their text, and the estimate leans wide
            val limit = if (n == 1) nodeW * 1.3f else n * nodeW * 0.98f
            if (w <= limit || n == 5) {
                val last = if (n == 1) min(w, nodeW.toFloat())
                else (w - (n - 1) * nodeW).coerceIn(0f, nodeW.toFloat())
                val lineH = h / n
                return Extent(l.left + last.toInt(), l.bottom - lineH / 2)
            }
        }
        return Extent(l.right, (l.top + l.bottom) / 2)
    }

    // ---------- card rectangle ----------

    fun cardRect(lines: List<Line>, win: Bounds?, sw: Int, sh: Int, d: Float): Bounds? {
        val edge = (3 * d).toInt()
        val floating = win != null && win.h < sh * 0.92f && win.w > sw * 0.5f
        val r: Bounds? = if (floating) {
            val pad = (6 * d).toInt()
            Bounds(max(edge, win!!.l - pad), max(edge, win.t - pad), min(sw - edge, win.r + pad), min(sh - edge, win.b + pad))
        } else cardFromLines(lines, sw, sh, d)
        return if (r != null && r.w > 0 && r.h > 0) r else null
    }

    /** Full-screen app window: the card is the block from just above the price down to the drop-off address. */
    private fun cardFromLines(lines: List<Line>, sw: Int, sh: Int, d: Float): Bounds? {
        val ls = lines.filter { it.right > it.left && it.bottom > it.top }
        if (ls.isEmpty()) return null
        val edge = (3 * d).toInt()
        val price = OfferParser.priceLine(ls)
        val top: Int
        val bottom: Int
        if (price != null) {
            val reach = min(price.height * 2.2f, 90 * d)
            val near = ls.filter { it.top <= price.top && price.top - it.top <= reach }
            top = (near.minOfOrNull { it.top } ?: price.top) - (10 * d).toInt()
            val drop = OfferParser.dropLine(ls)
            bottom = (drop?.bottom ?: ls.maxOf { it.bottom }) + (22 * d).toInt()
        } else {
            top = ls.minOf { it.top } - (6 * d).toInt()
            bottom = ls.maxOf { it.bottom } + (6 * d).toInt()
        }
        val inside = ls.filter { it.top >= top && it.bottom <= bottom }
        val minLeft = inside.minOfOrNull { it.left } ?: ls.minOf { it.left }
        val l = max(edge, minLeft - (17 * d).toInt())
        return Bounds(l, max(edge, top), sw - l, min(sh - edge, bottom))
    }

    // ---------- box building ----------

    private fun partW(s: Spec, k: Float, d: Float, m: Measure): Float {
        val px = max(s.sp * k, MIN_SP) * d
        return m.width(s.text, px) + if (s.chip) 2 * CHIP_PAD * d else 0f
    }

    private fun rowSize(row: List<Spec>, k: Float, d: Float, m: Measure): Pair<Float, Float> {
        var w = 0f
        var h = 0f
        row.forEachIndexed { i, s ->
            if (i > 0) w += PART_GAP * d
            w += partW(s, k, d, m)
            h = max(h, max(s.sp * k, MIN_SP) * d * ROW_H)
        }
        return w to h
    }

    private fun boxSize(rows: List<List<Spec>>, k: Float, d: Float, m: Measure): Pair<Int, Int> {
        var w = 0f
        var h = 0f
        rows.forEachIndexed { i, r ->
            val (rw, rh) = rowSize(r, k, d, m)
            w = max(w, rw)
            h += rh + if (i > 0) ROW_GAP * d else 0f
        }
        return ceil(w + 2 * PAD_X * d).toInt() to ceil(h + 2 * PAD_Y * d).toInt()
    }

    /** Largest scale (<=1) at which all boxes laid side by side fit in avail px; null if none. */
    private fun fitScale(boxes: List<List<List<Spec>>>, avail: Int, d: Float, m: Measure): Float? {
        var k = 1f
        while (k >= 0.3f) {
            var total = 0
            boxes.forEachIndexed { i, b ->
                total += boxSize(b, k, d, m).first
                if (i > 0) total += (GAP * d).toInt() - 0
            }
            if (total <= avail) return k
            // stop once everything is already at the minimum size
            if (boxes.all { b -> b.all { r -> r.all { s -> s.sp * k <= MIN_SP } } }) return null
            k -= 0.04f
        }
        return null
    }

    private fun build(rows: List<List<Spec>>, k: Float, d: Float, m: Measure, bg: Int, left: Int, cy: Int, sh: Int): Box {
        val (w, h) = boxSize(rows, k, d, m)
        val top = (cy - h / 2).coerceIn(0, max(0, sh - h))
        val out = rows.map { r ->
            Row(r.map { s -> Part(s.text, max(s.sp * k, MIN_SP) * d, s.color, s.chip) })
        }
        return Box(Bounds(left, top, left + w, top + h), bg, out)
    }

    private fun chipSpecs(v: Verdict, sp: Float): List<Spec> =
        v.chips.map { Spec(it.text, sp, if (it.good) CHIP_GOOD else CHIP_BAD, true) }

    // ---------- the plan ----------

    fun plan(
        app: App, v: Verdict, lines: List<Line>, win: Bounds?,
        sw: Int, sh: Int, d: Float, m: Measure
    ): Plan? {
        val card = cardRect(lines, win, sw, sh, d) ?: return null
        val bg = levelColor(v.level, 0xEE)
        val red = levelColor(Level.DECLINE, 0xEE)
        val boxes = ArrayList<Box>()
        val right = card.r - (MARGIN * d).toInt()
        val gap = (GAP * d).toInt()

        when (app) {
            App.UBER, App.BOLT -> {
                val uber = app == App.UBER
                val infoSp = 27f // Uber and Bolt share the same sizes
                val chipSp = 13f
                val pcSp = if (uber) 30f else 24f
                // info box: right of the end of the price text (Bolt: the wrapped "pay)" row)
                val price = OfferParser.priceLine(lines)
                val anchor = if (uber) price else (OfferParser.padLine(lines) ?: price)
                if (anchor != null && v.info != null) {
                    val e = extent(anchor, m)
                    val rows = listOf(
                        listOf(Spec(v.info, infoSp, WHITE)),
                        chipSpecs(v, chipSp)
                    ).filter { it.isNotEmpty() }
                    boxes += placeFlex(rows, e.endX + gap, right, e.cy, card, bg, d, m, sh)
                }
                // postcode: top right, level with the pickup row (Uber) or the driver row (Bolt)
                val pcRow = if (uber) OfferParser.routeLine(lines)
                else (OfferParser.ratingLine(lines) ?: OfferParser.routeLine(lines))
                postcodeBox(lines, v, pcSp, bg, right, d, m, sh, pcRow)?.let { boxes += it }
            }

            App.DELIVEROO -> {
                val price = OfferParser.priceLine(lines)
                if (price != null) {
                    val e = extent(price, m)
                    val left = e.endX + gap
                    val a: List<List<Spec>>
                    val b: List<List<Spec>>?
                    if (v.age) {
                        a = listOf(listOf(Spec("AGE RESTRICTED", 27f, WHITE)))
                        b = listOf(listOf(Spec("DECLINE", 28f, WHITE)))
                    } else {
                        a = listOf(listOf(Spec(v.info ?: "~? mi", 27f, WHITE)))
                        b = v.postcode?.takeIf { it.isNotBlank() }?.let { listOf(listOf(Spec(it, 28f, WHITE))) }
                    }
                    val both = if (b != null) listOf(a, b) else listOf(a)
                    val k = fitScale(both, right - left, d, m)
                    if (k != null) {
                        var x = left
                        for (rows in both) {
                            val box = build(rows, k, d, m, bg, x, e.cy, sh)
                            boxes += box
                            x = box.rect.r + gap
                        }
                    } else {
                        // no room beside the price: right-align over the text instead
                        val k2 = fitScale(both, (card.w * 0.9f).toInt(), d, m) ?: 0.01f
                        val total = both.sumOf { boxSize(it, k2, d, m).first } + gap * (both.size - 1)
                        var x = max(card.l, right - total)
                        for (rows in both) {
                            val box = build(rows, k2, d, m, bg, x, e.cy, sh)
                            boxes += box
                            x = box.rect.r + gap
                        }
                    }
                }
            }

            App.JUST_EAT -> {
                val stat = OfferParser.statLine(lines)
                val e = stat?.let { extent(it, m) }
                    ?: OfferParser.dropLine(lines)?.let { extent(it, m) }
                if (e != null) {
                    if (v.age) {
                        val rows = listOf(
                            listOf(Spec("AGE RESTRICTED", 15f, WHITE)),
                            listOf(Spec("DECLINE", 21f, WHITE))
                        )
                        // safety warning: allowed to use the whole card width
                        val k = fitScale(listOf(rows), right - card.l - gap, d, m) ?: 0.01f
                        val (w, _) = boxSize(rows, k, d, m)
                        boxes += build(rows, k, d, m, red, right - w, e.cy, sh)
                    } else {
                        postcodeBox(lines, v, 28f, bg, right, d, m, sh, stat)?.let { boxes += it }
                    }
                }
            }
        }
        val outline = levelColor(v.level, 0xFF)
        return Plan(card, outline, boxes)
    }

    /** Largest size that fits the slot [left, right], right-aligned to the card edge; if nothing fits, overlap the text (never hide). */
    private fun placeFlex(
        rows: List<List<Spec>>, left: Int, right: Int, cy: Int,
        card: Bounds, bg: Int, d: Float, m: Measure, sh: Int
    ): Box {
        val k = fitScale(listOf(rows), right - left, d, m)
            ?: fitScale(listOf(rows), (card.w * 0.6f).toInt(), d, m) ?: 0.01f
        val (w, _) = boxSize(rows, k, d, m)
        return build(rows, k, d, m, bg, max(card.l, right - w), cy, sh)
    }

    /** Drop-off postcode district, right-aligned and level with the anchor row; always shown. */
    private fun postcodeBox(
        lines: List<Line>, v: Verdict, sp: Float, bg: Int, right: Int,
        d: Float, m: Measure, sh: Int, anchor: Line?
    ): Box? {
        val pc = v.postcode?.takeIf { it.isNotBlank() } ?: return null
        val row = anchor ?: OfferParser.dropLine(lines) ?: return null
        val e = extent(row, m)
        val rows = listOf(listOf(Spec(pc, sp, WHITE)))
        val (w, _) = boxSize(rows, 1f, d, m)
        return build(rows, 1f, d, m, bg, right - w, e.cy, sh)
    }
}
