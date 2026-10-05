cmake_minimum_required(VERSION 3.22)
project(apkloom_art_hook_tests LANGUAGES C CXX)

if (NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "ART hook coexistence tests require Android arm64-v8a")
endif ()

get_filename_component(APKLOOM_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(NearBranch ON CACHE BOOL "" FORCE)
set(Plugin.SymbolResolver OFF CACHE BOOL "" FORCE)
add_subdirectory("${APKLOOM_ROOT}/core/external/dobby" "${CMAKE_BINARY_DIR}/dobby")
get_target_property(DOBBY_INTERNAL_INCLUDE_DIRS dobby_static INCLUDE_DIRECTORIES)

add_executable(art-hook-coexistence
    art_hook_coexistence_test.cpp
    ../../main/jni/src/art_inline_hook.cpp)
target_compile_features(art-hook-coexistence PRIVATE cxx_std_17)
target_compile_options(art-hook-coexistence PRIVATE -fno-rtti -fno-exceptions)
target_include_directories(art-hook-coexistence PRIVATE
    ../../main/jni/src ${DOBBY_INTERNAL_INCLUDE_DIRS})
target_link_libraries(art-hook-coexistence PRIVATE dobby_static log)
target_link_options(art-hook-coexistence PRIVATE
    "-Wl,--wrap=_Z28GenerateNearTrampolineBufferP16InterceptRoutingmm"
    "-Wl,--wrap=DobbyCodePatch")
