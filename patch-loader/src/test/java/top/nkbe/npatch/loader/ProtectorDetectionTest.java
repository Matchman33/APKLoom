package top.nkbe.npatch.loader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProtectorDetectionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void ordinaryUnityResourcesDoNotEnableProtectorHooks() throws Exception {
        assertFalse(detect("assets/AssetBundle/313.0/application/spritepack/302360_point",
                "assets/bin/Data/0460290e1a14bbc4b9e70bdd58c36028"));
    }

    @Test public void recognizesExplicitProtectorArtifacts() throws Exception {
        assertTrue(detect("lib/arm64-v8a/libjiagu.so"));
        assertTrue(detect("assets/.jiagu/loader.dex"));
        assertTrue(detect("assets/qihoo/loader.dex"));
    }

    private boolean detect(String... entries) throws Exception {
        File apk = temporary.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(apk.toPath()))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(1);
                zip.closeEntry();
            }
        }
        var method = SigBypass.class.getDeclaredMethod("is360ProtectedApk", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, apk.getAbsolutePath());
    }
}
