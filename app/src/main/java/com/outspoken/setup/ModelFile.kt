package com.outspoken.setup

import java.io.File

/** The Gemma model is pushed by hand into the app's external files folder. */
fun findModelFile(dir: File?): File? =
    dir?.listFiles { file -> file.extension == "litertlm" }?.maxByOrNull { it.length() }
