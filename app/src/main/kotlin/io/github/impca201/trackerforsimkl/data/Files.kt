package io.github.impca201.trackerforsimkl.data

import java.io.File

/** Writes via a temporary file and a rename, so a crash or a concurrent write can't leave a truncated cache. */
internal fun writeAtomically(
    file: File,
    text: String,
) {
    synchronized(file.canonicalPath.intern()) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
