# System LSPosed coexistence

## Failure identified on Android 15 ARM64

The observed startup crashes were SIGILL at ART Class::SetStatus +12. A
16-byte APKLoom entry jump replaced the continuation used by an earlier
system LSPosed hook. The process executed the high half of a jump address
instead of an instruction. Frida Gadget had not been loaded in these crashes.
Removing module scopes reduced the frequency but did not remove the same fault.

## Bounded ART routing

APKLoom supplies LSPlant with a dedicated ART inline-hook adapter on ARM64.
It installs exactly one 4-byte branch and uses Dobby's nearby relay allocator
when the replacement is outside the branch range. It never falls back to
a longer entry patch. Other architectures keep the existing Dobby hook path.

The backup relocates a complete recognized ADRP/ADD/BR or literal LDR/BR
sequence from the previous engine, while the saved/restored entry remains
only 4 bytes. Relocating just the first instruction of these sequences would
let Dobby's return jump clobber x17 before the remaining old jump executes.
The previous engine's cached continuation and literal address remain intact.

Allocation and activation failures are reported without registering a hook.
LSPlant initialization failure stops the loader with a Java exception instead
of continuing with partially initialized ART hooks. Core and Dobby submodules
are not modified. Unity resource paths and the embedded APK are unchanged.

This addresses the observed native entry-patch conflict. It does not guarantee
compatibility between arbitrary modules that hook the same Java method or
between every system LSPosed and Android version. Module-specific failures
still require their own crash evidence.

## Native regression test

Configure with an installed Android NDK, CMake, and Ninja:

```powershell
cmake -S patch-loader/src/test/native -B out/art-hook-tests -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE=D:/sdk/ndk/29.0.13846066/build/cmake/android.toolchain.cmake `
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DCMAKE_BUILD_TYPE=Release
cmake --build out/art-hook-tests --target art-hook-coexistence
adb push out/art-hook-tests/art-hook-coexistence /data/local/tmp/art-hook-coexistence
adb shell chmod 755 /data/local/tmp/art-hook-coexistence
adb shell /data/local/tmp/art-hook-coexistence 100
```

Each round verifies bare entry and prior 4/12/16-byte hooks using x16 and x17, nearby and distant
replacements, duplicate rejection, preservation of cached backups, unhooking,
near-allocation failure, and entry-patch failure. Invalid arguments are tested
once. The failure paths use linker wrappers around the real production adapter.

For full application verification, keep the system framework enabled and restore
the required module scopes. Repack the original APK with the rebuilt runtime,
retain its original embedded hash and Gadget configuration, and install with
adb install -r. Do not uninstall or clear game data. Test repeated cold starts,
check that the Gadget socket belongs to the game PID, then attach/resume and
check that the same PID survives. Preserve new tombstones for any failures.
