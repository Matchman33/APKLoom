#include "art_inline_hook.h"

#include <array>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <sys/mman.h>
#include <unistd.h>

#include "dobby/dobby_internal.h"
#include "Interceptor.h"

using TestFunction = int (*)();

static TestFunction previous_backup;
static TestFunction apkloom_backup;
static bool fail_near_allocation;
static void* fail_patch_address;

extern CodeBufferBase* RealNearTrampoline(InterceptRouting*, addr_t, addr_t)
    asm("__real__Z28GenerateNearTrampolineBufferP16InterceptRoutingmm");
CodeBufferBase* WrappedNearTrampoline(InterceptRouting*, addr_t, addr_t)
    asm("__wrap__Z28GenerateNearTrampolineBufferP16InterceptRoutingmm");

CodeBufferBase* WrappedNearTrampoline(InterceptRouting* routing, addr_t source, addr_t destination) {
    return fail_near_allocation ? nullptr : RealNearTrampoline(routing, source, destination);
}

extern "C" int __real_DobbyCodePatch(void*, uint8_t*, uint32_t);
extern "C" int __wrap_DobbyCodePatch(void* address, uint8_t* buffer, uint32_t size) {
    return address == fail_patch_address ? -1 : __real_DobbyCodePatch(address, buffer, size);
}

static void Require(bool condition, const char* message) {
    if (!condition) {
        std::fprintf(stderr, "FAIL: %s (errno=%d)\n", message, errno);
        std::exit(1);
    }
}

static int PreviousReplacement() { return previous_backup() + 10; }
static int ApkLoomReplacement() { return apkloom_backup() + 100; }

class CodePage {
public:
    CodePage(void* reference, bool far) : size_(static_cast<size_t>(sysconf(_SC_PAGESIZE))) {
        const uintptr_t center = reinterpret_cast<uintptr_t>(reference) & ~(size_ - 1);
        constexpr int fixed_no_replace = 0x100000;
        for (uintptr_t index = 1; index <= 30; ++index) {
            const uintptr_t hint = far ? index * 0x1000000000ULL : center + index * 0x400000ULL;
            if (far && (hint > center ? hint - center : center - hint) <= 0x100000000ULL) continue;
            auto memory = mmap(reinterpret_cast<void*>(hint), size_, PROT_READ | PROT_WRITE,
                               MAP_PRIVATE | MAP_ANONYMOUS | fixed_no_replace, -1, 0);
            if (memory == MAP_FAILED) continue;
            if (reinterpret_cast<uintptr_t>(memory) != hint) {
                munmap(memory, size_);
                continue;
            }
            address_ = memory;
            break;
        }
        Require(address_ != nullptr, "allocate test code page");
    }

    ~CodePage() { munmap(address_, size_); }
    void* Address(size_t offset = 0) const { return static_cast<uint8_t*>(address_) + offset; }
    void Protect() {
        __builtin___clear_cache(static_cast<char*>(address_), static_cast<char*>(address_) + size_);
        Require(mprotect(address_, size_, PROT_READ | PROT_EXEC) == 0, "protect test code page");
    }

private:
    size_t size_;
    void* address_ = nullptr;
};

static std::array<uint32_t, 4> AbsoluteJump(void* target, uint32_t reg = 17) {
    const uintptr_t destination = reinterpret_cast<uintptr_t>(target);
    return {0x58000040U | reg, 0xd61f0000U | (reg << 5), static_cast<uint32_t>(destination),
            static_cast<uint32_t>(destination >> 32)};
}

static void InstallPreviousHook(CodePage& original, unsigned footprint, uint32_t reg = 17) {
    const std::array<uint32_t, 5> body = {0x52800020, 0x11000800, 0x11001000, 0xd503201f, 0xd65f03c0};
    std::memcpy(original.Address(), body.data(), sizeof(body));
    if (footprint != 0) {
        auto continuation = AbsoluteJump(original.Address(footprint));
        std::memcpy(original.Address(128), body.data(), footprint);
        std::memcpy(original.Address(128 + footprint), continuation.data(), sizeof(continuation));
        previous_backup = reinterpret_cast<TestFunction>(original.Address(128));
    }
    original.Protect();
    if (footprint == 0) return;

    const uintptr_t source = reinterpret_cast<uintptr_t>(original.Address());
    const uintptr_t destination = reinterpret_cast<uintptr_t>(&PreviousReplacement);
    auto patch = AbsoluteJump(reinterpret_cast<void*>(destination), reg);
    if (footprint == 4) {
        const int64_t displacement = static_cast<int64_t>(destination) - static_cast<int64_t>(source);
        Require(displacement > -0x8000000 && displacement < 0x8000000, "previous short branch range");
        patch[0] = 0x14000000 | (static_cast<uint32_t>(displacement >> 2) & 0x03ffffff);
    } else if (footprint == 12) {
        const int64_t page_delta = (static_cast<int64_t>(destination & ~uintptr_t{0xfff}) -
                                    static_cast<int64_t>(source & ~uintptr_t{0xfff})) >> 12;
        const uint32_t immediate = static_cast<uint32_t>(page_delta) & 0x1fffff;
        patch[0] = 0x90000000U | reg | ((immediate & 3) << 29) | ((immediate >> 2) << 5);
        patch[1] = 0x91000000U | (reg << 5) | reg | ((destination & 0xfff) << 10);
        patch[2] = 0xd61f0000U | (reg << 5);
    }
    Require(__real_DobbyCodePatch(original.Address(), reinterpret_cast<uint8_t*>(patch.data()), footprint) == 0,
            "install previous engine hook");
}

static void TestChaining(unsigned footprint, bool far, uint32_t reg = 17) {
    CodePage original(reinterpret_cast<void*>(&PreviousReplacement), false);
    CodePage destination(reinterpret_cast<void*>(&PreviousReplacement), far);
    InstallPreviousHook(original, footprint, reg);
    auto jump = AbsoluteJump(reinterpret_cast<void*>(&ApkLoomReplacement));
    std::memcpy(destination.Address(), jump.data(), sizeof(jump));
    destination.Protect();
    auto function = reinterpret_cast<TestFunction>(original.Address());
    const int before = footprint == 0 ? 7 : 17;
    Require(function() == before, "previous engine works before rehook");

    std::array<uint8_t, 20> previous_entry;
    std::memcpy(previous_entry.data(), original.Address(), previous_entry.size());
    void* backup = nullptr;
    Require(lspd::HookArtInline(original.Address(), destination.Address(), &backup) == 0 && backup != nullptr,
            "install bounded hook");
    apkloom_backup = reinterpret_cast<TestFunction>(backup);
    Require((*static_cast<uint32_t*>(original.Address()) & 0xfc000000) == 0x14000000, "single branch entry");
    Require(std::memcmp(original.Address(4), previous_entry.data() + 4, previous_entry.size() - 4) == 0,
            "cached continuation and prior jump data preserved");
    Require(function() == before + 100, "both replacement chains execute");
    if (footprint != 0) Require(previous_backup() == 7, "previous cached backup remains callable");

    void* duplicate = reinterpret_cast<void*>(1);
    Require(lspd::HookArtInline(original.Address(), destination.Address(), &duplicate) != 0 && duplicate == nullptr,
            "duplicate install rejected without changing hook");
    Require(function() == before + 100, "duplicate rejection preserves working hook");
    Require(DobbyDestroy(original.Address()) == 0, "unhook bounded hook");
    Require(std::memcmp(original.Address(), previous_entry.data(), previous_entry.size()) == 0,
            "unhook restores previous engine entry");
    Require(function() == before, "previous engine survives unhook");
}

static void TestFailure(bool allocation_failure) {
    CodePage original(reinterpret_cast<void*>(&PreviousReplacement), false);
    CodePage destination(reinterpret_cast<void*>(&PreviousReplacement), true);
    InstallPreviousHook(original, 12);
    auto jump = AbsoluteJump(reinterpret_cast<void*>(&ApkLoomReplacement));
    std::memcpy(destination.Address(), jump.data(), sizeof(jump));
    destination.Protect();
    std::array<uint8_t, 20> entry;
    std::memcpy(entry.data(), original.Address(), entry.size());
    const int count = Interceptor::SharedInstance()->count();
    fail_near_allocation = allocation_failure;
    fail_patch_address = allocation_failure ? nullptr : original.Address();
    void* backup = reinterpret_cast<void*>(1);
    Require(lspd::HookArtInline(original.Address(), destination.Address(), &backup) != 0 && backup == nullptr,
            "failed preparation or activation reports failure");
    fail_near_allocation = false;
    fail_patch_address = nullptr;
    Require(Interceptor::SharedInstance()->count() == count, "failed hook is not registered");
    Require(std::memcmp(original.Address(), entry.data(), entry.size()) == 0, "failure never falls back to long jump");
    Require(reinterpret_cast<TestFunction>(original.Address())() == 17, "previous hook survives failure");
}

int main(int argc, char** argv) {
    const unsigned iterations = argc == 2 ? static_cast<unsigned>(std::strtoul(argv[1], nullptr, 10)) : 1;
    Require(iterations > 0 && iterations <= 1000, "valid iteration count");
    void* backup = reinterpret_cast<void*>(1);
    Require(lspd::HookArtInline(nullptr, reinterpret_cast<void*>(&ApkLoomReplacement), &backup) != 0 &&
            backup == nullptr, "null original rejected");
    Require(lspd::HookArtInline(reinterpret_cast<void*>(&PreviousReplacement), nullptr, &backup) != 0 &&
            backup == nullptr, "null replacement rejected");
    Require(lspd::HookArtInline(reinterpret_cast<void*>(&PreviousReplacement),
                               reinterpret_cast<void*>(&ApkLoomReplacement), nullptr) != 0, "null backup rejected");
    Require(lspd::HookArtInline(reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(&PreviousReplacement) + 1),
                               reinterpret_cast<void*>(&ApkLoomReplacement), &backup) != 0 && backup == nullptr,
            "unaligned original rejected");

    for (unsigned iteration = 0; iteration < iterations; ++iteration) {
        for (unsigned footprint : {0U, 4U, 12U, 16U}) TestChaining(footprint, true);
        TestChaining(12, false);
        TestChaining(12, true, 16);
        TestChaining(16, true, 16);
        TestFailure(true);
        TestFailure(false);
    }
    std::printf("PASS: %u rounds, prior 4/12/16-byte hooks, x16/x17, near/far targets, unhook, allocation and patch failure\n",
                iterations);
    return 0;
}
