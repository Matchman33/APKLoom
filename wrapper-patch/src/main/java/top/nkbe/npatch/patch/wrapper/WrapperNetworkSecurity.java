package top.nkbe.npatch.patch.wrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

final class WrapperNetworkSecurity {
    private static final String CLEARTEXT = "cleartextTrafficPermitted";

    private WrapperNetworkSecurity() {}

    static byte[] rewrite(byte[] xml, boolean allowed) throws IOException {
        if (xml.length < 8 || xml[0] != 3 || xml[1] != 0) return null;
        List<Node> roots = new ArrayList<>();
        List<Namespace> namespaces = new ArrayList<>();
        new AxmlReader(xml).accept(new AxmlVisitor() {
            @Override public void ns(String prefix, String uri, int line) {
                namespaces.add(new Namespace(prefix, uri, line));
            }
            @Override public NodeVisitor child(String ns, String name) {
                Node node = new Node(ns, name);
                roots.add(node);
                return node;
            }
        });
        if (roots.size() != 1 || !"network-security-config".equals(roots.get(0).name)) return null;
        Node root = roots.get(0);
        if (root.children.stream().noneMatch(n -> "base-config".equals(n.name))) {
            root.children.add(new Node(null, "base-config"));
        }
        AxmlWriter writer = new AxmlWriter();
        for (Namespace ns : namespaces) writer.ns(ns.prefix, ns.uri, ns.line);
        write(root, writer, allowed);
        return writer.toByteArray();
    }

    private static void write(Node node, NodeVisitor parent, boolean allowed) {
        NodeVisitor output = parent.child(node.ns, node.name);
        output.line(node.line);
        boolean config = "base-config".equals(node.name) || "domain-config".equals(node.name);
        for (Attr attr : node.attributes) {
            if (config && CLEARTEXT.equals(attr.name)) continue;
            output.attr(attr.ns, attr.name, attr.id, attr.type, attr.value);
        }
        if (config) output.attr(null, CLEARTEXT, -1, NodeVisitor.TYPE_INT_BOOLEAN, allowed);
        for (Node child : node.children) write(child, output, allowed);
        for (Text text : node.texts) output.text(text.line, text.value);
        output.end();
    }

    private record Namespace(String prefix, String uri, int line) {}
    private record Attr(String ns, String name, int id, int type, Object value) {}
    private record Text(int line, String value) {}

    private static final class Node extends NodeVisitor {
        final String ns;
        final String name;
        int line;
        final List<Attr> attributes = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();

        Node(String ns, String name) { this.ns = ns; this.name = name; }
        @Override public void attr(String ns, String name, int id, int type, Object value) {
            attributes.add(new Attr(ns, name, id, type, value));
        }
        @Override public NodeVisitor child(String ns, String name) {
            Node child = new Node(ns, name);
            children.add(child);
            return child;
        }
        @Override public void line(int line) { this.line = line; }
        @Override public void text(int line, String value) { texts.add(new Text(line, value)); }
    }
}
