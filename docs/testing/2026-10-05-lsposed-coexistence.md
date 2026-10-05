# ART-hook coexistence regression verification

Date: 2026-10-05 (Asia/Shanghai).
Device: OnePlus PLC110, Android 15, ARM64.
Target: com.r2games.myhero.aligames, version 7.6.1 (203).

## Observed failure

The remaining failure after removing module scopes was the same SIGILL at
ART Class::SetStatus +12. The entry contained a 16-byte literal-address jump
from APKLoom, while an earlier system LSPosed trampoline resumed at +12.
The faulting instruction was part of the embedded address, not executable code.
Frida Gadget was not mapped in the failing process.

## Change under test

APKLoom's ARM64 ART adapter patches only one 4-byte branch. Nearby relays
support distant replacements without increasing the patched entry footprint.
Recognized old ADRP/ADD/BR and LDR/BR sequences are relocated as complete
jumps; this prevents Dobby's return branch from clobbering a live x17 value.
Only the first 4 bytes are saved and restored when unhooking.

See ../lsposed-coexistence.md for implementation boundaries and native-test commands.
The core submodule, embedded APK, Unity resource paths, and Gadget binary
were unchanged.

## Completed checks

- Formal manager Release and CLI builds succeeded; manager Release lint passed.
- A forced rerun passed 68 manager, wrapper-patch, and loader unit tests.
- The production adapter passed 1,000 native ARM64 regression rounds covering
  prior 4/12/16-byte hooks, x16/x17, near/far replacements, duplicate rejection,
  cached backup calls, unhook, allocation failure, and entry-patch failure.
- The wrapped game was updated with adb install -r -t without clearing data.
- Twenty consecutive cold starts between 11:28:10 and 11:30:23 passed.
- Each round verified that port 27042 belonged to the game PID, connected a
  Frida client, attached/resumed, checked process survival, and read a 4-byte
  ART branch with the earlier engine's following instructions preserved.
- Logs confirmed system LSPosed callbacks and Unity/MainActivity startup.
- The final process remained alive in MainActivity after the test.
- The manager test build was also updated with the existing formal certificate.
- Temporary ADB forwarding was removed; the existing port-28042 server was untouched.

## Preserved inputs

Embedded APK SHA256:
372b418d479f6fa35e92ddedc5ea39e7f0b9827e49363ce9a875eda6dc408062

Gadget SHA256:
fc6aa7b5b9604c6ec9b021c1cf0394968cf7279f8d3b29c98167036f7e440eba

Gadget configuration: listen 127.0.0.1:27042, on_load=wait,
on_port_conflict=fail. Overlay prompting and signature compatibility retained.

## Limitations

The device runs used the current module-scope settings after the user removed
the game from scopes. Restoration of those scopes has not been confirmed.
These results verify coexistence with the injected system framework, not every
third-party module combination. Restore the required scopes and repeat testing
before claiming module-specific compatibility. Twenty successful starts do not
prove the absence of every intermittent failure.

Device verification used the 1.0.8/615 test build with the new runtime. The
1.0.9/616 release increments package/version metadata and uses the same fix.
