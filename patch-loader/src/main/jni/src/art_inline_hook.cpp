#include "art_inline_hook.h"

#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <mutex>

#include <dobby.h>

#if defined(__aarch64__)
#include "dobby/dobby_internal.h"
#include "Interceptor.h"

extern CodeBufferBase* GenerateNearTrampolineBuffer(InterceptRouting* routing, addr_t source, addr_t destination);

namespace {

class ArtHookRouting final : public InterceptRouting {
public:
    ArtHookRouting(InterceptEntry* entry, void* replacement) : InterceptRouting(entry) {
        SetTrampolineTarget(reinterpret_cast<addr_t>(replacement));
    }

    ~ArtHookRouting() {
        delete origin_;
        delete relocated_;
        delete trampoline_buffer_;
    }

    void DispatchRouting() override {
        SetTrampolineBuffer(GenerateNearTrampolineBuffer(this, entry_->patched_addr, GetTrampolineTarget()));
        if (GetTrampolineBuffer() == nullptr || GetTrampolineBuffer()->GetBufferSize() != sizeof(uint32_t)) return;
        ready_ = RelocatePreviousEntry();
    }

    void Active() override {
        if (ready_) {
            patch_result_ = DobbyCodePatch(reinterpret_cast<void*>(entry_->patched_addr),
                                          GetTrampolineBuffer()->GetBuffer(), sizeof(uint32_t));
        }
    }

    bool Ready() const { return ready_; }
    int PatchResult() const { return patch_result_; }

private:
    bool RelocatePreviousEntry() {
        auto instructions = reinterpret_cast<const uint32_t*>(entry_->patched_addr);
        uint32_t size = sizeof(uint32_t);
        const uint32_t reg = instructions[0] & 31U;
        const uint32_t branch_register = 0xd61f0000U | (reg << 5);
        if ((instructions[0] & 0xff000000U) == 0x58000000U && instructions[1] == branch_register) {
            size = 8;
        } else if ((instructions[0] & 0x9f000000U) == 0x90000000U &&
                   (instructions[1] & 0xffc003ffU) == (0x91000000U | (reg << 5) | reg) &&
                   instructions[2] == branch_register) {
            size = 12;
        }
        origin_ = new CodeMemBlock(entry_->patched_addr, size);
        relocated_ = new CodeMemBlock();
        GenRelocateCodeAndBranch(reinterpret_cast<void*>(entry_->patched_addr), origin_, relocated_);
        if (relocated_->addr == 0 || relocated_->size == 0) return false;
        entry_->relocated_addr = relocated_->addr;
        entry_->relocated_size = relocated_->size;
        entry_->origin_insn_size = sizeof(uint32_t);
        std::memcpy(entry_->origin_insns, instructions, sizeof(uint32_t));
        return true;
    }

    bool ready_ = false;
    int patch_result_ = -1;
};

std::mutex art_hook_mutex;

}
#endif

namespace lspd {

int HookArtInline(void* original, void* replacement, void** backup) {
    if (backup != nullptr) *backup = nullptr;
    if (original == nullptr || replacement == nullptr || backup == nullptr) return -1;

#if defined(__aarch64__)
    if ((reinterpret_cast<uintptr_t>(original) & 3U) != 0 ||
        (reinterpret_cast<uintptr_t>(replacement) & 3U) != 0) return -1;

    std::lock_guard lock(art_hook_mutex);
    auto interceptor = Interceptor::SharedInstance();
    if (interceptor->find(reinterpret_cast<addr_t>(original)) != nullptr) return -1;

    auto entry = new InterceptEntry(kFunctionInlineHook, reinterpret_cast<addr_t>(original));
    auto routing = new ArtHookRouting(entry, replacement);
    routing->Prepare();
    routing->DispatchRouting();
    if (!routing->Ready()) {
        __android_log_print(ANDROID_LOG_ERROR, "NPatch-ArtHook", "Cannot prepare bounded ART hook at %p", original);
        delete routing;
        delete entry;
        return -1;
    }

    *backup = reinterpret_cast<void*>(entry->relocated_addr);
    routing->Commit();
    if (routing->PatchResult() != 0) {
        *backup = nullptr;
        __android_log_print(ANDROID_LOG_ERROR, "NPatch-ArtHook", "Cannot patch ART hook at %p", original);
        delete routing;
        delete entry;
        return -1;
    }
    interceptor->add(entry);
    return 0;
#else
    return DobbyHook(original, reinterpret_cast<dobby_dummy_func_t>(replacement),
                     reinterpret_cast<dobby_dummy_func_t*>(backup));
#endif
}

}
