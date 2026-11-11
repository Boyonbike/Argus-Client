package dev.argus.client.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FreeProxySourceTest {
    private static final ProxyEntry.Type S5 = ProxyEntry.Type.SOCKS5;

    private static String row(String ip, int port, double uptime, String isp, double latency) {
        return "socks5," + ip + "," + port + ",Germany,DE,Berlin,elite,false," + uptime + ",AS1," + isp + "," + latency + ",now";
    }

    @Test void parsesRowsAndSkipsHeaderAndMalformedLines() {
        assertNull(FreeProxySource.parse("protocol,ip,port", S5));
        assertNull(FreeProxySource.parse("", S5));
        assertNull(FreeProxySource.parse("socks5,1.2.3.4,notaport,x,DE,x,x,x,90,x,Isp,100,now", S5));
        var c = FreeProxySource.parse(row("1.2.3.4", 1080, 90, "Deutsche Telekom", 120), S5);
        assertEquals("1.2.3.4:1080", c.label());
        assertEquals("DE", c.countryCode());
        assertTrue(c.residential());
    }

    @Test void ranksResidentialFirstAndFiltersWeakEntries() {
        var host = FreeProxySource.parse(row("1.1.1.1", 1, 99, "Hetzner Online", 50), S5);
        var home = FreeProxySource.parse(row("2.2.2.2", 2, 70, "Comcast", 900), S5);
        var lowUptime = FreeProxySource.parse(row("3.3.3.3", 3, 10, "Comcast", 100), S5);
        var slow = FreeProxySource.parse(row("4.4.4.4", 4, 90, "Comcast", 5000), S5);
        assertFalse(host.residential());
        var ranked = FreeProxySource.rank(List.of(host, home, lowUptime, slow), 10, 50, 3000, "");
        assertEquals(List.of(home, host), ranked);
        assertEquals(1, FreeProxySource.rank(List.of(host, home), 1, 50, 3000, "").size());
        assertTrue(FreeProxySource.rank(List.of(host, home), 10, 50, 3000, "US").isEmpty());
    }

    @Test void convertsToSavedEntry() {
        var entry = FreeProxySource.parse(row("5.5.5.5", 8080, 80, "Comcast", 100), S5).toEntry();
        assertEquals("5.5.5.5", entry.host());
        assertEquals(8080, entry.port());
        assertNull(FreeProxySource.parse(row("5.5.5.5", 99999, 80, "Comcast", 100), S5).toEntry());
    }
}
