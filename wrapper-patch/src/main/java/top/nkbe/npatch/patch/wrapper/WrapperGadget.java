package top.nkbe.npatch.patch.wrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import top.nkbe.npatch.share.WrapperConfig;

public final class WrapperGadget {
    public static final int MAX_CONFIG_SIZE = 1024 * 1024;
    private static final int MAX_LIBRARY_SIZE = 128 * 1024 * 1024;
    private static final int MAX_SCRIPT_SIZE = 16 * 1024 * 1024;

    private final String abi;
    private final String mode;
    private final byte[] library;
    private final byte[] config;
    private final byte[] script;

    private WrapperGadget(String abi, String mode, byte[] library, byte[] config, byte[] script) {
        this.abi = abi;
        this.mode = mode;
        this.library = library;
        this.config = config;
        this.script = script;
    }

    public static WrapperGadget listen(byte[] library, String address, int port, boolean waitForClient)
            throws IOException {
        String abi = detectAbi(library);
        return new WrapperGadget(abi, "listen", library.clone(),
                listenConfig(address, port, waitForClient).getBytes(StandardCharsets.UTF_8), null);
    }

    public static String listenConfig(String address, int port, boolean waitForClient) throws IOException {
        String host = address == null ? "" : address.trim();
        if (host.isEmpty() || host.length() > 255 || host.chars().anyMatch(Character::isISOControl)) {
            throw new IOException("Frida Gadget listen address is invalid");
        }
        if (port < 1 || port > 65535) throw new IOException("Frida Gadget port must be between 1 and 65535");

        JsonObject interaction = new JsonObject();
        interaction.addProperty("type", "listen");
        interaction.addProperty("address", host);
        interaction.addProperty("port", port);
        interaction.addProperty("on_port_conflict", "fail");
        interaction.addProperty("on_load", waitForClient ? "wait" : "resume");
        return configText(interaction);
    }

    public static WrapperGadget script(byte[] library, byte[] script) throws IOException {
        String abi = detectAbi(library);
        validateScript(script);
        return new WrapperGadget(abi, "script", library.clone(),
                scriptConfig().getBytes(StandardCharsets.UTF_8), script.clone());
    }

    public static String scriptConfig() {
        JsonObject interaction = new JsonObject();
        interaction.addProperty("type", "script");
        interaction.addProperty("path", WrapperConfig.GADGET_SCRIPT);
        interaction.addProperty("on_change", "ignore");
        return configText(interaction);
    }

    public static WrapperGadget custom(byte[] library, byte[] config, byte[] script) throws IOException {
        String abi = detectAbi(library);
        validateCustomConfig(config);
        if (script != null) validateScript(script);
        return new WrapperGadget(abi, "custom", library.clone(), config.clone(),
                script == null ? null : script.clone());
    }

    /** Validate syntax only. Modified Gadgets own their schema and interaction types. */
    public static String validateCustomConfig(byte[] config) throws IOException {
        if (config == null || config.length == 0 || config.length > MAX_CONFIG_SIZE) {
            throw new IOException("Gadget config must be a non-empty JSON object, at most 1 MiB");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(config)).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("Gadget config must be UTF-8 text", error);
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Gadget config must contain exactly one JSON object");
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid Gadget JSON: " + error.getMessage(), error);
        }
        return text;
    }

    public static String detectAbi(byte[] library) throws IOException {
        if (library == null || library.length < 20 || library.length > MAX_LIBRARY_SIZE
                || library[0] != 0x7f || library[1] != 'E' || library[2] != 'L' || library[3] != 'F'
                || library[4] != 2 || library[5] != 1) {
            throw new IOException("Frida Gadget must be a 64-bit little-endian ELF library");
        }
        int machine = (library[18] & 255) | ((library[19] & 255) << 8);
        if (machine == 183) return "arm64-v8a";
        if (machine == 62) return "x86_64";
        throw new IOException("Frida Gadget must target arm64-v8a or x86_64");
    }

    public String abi() {
        return abi;
    }

    public String mode() {
        return mode;
    }

    Map<String, byte[]> entries() {
        String prefix = WrapperConfig.GADGET_PREFIX + abi + "/";
        Map<String, byte[]> result = new TreeMap<>();
        result.put(prefix + WrapperConfig.GADGET_LIBRARY, library.clone());
        result.put(prefix + WrapperConfig.GADGET_CONFIG, config.clone());
        if (script != null) result.put(prefix + WrapperConfig.GADGET_SCRIPT, script.clone());
        return result;
    }

    private static String configText(JsonObject interaction) {
        JsonObject root = new JsonObject();
        root.add("interaction", interaction);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static void validateScript(byte[] script) throws IOException {
        if (script == null || script.length == 0 || script.length > MAX_SCRIPT_SIZE) {
            throw new IOException("Frida Gadget script is empty or larger than 16 MiB");
        }
    }
}
