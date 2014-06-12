package org.apache.shindig.gadgets.parse.caja;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:lponce@redhat.com">Lucas Ponce</a>
 */
public class CajaCssUtilsTest extends Assert {

  @Test
  public void isIp6Test() throws Exception {
    assertNull(CajaCssUtils.extractIp6(null));
    assertNull(CajaCssUtils.extractIp6(""));
    assertNull(CajaCssUtils.extractIp6("http"));
    assertNull(CajaCssUtils.extractIp6("http"));
    assertNull(CajaCssUtils.extractIp6("http://test:port/"));
    assertNull(CajaCssUtils.extractIp6("http://test:port"));
    assertNull(CajaCssUtils.extractIp6("http://test:port/other"));
    assertNull(CajaCssUtils.extractIp6("https://test:port/"));
    assertNull(CajaCssUtils.extractIp6("https://test:port"));
    assertNull(CajaCssUtils.extractIp6("https://test:port/other"));
    assertEquals("[::1]", CajaCssUtils.extractIp6("http://[::1]"));
    assertEquals("[::1]", CajaCssUtils.extractIp6("http://[::1]/portal/gadgets"));
    assertEquals("2001:cdba:0000:0000:0000:0000:3257:9652", CajaCssUtils.extractIp6("http://2001:cdba:0000:0000:0000:0000:3257:9652/portal/gadgets"));
    assertEquals("[2001:cdba:0000:0000:0000:0000:3257:9652]", CajaCssUtils.extractIp6("http://[2001:cdba:0000:0000:0000:0000:3257:9652]:8080/portal/gadgets"));
  }

  @Test
  public void restoreIp6UrisTest() throws Exception {
    // Normal case
    String styleSheet = "ipv6i0f ipv6i2f bla bla bla bla bla bla ipv6i1f bla bla bla";
    List<String> ip6Uris = new ArrayList<String>();
    ip6Uris.add("change0");
    ip6Uris.add("change1");
    ip6Uris.add("change2");
    String expected = "change0 change2 bla bla bla bla bla bla change1 bla bla bla";

    assertEquals(expected, CajaCssUtils.restoreIp6Uris(styleSheet, ip6Uris));

    // Quasi similar pattern
    styleSheet = "ipv6i0f ipv6i2f bla bla bla bla bla bla ipv6i1af bla bla bla";
    expected = "change0 change2 bla bla bla bla bla bla ipv6i1af bla bla bla";

    assertEquals(expected, CajaCssUtils.restoreIp6Uris(styleSheet, ip6Uris));

    // Pattern at the end and incomplete
    styleSheet = "ipv6i0f ipv6i2f bla bla bla bla bla bla ipv6i122222";
    expected = "change0 change2 bla bla bla bla bla bla ipv6i122222";

    assertEquals(expected, CajaCssUtils.restoreIp6Uris(styleSheet, ip6Uris));
  }
}
