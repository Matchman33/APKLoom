package top.nkbe.npatch.patch.wrapper;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;
import top.nkbe.npatch.share.WrapperConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class WrapperGadgetTest {
    @Test public void preservesCustomConfigBytesAndUnknownSchema() throws Exception {
        byte[] config = ("\ufeff{\r\n  \"interaction\": {\"type\": \"vendor-connect\", \"token\": \"测试\"},\r\n"
                + "  \"vendor\": [1e3, 9007199254740993, true, null]\r\n}\r\n").getBytes(StandardCharsets.UTF_8);
        WrapperGadget gadget = WrapperGadget.custom(elf(183), config, null);
        String prefix = WrapperConfig.GADGET_PREFIX + "arm64-v8a/";
        assertEquals("custom", gadget.mode());
        assertArrayEquals(config, gadget.entries().get(prefix + WrapperConfig.GADGET_CONFIG));
        assertFalse(gadget.entries().containsKey(prefix + WrapperConfig.GADGET_SCRIPT));
        byte[] expected = config.clone();
        config[0] = 0;
        var entries = gadget.entries();
        entries.get(prefix + WrapperConfig.GADGET_CONFIG)[0] = 0;
        assertArrayEquals(expected, gadget.entries().get(prefix + WrapperConfig.GADGET_CONFIG));
    }

    @Test public void customModeDoesNotRequireOfficialFieldsOrRewritePaths() throws Exception {
        for (String text : new String[] {"{}", "{\"interaction\":null}", "{\"vendor\":{\"enabled\":true}}",
                "{\"interaction\":{\"type\":\"script\",\"path\":\"/vendor/script.js\"}}"}) {
            byte[] config = text.getBytes(StandardCharsets.UTF_8);
            WrapperGadget gadget = WrapperGadget.custom(elf(62), config, null);
            assertArrayEquals(config, gadget.entries().get(WrapperConfig.GADGET_PREFIX + "x86_64/" + WrapperConfig.GADGET_CONFIG));
        }
    }

    @Test public void customModeCanAttachBinaryScriptWithoutChangingConfig() throws Exception {
        byte[] config = "{\"vendor-script\":\"libscript.so\"}".getBytes(StandardCharsets.UTF_8);
        byte[] script = new byte[] {0, (byte) 0xff, (byte) 0xc3, 0x28, (byte) 0x80};
        var entries = WrapperGadget.custom(elf(183), config, script).entries();
        String prefix = WrapperConfig.GADGET_PREFIX + "arm64-v8a/";
        assertArrayEquals(config, entries.get(prefix + WrapperConfig.GADGET_CONFIG));
        assertArrayEquals(script, entries.get(prefix + WrapperConfig.GADGET_SCRIPT));
        assertThrows(IOException.class, () -> WrapperGadget.custom(elf(183), config, new byte[0]));
        assertThrows(IOException.class, () -> WrapperGadget.custom(elf(183), config, new byte[16 * 1024 * 1024 + 1]));
    }

    @Test public void scriptModePreservesOpaqueBytesAndCopiesInput() throws Exception {
        byte[] script = new byte[] {0, (byte) 0xc3, 0x28, (byte) 0xff, (byte) 0x80};
        byte[] expected = script.clone();
        WrapperGadget gadget = WrapperGadget.script(elf(183), script);
        script[0] = 1;
        String path = WrapperConfig.GADGET_PREFIX + "arm64-v8a/" + WrapperConfig.GADGET_SCRIPT;
        assertArrayEquals(expected, gadget.entries().get(path));
    }

    @Test public void rejectsMalformedOrNonObjectCustomJson() {
        for (String invalid : new String[] {"", " ", "[]", "null", "42", "\"text\"", "{", "{} {}", "{} trailing",
                "{key:1}", "{'key':1}", "{\"key\":1,}", "{/*comment*/\"key\":1}", "{\"key\":NaN}"}) {
            assertThrows(invalid, IOException.class, () -> WrapperGadget.custom(elf(183), invalid.getBytes(StandardCharsets.UTF_8), null));
        }
        assertThrows(IOException.class, () -> WrapperGadget.validateCustomConfig(null));
        assertThrows(IOException.class, () -> WrapperGadget.validateCustomConfig(new byte[] {'{', (byte) 0xff, '}'}));
        assertThrows(IOException.class, () -> WrapperGadget.custom(elf(3), "{}".getBytes(StandardCharsets.UTF_8), null));
    }

    @Test public void boundsCustomConfigurationSizeWithoutReserializing() throws Exception {
        byte[] maximum = ("{}" + " ".repeat(WrapperGadget.MAX_CONFIG_SIZE - 2)).getBytes(StandardCharsets.UTF_8);
        assertEquals(WrapperGadget.MAX_CONFIG_SIZE, WrapperGadget.validateCustomConfig(maximum).length());
        assertThrows(IOException.class, () -> WrapperGadget.validateCustomConfig(java.util.Arrays.copyOf(maximum, maximum.length + 1)));
    }

    @Test public void exportedPresetsMatchPackagedPresets() throws Exception {
        String prefix = WrapperConfig.GADGET_PREFIX + "arm64-v8a/";
        assertArrayEquals(WrapperGadget.listenConfig("localhost", 12345, false).getBytes(StandardCharsets.UTF_8),
                WrapperGadget.listen(elf(183), "localhost", 12345, false).entries().get(prefix + WrapperConfig.GADGET_CONFIG));
        assertArrayEquals(WrapperGadget.scriptConfig().getBytes(StandardCharsets.UTF_8),
                WrapperGadget.script(elf(183), "// script".getBytes(StandardCharsets.UTF_8)).entries().get(prefix + WrapperConfig.GADGET_CONFIG));
    }
    @Test public void createsListenConfigurationWithFixedNames() throws Exception {
        WrapperGadget gadget = WrapperGadget.listen(elf(183), "127.0.0.1", 27043, false);
        assertEquals("arm64-v8a", gadget.abi());
        assertEquals("listen", gadget.mode());
        Map<String, byte[]> entries = gadget.entries();
        String prefix = WrapperConfig.GADGET_PREFIX + "arm64-v8a/";
        var interaction = JsonParser.parseString(new String(
                entries.get(prefix + WrapperConfig.GADGET_CONFIG), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("interaction");
        assertEquals("127.0.0.1", interaction.get("address").getAsString());
        assertEquals(27043, interaction.get("port").getAsInt());
        assertEquals("resume", interaction.get("on_load").getAsString());
    }

    @Test public void createsScriptConfigurationWithFixedInternalPath() throws Exception {
        byte[] script = "rpc.exports = {};".getBytes(StandardCharsets.UTF_8);
        WrapperGadget gadget = WrapperGadget.script(elf(62), script);
        assertEquals("x86_64", gadget.abi());
        String prefix = WrapperConfig.GADGET_PREFIX + "x86_64/";
        Map<String, byte[]> entries = gadget.entries();
        var interaction = JsonParser.parseString(new String(
                entries.get(prefix + WrapperConfig.GADGET_CONFIG), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("interaction");
        assertEquals(WrapperConfig.GADGET_SCRIPT, interaction.get("path").getAsString());
        assertEquals("ignore", interaction.get("on_change").getAsString());
        assertEquals(new String(script, StandardCharsets.UTF_8),
                new String(entries.get(prefix + WrapperConfig.GADGET_SCRIPT), StandardCharsets.UTF_8));
    }

    @Test public void rejectsUnsupportedElfInvalidPortAndInvalidScriptSize() {
        assertThrows(IOException.class, () -> WrapperGadget.detectAbi(elf(3)));
        assertThrows(IOException.class, () -> WrapperGadget.listen(elf(183), "127.0.0.1", 0, true));
        assertThrows(IOException.class, () -> WrapperGadget.script(elf(183), null));
        assertThrows(IOException.class, () -> WrapperGadget.script(elf(183), new byte[0]));
        assertThrows(IOException.class, () -> WrapperGadget.script(elf(183), new byte[16 * 1024 * 1024 + 1]));
    }

    private byte[] elf(int machine) {
        byte[] elf = new byte[20];
        elf[0] = 0x7f;
        elf[1] = 'E';
        elf[2] = 'L';
        elf[3] = 'F';
        elf[4] = 2;
        elf[5] = 1;
        elf[18] = (byte) machine;
        elf[19] = (byte) (machine >>> 8);
        return elf;
    }
}
