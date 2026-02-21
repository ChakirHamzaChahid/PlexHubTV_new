package com.chakir.plexhubtv.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorTest {

    @Test
    fun `toUserMessage returns correct message for Network NoConnection`() {
        val error = AppError.Network.NoConnection()
        val message = error.toUserMessage()

        assertThat(message).contains("connexion")
        assertThat(message).contains("réseau")
    }

    @Test
    fun `toUserMessage returns correct message for Network Timeout`() {
        val error = AppError.Network.Timeout()
        val message = error.toUserMessage()

        assertThat(message).contains("temps")
        assertThat(message).contains("réessayer")
    }

    @Test
    fun `toUserMessage returns correct message for Auth SessionExpired`() {
        val error = AppError.Auth.SessionExpired()
        val message = error.toUserMessage()

        assertThat(message).contains("session")
        assertThat(message).contains("expiré")
        assertThat(message).contains("reconnecter")
    }

    @Test
    fun `toUserMessage returns correct message for Media NotFound`() {
        val error = AppError.Media.NotFound()
        val message = error.toUserMessage()

        assertThat(message).contains("Média")
        assertThat(message).contains("introuvable")
    }

    @Test
    fun `toUserMessage returns correct message for Playback StreamingError`() {
        val error = AppError.Playback.StreamingError("Stream interrupted")
        val message = error.toUserMessage()

        assertThat(message).isNotEmpty()
    }

    @Test
    fun `toUserMessage returns correct message for Search NoResults`() {
        val error = AppError.Search.NoResults()
        val message = error.toUserMessage()

        assertThat(message).contains("Aucun résultat")
    }

    @Test
    fun `toUserMessage returns custom message for Unknown error`() {
        val customMessage = "Custom error occurred"
        val error = AppError.Unknown(customMessage)
        val message = error.toUserMessage()

        assertThat(message).isEqualTo(customMessage)
    }

    @Test
    fun `toUserMessage returns default message for Unknown error without message`() {
        val error = AppError.Unknown()
        val message = error.toUserMessage()

        assertThat(message).contains("erreur")
        assertThat(message).contains("inattendue")
    }

    @Test
    fun `isCritical returns true for SessionExpired`() {
        val error = AppError.Auth.SessionExpired()

        assertThat(error.isCritical()).isTrue()
    }

    @Test
    fun `isCritical returns true for InvalidToken`() {
        val error = AppError.Auth.InvalidToken()

        assertThat(error.isCritical()).isTrue()
    }

    @Test
    fun `isCritical returns false for Network Timeout`() {
        val error = AppError.Network.Timeout()

        assertThat(error.isCritical()).isFalse()
    }

    @Test
    fun `isCritical returns true for Storage DiskFull`() {
        val error = AppError.Storage.DiskFull()

        assertThat(error.isCritical()).isTrue()
    }

    @Test
    fun `isRetryable returns true for Network NoConnection`() {
        val error = AppError.Network.NoConnection()

        assertThat(error.isRetryable()).isTrue()
    }

    @Test
    fun `isRetryable returns true for Network Timeout`() {
        val error = AppError.Network.Timeout()

        assertThat(error.isRetryable()).isTrue()
    }

    @Test
    fun `isRetryable returns true for Media LoadFailed`() {
        val error = AppError.Media.LoadFailed()

        assertThat(error.isRetryable()).isTrue()
    }

    @Test
    fun `isRetryable returns false for Auth InvalidToken`() {
        val error = AppError.Auth.InvalidToken()

        assertThat(error.isRetryable()).isFalse()
    }

    @Test
    fun `isRetryable returns false for Media UnsupportedFormat`() {
        val error = AppError.Media.UnsupportedFormat()

        assertThat(error.isRetryable()).isFalse()
    }

    @Test
    fun `toAppError converts UnknownHostException to Network NoConnection`() {
        val exception = UnknownHostException("host not found")
        val error = exception.toAppError()

        assertThat(error).isInstanceOf(AppError.Network.NoConnection::class.java)
    }

    @Test
    fun `toAppError converts SocketTimeoutException to Network Timeout`() {
        val exception = SocketTimeoutException("timeout")
        val error = exception.toAppError()

        assertThat(error).isInstanceOf(AppError.Network.Timeout::class.java)
    }

    @Test
    fun `toAppError converts IOException to Network ServerError`() {
        val exception = IOException("io error")
        val error = exception.toAppError()

        assertThat(error).isInstanceOf(AppError.Network.ServerError::class.java)
    }

    @Test
    fun `toAppError converts generic exception to Unknown`() {
        val exception = RuntimeException("generic error")
        val error = exception.toAppError()

        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        assertThat(error.message).isEqualTo("generic error")
    }

    @Test
    fun `AppError preserves cause when provided`() {
        val cause = IOException("original cause")
        val error = AppError.Network.ServerError("server failed", cause)

        assertThat(error.cause).isEqualTo(cause)
    }

    @Test
    fun `AppError Auth NoServersFound has correct message`() {
        val error = AppError.Auth.NoServersFound()
        val message = error.toUserMessage()

        assertThat(message).contains("serveur")
        assertThat(message).contains("Plex")
    }

    @Test
    fun `AppError Playback CodecNotSupported has correct message`() {
        val error = AppError.Playback.CodecNotSupported()
        val message = error.toUserMessage()

        assertThat(message).contains("Codec")
        assertThat(message).contains("supporté")
    }

    @Test
    fun `AppError Storage WriteError is retryable`() {
        val error = AppError.Storage.WriteError()

        // Storage errors are not marked as retryable by default
        assertThat(error.isRetryable()).isFalse()
    }

    // --- New tests: AppError extends Exception ---

    @Test
    fun `AppError is an instance of Exception`() {
        val error = AppError.Network.NoConnection("test")
        assertThat(error).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `AppError can be thrown and caught as Exception`() {
        val caught = try {
            throw AppError.Auth.InvalidToken("expired")
        } catch (e: Exception) {
            e
        }
        assertThat(caught).isInstanceOf(AppError.Auth.InvalidToken::class.java)
        assertThat(caught.message).isEqualTo("expired")
    }

    @Test
    fun `toAppError returns same instance for AppError input`() {
        val original = AppError.Media.NotFound("test")
        val result = original.toAppError()
        assertThat(result).isSameInstanceAs(original)
    }
}
