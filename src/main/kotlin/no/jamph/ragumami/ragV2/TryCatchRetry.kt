package no.jamph.ragumami.ragV2

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("TryCatchRetry")

suspend fun <T> tryCatchRetry(
    attempts: Int = 3,
    message: String,
    block: suspend () -> T?
): T {
    repeat(attempts) { i ->
        try {
            val result = block()
            if (result != null) return result
            logger.warn("$message - Attempt ${i + 1}/$attempts returned null")
        } catch (e: Exception) {
            logger.warn("$message - Attempt ${i + 1}/$attempts failed", e)
            if (i == attempts - 1) throw IllegalStateException("$message after $attempts attempts", e)
        }
    }
    throw IllegalStateException("$message after $attempts attempts")
}
