package tn.iteam.util;

import java.net.InetAddress;
import java.net.Socket;

public final class NetworkUtils {

    private NetworkUtils() {}

    public static boolean isHostReachable(String ip, int timeoutMs) {
        try {
            return InetAddress.getByName(ip).isReachable(timeoutMs);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPortOpen(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new java.net.InetSocketAddress(ip, port),
                    timeoutMs
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}