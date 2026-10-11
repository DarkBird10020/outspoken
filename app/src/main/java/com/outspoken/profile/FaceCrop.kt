package com.outspoken.profile

/** Where the face is in a picture, each side from 0 to 1 of the picture's width or height. */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A part of a picture, in pixels. */
data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

/** The profile's photo frame, 132 by 176 dp: width over height. */
const val PHOTO_FRAME_ASPECT = 132f / 176f

/**
 * The part of a [width] by [height] picture to keep as the profile photo: the [face] with room
 * around it for hair and chin, in the photo frame's shape, inside the picture. With no face, the
 * largest middle part of that shape.
 */
fun faceCrop(width: Int, height: Int, face: FaceBox?, aspect: Float = PHOTO_FRAME_ASPECT): Crop {
    val fullHeight = minOf(height.toFloat(), width / aspect)
    var cropHeight = if (face == null) fullHeight else ((face.bottom - face.top) * height * FACE_ROOM).coerceIn(1f, fullHeight)
    var cropWidth = cropHeight * aspect
    if (cropWidth > width) {
        cropWidth = width.toFloat()
        cropHeight = cropWidth / aspect
    }
    val centreX = face?.let { (it.left + it.right) / 2 * width } ?: (width / 2f)
    val centreY = face?.let { (it.top + it.bottom) / 2 * height } ?: (height / 2f)
    val w = cropWidth.toInt().coerceIn(1, width)
    val h = cropHeight.toInt().coerceIn(1, height)
    val left = (centreX - w / 2f).toInt().coerceIn(0, width - w)
    val top = (centreY - h / 2f).toInt().coerceIn(0, height - h)
    return Crop(left, top, w, h)
}

/** How much taller than the face the photo is: room for hair above and the chin and neck below. */
private const val FACE_ROOM = 1.8f
