package top.nkbe.npatch.patch.wrapper;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import top.nkbe.npatch.share.WrapperConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertArrayEquals;

public class WrapperRuntimeTest {
    @Test public void allowsCustomSchemaOnlyForExplicitPerPackageCustomSelection() throws Exception {
        byte[] config = "{\"interaction\":{\"type\":\"vendor\"},\"extras\":true}\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> WrapperRuntime.read(gadgetArchive(new String(config, StandardCharsets.UTF_8), false)));
        WrapperGadget selected = WrapperGadget.custom(elf("x86_64", false), config, null);
        Map<String, byte[]> entries = WrapperRuntime.read(gadgetArchive(true, true), selected);
        assertEquals(5, entries.size());
        assertArrayEquals(config, entries.get("gadget/x86_64/" + WrapperConfig.GADGET_CONFIG));
        assertFalse(entries.keySet().stream().anyMatch(name -> name.startsWith("gadget/arm64-v8a/")));
        assertFalse(entries.containsKey("gadget/x86_64/" + WrapperConfig.GADGET_SCRIPT));
    }
    @Test public void acceptsBothSupportedArchitectures() throws Exception {
        assertEquals(3, WrapperRuntime.read(baseArchive(false)).size());
    }

    @Test public void rejectsWrongElfAndEmptyPayload() throws Exception {
        assertThrows(IOException.class, () -> WrapperRuntime.read(baseArchive(true)));
        assertThrows(IOException.class, () -> WrapperRuntime.read(new byte[0]));
    }

    @Test public void rejectsUnexpectedPaths() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("../libnpatch.so"));
            zip.write(new byte[20]);
        }
        assertThrows(IOException.class, () -> WrapperRuntime.read(output.toByteArray()));
    }

    @Test public void acceptsListenAndColocatedScriptModes() throws Exception {
        assertEquals(5, WrapperRuntime.read(gadgetArchive(false, false)).size());
        assertEquals(6, WrapperRuntime.read(gadgetArchive(true, true)).size());
    }

    @Test public void acceptsSoNamedScriptWithoutElfHeader() throws Exception {
        assertEquals(6, WrapperRuntime.read(gadgetArchive(false, true)).size());
    }

    @Test public void preservesEncryptedScriptBytesFromRuntimeArchive() throws Exception {
        byte[] script = new byte[] {0, (byte) 0xff, (byte) 0xc3, 0x28, (byte) 0x80};
        Map<String, byte[]> entries = WrapperRuntime.read(gadgetArchive(gadgetConfig("script", WrapperConfig.GADGET_SCRIPT), script));
        assertArrayEquals(script, entries.get(WrapperConfig.GADGET_PREFIX + "arm64-v8a/" + WrapperConfig.GADGET_SCRIPT));
    }

    @Test public void rejectsScriptModeWithoutColocatedSoScript() throws Exception {
        assertThrows(IOException.class, () -> WrapperRuntime.read(gadgetArchive(true, false)));
    }

    @Test public void rejectsScriptOutsideGadgetDirectory() throws Exception {
        assertThrows(IOException.class, () -> WrapperRuntime.read(gadgetArchive(
                gadgetConfig("script", "../script.js"), true)));
    }

    @Test public void rejectsMalformedGadgetConfig() throws Exception {
        assertThrows(IOException.class, () -> WrapperRuntime.read(gadgetArchive(
                "{\"interaction\":\"listen\"}", false)));
    }

    @Test public void replacesBuildTimeGadgetWithPerPackageSelection() throws Exception {
        byte[] archive = gadgetArchive(false, false);
        Map<String, byte[]> disabled = WrapperRuntime.read(archive, null);
        assertEquals(3, disabled.size());
        assertFalse(disabled.keySet().stream().anyMatch(name -> name.startsWith(WrapperConfig.GADGET_PREFIX)));

        WrapperGadget selected = WrapperGadget.script(elf("x86_64", false),
                "console.log('selected');".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> enabled = WrapperRuntime.read(archive, selected);
        assertEquals(6, enabled.size());
        assertTrue(enabled.containsKey("gadget/x86_64/" + WrapperConfig.GADGET_LIBRARY));
        assertTrue(enabled.containsKey("gadget/x86_64/" + WrapperConfig.GADGET_CONFIG));
        assertTrue(enabled.containsKey("gadget/x86_64/" + WrapperConfig.GADGET_SCRIPT));
        assertFalse(enabled.keySet().stream().anyMatch(name -> name.startsWith("gadget/arm64-v8a/")));
    }

    private byte[] baseArchive(boolean wrongMachine) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addBaseRuntime(zip, wrongMachine);
        }
        return output.toByteArray();
    }

    private byte[] gadgetArchive(boolean scriptMode, boolean includeScript) throws Exception {
        return gadgetArchive(gadgetConfig(scriptMode ? "script" : "listen",
                scriptMode ? WrapperConfig.GADGET_SCRIPT : null), includeScript);
    }

    private String gadgetConfig(String type, String path) {
        JsonObject interaction = new JsonObject();
        interaction.addProperty("type", type);
        if ("script".equals(type)) {
            interaction.addProperty("path", path);
            interaction.addProperty("on_change", "ignore");
        } else {
            interaction.addProperty("address", "127.0.0.1");
            interaction.addProperty("port", 27043);
            interaction.addProperty("on_load", "wait");
        }
        JsonObject root = new JsonObject();
        root.add("interaction", interaction);
        return root.toString();
    }

    private byte[] gadgetArchive(String config, boolean includeScript) throws Exception {
        return gadgetArchive(config, includeScript ? "rpc.exports = { init() {} };".getBytes(StandardCharsets.UTF_8) : null);
    }

    private byte[] gadgetArchive(String config, byte[] script) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addBaseRuntime(zip, false);
            String prefix = WrapperConfig.GADGET_PREFIX + "arm64-v8a/";
            add(zip, prefix + WrapperConfig.GADGET_LIBRARY, elf("arm64-v8a", false));
            add(zip, prefix + WrapperConfig.GADGET_CONFIG, config.getBytes(StandardCharsets.UTF_8));
            if (script != null) {
                add(zip, prefix + WrapperConfig.GADGET_SCRIPT, script);
            }
        }
        return output.toByteArray();
    }

    private void addBaseRuntime(ZipOutputStream zip, boolean wrongMachine) throws Exception {
        add(zip, "loader.bin", new byte[] {'d', 'e', 'x', 10, '0', '3', '5', 0});
        for (String abi : new String[] {"arm64-v8a", "x86_64"}) {
            add(zip, "so/" + abi + "/libnpatch.so", elf(abi, wrongMachine));
        }
    }

    private void add(ZipOutputStream zip, String name, byte[] contents) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents);
        zip.closeEntry();
    }

    private byte[] elf(String abi, boolean wrongMachine) {
        byte[] elf = new byte[20];
        elf[0] = 0x7f;
        elf[1] = 'E';
        elf[2] = 'L';
        elf[3] = 'F';
        elf[4] = 2;
        elf[5] = 1;
        elf[18] = (byte) (wrongMachine ? 0 : abi.startsWith("arm64") ? 183 : 62);
        return elf;
    }
}
