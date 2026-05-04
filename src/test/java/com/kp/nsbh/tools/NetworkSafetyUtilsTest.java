package com.kp.nsbh.tools;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class NetworkSafetyUtilsTest {

    @Test
    void localhostIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NetworkSafetyUtils.validateResolvedAddresses("localhost"));
        assertTrue(ex.getMessage().contains("Private"));
    }

    @Test
    void loopbackIpv4IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void siteLocalIpv4IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("192.168.1.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void publicIpv4IsNotPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("8.8.8.8");
        assertTrue(!NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void loopbackIpv6IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("::1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void linkLocalIpv4IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("169.254.1.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void multicastIpv4IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("224.0.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void anyLocalIpv4IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("0.0.0.0");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void carrierGradeNatRangeIsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("100.64.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void benchmarkingRange198IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("198.18.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void ietfProtocolRange192IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("192.0.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void publicIpv6IsNotPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("2001:4860:4860::8888");
        assertTrue(!NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void uniqueLocalIpv6IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("fc00::1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void validateResolvedAddressesPassesForPublicHostByIp() {
        assertDoesNotThrow(() -> NetworkSafetyUtils.validateResolvedAddresses("8.8.8.8"));
    }

    @Test
    void classEIpv4IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("240.0.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void zeroFirstOctetIsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("0.0.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void benchmarkingRange198v19IsPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("198.19.0.1");
        assertTrue(NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void ip198NotInBenchmarkRangeIsNotPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("198.20.0.1");
        assertTrue(!NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void carrierGradeOutsideRange100dot128IsNotPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("100.128.0.1");
        assertTrue(!NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void ip192NotInIetfRangeIsNotPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("192.1.0.1");
        assertTrue(!NetworkSafetyUtils.isPrivateAddress(addr));
    }

    @Test
    void ip100BelowCarrierGradeRangeIsNotPrivate() throws Exception {
        InetAddress addr = InetAddress.getByName("100.63.0.1");
        assertTrue(!NetworkSafetyUtils.isPrivateAddress(addr));
    }
}
