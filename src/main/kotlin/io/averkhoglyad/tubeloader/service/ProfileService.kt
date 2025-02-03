package io.averkhoglyad.tubeloader.service

import io.averkhoglyad.tubeloader.util.log4j
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class ProfileService(private val profileFile: Path,
                     private val userDir: Path) {

    private val log by log4j()

    init {
        require(userDir.isDirectory()) { "Parameter `userDir` must be a directory" }
        require(!profileFile.isDirectory()) { "Parameter `profileFile` must be a file" }

        Files.createDirectories(profileFile.parent)
    }

    fun getLastUploadDir(): Path {
        return profileFile.takeIf {  it.isRegularFile() }
            ?. let { readDetails() }
            ?.let { Path(it) }
            ?: userDir
    }

    fun saveLastUploadDir(dir: Path) {
        writeDetails(dir.toString())
    }

    private fun readDetails(): String? {
        try {
            return Files.readString(profileFile)
        } catch (e: IOException) {
            log.error("Error on profile details modification", e)
        }
        return null
    }

    private fun writeDetails(details: String) {
        try {
            Files.write(profileFile, details.toByteArray(), CREATE, TRUNCATE_EXISTING)
        } catch (e: IOException) {
            log.error("Error on profile details modification", e)
        }
    }
}