package com.studytrack.app.util

/**
 * The app's single level curve, shared by Home's banner and the Progress
 * screen so the two can never disagree: 500 XP per level, starting at
 * Level 1 with 0 XP. XP is the sum of points on completed tasks
 * (High 20 / Medium 10 / Low 5).
 */
object Levels {

    const val XP_PER_LEVEL = 500

    /** Scholar level for a total XP amount. */
    fun levelFor(points: Int): Int = (points / XP_PER_LEVEL) + 1

    /** XP earned inside the current level — what the progress bar fills to. */
    fun xpInLevel(points: Int): Int = points % XP_PER_LEVEL
}
