package com.chakir.plexhubtv.core.network

import android.os.Build
import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    @Before
    fun setUp() {
        // Use Unsafe to set final static fields from Android stub jar (Java 17+)
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
        val putObject = unsafeClass.getMethod("putObject", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)

        val releaseField = Build.VERSION::class.java.getField("RELEASE")
        val releaseOffset = staticFieldOffset.invoke(unsafe, releaseField) as Long
        putObject.invoke(unsafe, Build.VERSION::class.java, releaseOffset, "14")

        val modelField = Build::class.java.getField("MODEL")
        val modelOffset = staticFieldOffset.invoke(unsafe, modelField) as Long
        putObject.invoke(unsafe, Build::class.java, modelOffset, "Test Device")
    }

    @Test
    fun `intercept emits TokenInvalid event on 401 response`() = runTest {
        val mockEventBus = mockk<AuthEventBus>(relaxed = true)
        val mockDataStore = mockk<SettingsDataStore>(relaxed = true)
        val scope = CoroutineScope(SupervisorJob())

        val interceptor = AuthInterceptor(mockDataStore, scope, mockEventBus)

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://test").build()
            override fun proceed(request: Request): Response = Response.Builder()
                .code(401)
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("Unauthorized")
                .build()
            override fun connection() = null
            override fun call(): Call = mockk()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(mockChain)

        verify(exactly = 1) { mockEventBus.emitTokenInvalid() }
    }

    @Test
    fun `intercept does not emit event on 200 response`() = runTest {
        val mockEventBus = mockk<AuthEventBus>(relaxed = true)
        val mockDataStore = mockk<SettingsDataStore>(relaxed = true)
        val scope = CoroutineScope(SupervisorJob())

        val interceptor = AuthInterceptor(mockDataStore, scope, mockEventBus)

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://test").build()
            override fun proceed(request: Request): Response = Response.Builder()
                .code(200)
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("OK")
                .build()
            override fun connection() = null
            override fun call(): Call = mockk()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(mockChain)

        verify(exactly = 0) { mockEventBus.emitTokenInvalid() }
    }
}
