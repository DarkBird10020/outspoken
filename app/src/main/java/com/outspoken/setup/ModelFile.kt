package com.outspoken.setup

import java.io.File

/** The largest `.litertlm` file in [dir]; unfinished copies (`.part`) are skipped. */
fun findModelFile(dir: File?): File? =
    dir?.listFiles { file -> file.extension == "litertlm" }?.maxByOrNull { it.length() }
