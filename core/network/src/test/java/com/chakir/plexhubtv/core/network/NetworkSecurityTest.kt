package com.chakir.plexhubtv.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for network security configuration:
 * - Private IP address detection (used by TrustManager and HostnameVerifier)
 * - Ensures self-signed certificate acceptance is restricted to LAN only
 */
class NetworkSecurityTest {

    // ===========================================
    // isPrivateAddress() — Private IP detection
    // ===========================================

    @Test
    fun `private IPv4 ranges are detected correctly`() {
        // 10.0.0.0/8
        assertThat(NetworkModule.isPrivateAddress("10.0.0.1")).isTrue()
        assertThat(NetworkModule.isPrivateAddress("10.255.255.255")).isTrue()

        // 172.16.0.0/12
        assertThat(NetworkModule.isPrivateAddress("172.16.0.1")).isTrue()
        assertThat(NetworkModule.isPrivateAddress("172.31.255.255")).isTrue()

        // 192.168.0.0/16
        assertThat(NetworkModule.isPrivateAddress("192.168.0.1")).isTrue()
        assertThat(NetworkModule.isPrivateAddress("192.168.1.100")).isTrue()
        assertThat(NetworkModule.isPrivateAddress("192.168.255.255")).isTrue()
    }

    @Test
    fun `loopback addresses are detected as private`() {
        assertThat(NetworkModule.isPrivateAddress("127.0.0.1")).isTrue()
        assertThat(NetworkModule.isPrivateAddress("localhost")).isTrue()
        assertThat(NetworkModule.isPrivateAddress("::1")).isTrue()
    }

    @Test
    fun `public IPs are NOT detected as private`() {
        assertThat(NetworkModule.isPrivateAddress("8.8.8.8")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("1.1.1.1")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("203.0.113.1")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("104.26.0.1")).isFalse()
    }

    @Test
    fun `public domains are NOT detected as private`() {
        assertThat(NetworkModule.isPrivateAddress("plex.tv")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("api.themoviedb.org")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("www.omdbapi.com")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("google.com")).isFalse()
    }

    @Test
    fun `invalid hostnames return false`() {
        assertThat(NetworkModule.isPrivateAddress("not-a-valid-host-!!!")).isFalse()
    }

    @Test
    fun `empty string resolves to loopback and is treated as private`() {
        // InetAddress.getByName("") returns loopback on JVM
        assertThat(NetworkModule.isPrivateAddress("")).isTrue()
    }

    @Test
    fun `172_x addresses outside private range are not private`() {
        // 172.32.0.0 and above are public
        assertThat(NetworkModule.isPrivateAddress("172.32.0.1")).isFalse()
        assertThat(NetworkModule.isPrivateAddress("172.100.0.1")).isFalse()
    }
}
