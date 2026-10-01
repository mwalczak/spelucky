package io.github.mwalczak.spelucky.game

/**
 * Whether a newer build of the game is on GitHub, and how installing it is going.
 * Written by the updater, read by the renderer; every field is replaced as a whole.
 */
class UpdateState {
    /** Build number of the running game (0 for local builds, which never update). */
    @Volatile var currentBuild = 0
    /** Build number of the newest release, or 0 if unknown. */
    @Volatile var latestBuild = 0
    /** 0..1 while downloading, otherwise -1. */
    @Volatile var progress = -1f
    /** Shown instead of the usual "tap to update" text, e.g. after an error. */
    @Volatile var message: String? = null

    val available get() = currentBuild > 0 && latestBuild > currentBuild
    val busy get() = progress >= 0f

    companion object {
        /** "build-12" -> 12, anything else -> 0. */
        fun buildFromTag(tag: String): Int =
            tag.removePrefix("build-").takeIf { tag.startsWith("build-") }?.toIntOrNull() ?: 0
    }
}
