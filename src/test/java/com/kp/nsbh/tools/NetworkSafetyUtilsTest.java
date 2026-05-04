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
}
