package top.nkbe.npatch.patch.wrapper;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

public class WrapperNetworkSecurityTest {
    @Test public void rewritesBaseAndNestedDomainsWithoutChangingTlsRules() throws Exception {
        byte[] original = config(true);
        for (boolean allowed : new boolean[] {true, false}) {
            byte[] rewritten = WrapperNetworkSecurity.rewrite(original, allowed);
            List<String> attrs = attributes(rewritten);
            assertEquals(3, attrs.stream().filter(s -> s.equals("cleartextTrafficPermitted=" + allowed)).count());
            assertEquals(0, attrs.stream().filter(s -> s.equals("cleartextTrafficPermitted=" + !allowed)).count());
            assertTrue(attrs.contains("certificates:src=system"));
            assertTrue(attrs.contains("pin-set:expiration=2030-01-01"));
            assertTrue(attrs.contains("domain:includeSubdomains=true"));
            assertEquals(2, attributes(original).stream().filter(s -> s.equals("cleartextTrafficPermitted=false")).count());
        }
    }

    @Test public void addsBasePolicyWhenOnlyDomainConfigsExist() throws Exception {
        List<String> attrs = attributes(WrapperNetworkSecurity.rewrite(config(false), true));
        assertEquals(3, attrs.stream().filter(s -> s.equals("cleartextTrafficPermitted=true")).count());
        assertTrue(attrs.contains("certificates:src=system"));
    }

    @Test public void ignoresOtherXmlResources() throws Exception {
        AxmlWriter writer = new AxmlWriter();
        writer.child(null, "preferences").end();
        assertNull(WrapperNetworkSecurity.rewrite(writer.toByteArray(), true));
        assertNull(WrapperNetworkSecurity.rewrite("<network-security-config/>".getBytes(), true));
    }

    private static byte[] config(boolean base) throws IOException {
        AxmlWriter writer = new AxmlWriter();
        NodeVisitor root = writer.child(null, "network-security-config");
        if (base) {
            NodeVisitor baseConfig = root.child(null, "base-config");
            baseConfig.attr(null, "cleartextTrafficPermitted", -1, NodeVisitor.TYPE_INT_BOOLEAN, false);
            NodeVisitor anchors = baseConfig.child(null, "trust-anchors");
            NodeVisitor certificates = anchors.child(null, "certificates");
            certificates.attr(null, "src", -1, NodeVisitor.TYPE_STRING, "system");
            certificates.end();
            anchors.end(); baseConfig.end();
        } else {
            NodeVisitor anchors = root.child(null, "debug-overrides");
            NodeVisitor certificates = anchors.child(null, "certificates");
            certificates.attr(null, "src", -1, NodeVisitor.TYPE_STRING, "system");
            certificates.end();
            anchors.end();
        }
        NodeVisitor domain = root.child(null, "domain-config");
        domain.attr(null, "cleartextTrafficPermitted", -1, NodeVisitor.TYPE_INT_BOOLEAN, false);
        NodeVisitor name = domain.child(null, "domain");
        name.attr(null, "includeSubdomains", -1, NodeVisitor.TYPE_INT_BOOLEAN, true);
        name.text(0, "example.com"); name.end();
        NodeVisitor pinSet = domain.child(null, "pin-set");
        pinSet.attr(null, "expiration", -1, NodeVisitor.TYPE_STRING, "2030-01-01");
        pinSet.end();
        NodeVisitor nested = domain.child(null, "domain-config");
        NodeVisitor nestedDomain = nested.child(null, "domain");
        nestedDomain.text(0, "sub.example.com"); nestedDomain.end();
        nested.end(); domain.end(); root.end();
        return writer.toByteArray();
    }

    private static List<String> attributes(byte[] xml) throws Exception {
        List<String> result = new ArrayList<>();
        new AxmlReader(xml).accept(new AxmlVisitor() {
            @Override public NodeVisitor child(String ns, String name) { return visitor(name); }
            private NodeVisitor visitor(String tag) {
                return new NodeVisitor() {
                    @Override public void attr(String ns, String name, int id, int type, Object value) {
                        result.add((name.equals("cleartextTrafficPermitted") ? "" : tag + ":") + name + "=" + value);
                    }
                    @Override public NodeVisitor child(String ns, String name) { return visitor(name); }
                };
            }
        });
        return result;
    }
}
