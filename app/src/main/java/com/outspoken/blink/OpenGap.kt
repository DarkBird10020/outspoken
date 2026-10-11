package com.outspoken.blink

/**
 * How wide one eye's lids read open lately: the median lid gap of the last [size] frames with the
 * eyes clearly open (about 6 s at 25 fps). The median leaves out a glance up or down.
 */
class OpenGap(private val size: Int = 150) {

    private val gaps = ArrayDeque<Float>()

    fun add(gap: Float) {
        gaps.addLast(gap)
        if (gaps.size > size) gaps.removeFirst()
    }

    /** Null until half the frames are in, so a few frames do not move the lines. */
    fun median(): Float? = if (gaps.size < size / 2) null else gaps.sorted()[gaps.size / 2]
}
