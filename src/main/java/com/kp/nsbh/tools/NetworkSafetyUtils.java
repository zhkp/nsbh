package com.kp.nsbh.tools;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

final class NetworkSafetyUtils {

    private NetworkSafetyUtils() {}

    static void validateResolvedAddresses(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            throw new IllegalArgumentException("Private host is not allowed");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateAddress(address)) {
                    throw new IllegalArgumentException("Private IP is not allowed: " + address.getHostAddress());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve host", e);
        }
    }

    static boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address ipv4) {
            byte[] b = ipv4.getAddress();
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            if (first == 100 && second >= 64 && second <= 127) return true;
            if (first == 198 && (second == 18 || second == 19)) return true;
            if (first == 192 && second == 0) return true;
            if (first >= 224) return true;
            return first == 0;
        }
        if (address instanceof Inet6Address ipv6) {
            byte[] b = ipv6.getAddress();
            return (b[0] & 0xFE) == 0xFC;
        }
        return false;
    }
}
