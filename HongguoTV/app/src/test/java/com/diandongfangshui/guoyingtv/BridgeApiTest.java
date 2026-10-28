package com.diandongfangshui.guoyingtv;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BridgeApiTest {
    @Test public void localIpv4AddressesAccepted() {
        assertEquals("http://192.168.1.30:8787",
                BridgeApi.normalizeBase(" http://192.168.1.30:8787/ "));
        assertEquals("http://10.2.3.4", BridgeApi.normalizeBase("http://10.2.3.4"));
        assertEquals("https://172.16.0.10:443",
                BridgeApi.normalizeBase("https://172.16.0.10:443"));
        assertEquals("http://172.31.255.1",
                BridgeApi.normalizeBase("http://172.31.255.1"));
    }

    private void reject(String address) {
        try {
            BridgeApi.normalizeBase(address);
            fail("Must reject: " + address);
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test public void publicHostsAndHostnamesRejected() {
        reject("https://8.8.8.8");
        reject("http://127.0.0.1:8787");
        reject("http://172.32.1.1");
        reject("http://192.167.1.1");
        reject("http://example.org");
        reject("https://localhost:8787");
        reject("http://192.168.1.999");
        reject("http://192.168.1.1.evil.example");
    }

    @Test public void userinfoFragmentsAndPathsRejected() {
        reject("http://user:secret@192.168.1.5:8787");
        reject("http://192.168.1.5:8787/path");
        reject("http://192.168.1.5:8787/?x=1");
        reject("http://192.168.1.5:8787/#foo");
        reject("file:///etc/passwd");
        reject("");
    }

    @Test public void tokenHeaderInjectionRejected() {
        try {
            new BridgeApi("http://192.168.1.5:8787", "test\r\nX-Other: value");
            fail("Header injection cannot be accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("换行"));
        }
    }
}
