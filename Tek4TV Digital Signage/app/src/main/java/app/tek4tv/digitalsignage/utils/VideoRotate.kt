package app.tek4tv.digitalsignage.utils

/*
import java.io.*
import java.nio.file.Paths

object VideoRotate {
    private val orientations: List<ByteArray> = listOf(
        //no rotation
        byteArrayOfInts(
            0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 64),
        //180
        byteArrayOfInts(
            255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 64),
        //90
        byteArrayOfInts(
            0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 64),
        //270
        byteArrayOfInts(
            0, 0, 0, 0, 255, 255, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 64))

    fun rotateVideo(path : String, orientation : Int)
    {
        val inputStream = BufferedInputStream(FileInputStream(path))
        val outputStream = DataOutputStream(BufferedOutputStream(FileOutputStream(path)))
        val fileBytes = inputStream.readBytes()
        inputStream.close()

        val videPos = KMPMatch.indexOf(fileBytes, "vide".toByteArray())

        if(videPos == -1) return

        val file = RandomAccessFile(path,"rw")
        val fileBytes = ByteArray(file.length())

    }
}

fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }

internal object KMPMatch {
    */
/**
 * Finds the first occurrence of the pattern in the text.
 *//*

    fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        if (data.size == 0) return -1
        val failure = computeFailure(pattern)
        var j = 0
        for (i in data.indices) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1]
            }
            if (pattern[j] == data[i]) {
                j++
            }
            if (j == pattern.size) {
                return i - pattern.size + 1
            }
        }
        return -1
    }

    */
/**
 * Computes the failure function using a boot-strapping process,
 * where the pattern is matched against itself.
 *//*

    private fun computeFailure(pattern: ByteArray): IntArray {
        val failure = IntArray(pattern.size)
        var j = 0
        for (i in 1 until pattern.size) {
            while (j > 0 && pattern[j] != pattern[i]) {
                j = failure[j - 1]
            }
            if (pattern[j] == pattern[i]) {
                j++
            }
            failure[i] = j
        }
        return failure
    }
}*/
