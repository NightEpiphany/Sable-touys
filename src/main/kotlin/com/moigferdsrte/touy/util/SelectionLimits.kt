package com.moigferdsrte.touy.util

import kotlin.math.abs

/** Inclusive selection arithmetic, checked before allocating or visiting any blocks. */
object SelectionLimits {
    fun volume(
        firstX: Int, firstY: Int, firstZ: Int,
        secondX: Int, secondY: Int, secondZ: Int,
        limit: Long
    ): Long {
        if (limit < 1) return -1
        val width = abs(secondX.toLong() - firstX.toLong()) + 1
        val height = abs(secondY.toLong() - firstY.toLong()) + 1
        val length = abs(secondZ.toLong() - firstZ.toLong()) + 1
        if (width > limit || height > limit / width) return -1
        val area = width * height
        return if (length > limit / area) -1 else area * length
    }
}
