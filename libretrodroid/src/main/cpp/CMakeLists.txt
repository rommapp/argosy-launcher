cmake_minimum_required(VERSION 3.13.2)

# now build app's shared lib
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall")

add_definitions("-DVFS_FRONTEND -DHAVE_STRL")

# Let's include oboe
set (OBOE_DIR oboe)
add_subdirectory (${OBOE_DIR} oboe)
include_directories (${OBOE_DIR}/include)
include_directories (${OBOE_DIR}/src)

include_directories("libretro/libretro-common/include")

# SoundTouch time-stretch (LGPL 2.1) for pitch-preserving fast forward
set(SOUNDTOUCH_DIR ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch)
include_directories(${SOUNDTOUCH_DIR}/include ${SOUNDTOUCH_DIR}/source)
set(SOUNDTOUCH_SOURCES
    ${SOUNDTOUCH_DIR}/source/AAFilter.cpp
    ${SOUNDTOUCH_DIR}/source/FIFOSampleBuffer.cpp
    ${SOUNDTOUCH_DIR}/source/FIRFilter.cpp
    ${SOUNDTOUCH_DIR}/source/InterpolateCubic.cpp
    ${SOUNDTOUCH_DIR}/source/InterpolateLinear.cpp
    ${SOUNDTOUCH_DIR}/source/InterpolateShannon.cpp
    ${SOUNDTOUCH_DIR}/source/PeakFinder.cpp
    ${SOUNDTOUCH_DIR}/source/RateTransposer.cpp
    ${SOUNDTOUCH_DIR}/source/SoundTouch.cpp
    ${SOUNDTOUCH_DIR}/source/TDStretch.cpp
    ${SOUNDTOUCH_DIR}/source/cpu_detect_x86.cpp
)

# rcheevos library for RetroAchievements
set(RCHEEVOS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/rcheevos)
include_directories(${RCHEEVOS_DIR}/include)
include_directories(${RCHEEVOS_DIR}/src/rhash)
include_directories(${RCHEEVOS_DIR}/src)

set(RCHEEVOS_SOURCES
    ${RCHEEVOS_DIR}/src/rcheevos/alloc.c
    ${RCHEEVOS_DIR}/src/rcheevos/condition.c
    ${RCHEEVOS_DIR}/src/rcheevos/condset.c
    ${RCHEEVOS_DIR}/src/rcheevos/consoleinfo.c
    ${RCHEEVOS_DIR}/src/rcheevos/format.c
    ${RCHEEVOS_DIR}/src/rcheevos/lboard.c
    ${RCHEEVOS_DIR}/src/rcheevos/memref.c
    ${RCHEEVOS_DIR}/src/rcheevos/operand.c
    ${RCHEEVOS_DIR}/src/rcheevos/richpresence.c
    ${RCHEEVOS_DIR}/src/rcheevos/runtime.c
    ${RCHEEVOS_DIR}/src/rcheevos/runtime_progress.c
    ${RCHEEVOS_DIR}/src/rcheevos/trigger.c
    ${RCHEEVOS_DIR}/src/rcheevos/value.c
    ${RCHEEVOS_DIR}/src/rhash/md5.c
    ${RCHEEVOS_DIR}/src/rhash/hash.c
    ${RCHEEVOS_DIR}/src/rhash/hash_rom.c
    ${RCHEEVOS_DIR}/src/rhash/hash_disc.c
    ${RCHEEVOS_DIR}/src/rhash/hash_zip.c
    ${RCHEEVOS_DIR}/src/rhash/hash_encrypted.c
    ${RCHEEVOS_DIR}/src/rhash/cdreader.c
    ${RCHEEVOS_DIR}/src/rhash/aes.c
    ${RCHEEVOS_DIR}/src/rc_compat.c
    ${RCHEEVOS_DIR}/src/rc_util.c
    ${RCHEEVOS_DIR}/src/rc_version.c
    ${RCHEEVOS_DIR}/src/rc_libretro.c
)

set (LIBRETRO_COMMON
        libretro/libretro-common/vfs/vfs_implementation.c
        libretro/libretro-common/string/stdstring.c
        libretro/libretro-common/encodings/encoding_utf.c
        libretro/libretro-common/file/file_path.c
        libretro/libretro-common/time/rtime.c
)

add_library(libretrodroid SHARED
        libretrodroidjni.h
        libretrodroidjni.cpp
        libretrodroid.h
        libretrodroid.cpp
        stateloadpolicy.h
        stateloadpolicy.cpp
        stateloadpolicy_test.h
        stateloadpolicy_test.cpp
        log.h
        core.h
        core.cpp
        video.h
        video.cpp
        immersivemode.h
        immersivemode.cpp
        backgroundframe.h
        backgroundframe.cpp
        videolayout.h
        videolayout.cpp
        renderers/renderer.h
        renderers/renderer.cpp
        renderers/es3/es3utils.h
        renderers/es3/es3utils.cpp
        renderers/es3/framebufferrenderer.h
        renderers/es3/framebufferrenderer.cpp
        renderers/es2/imagerendereres2.h
        renderers/es2/imagerendereres2.cpp
        renderers/es3/imagerendereres3.h
        renderers/es3/imagerendereres3.cpp
        audio.h
        audio.cpp
        resamplers/resampler.h
        resamplers/linearresampler.h
        resamplers/linearresampler.cpp
        resamplers/sincresampler.h
        resamplers/sincresampler.cpp
        fpssync.h
        fpssync.cpp
        environment.h
        environment.cpp
        input.h
        input.cpp
        shadermanager.h
        shadermanager.cpp
        rumble.h
        rumble.cpp
        rumblestate.h
        rumblestate.cpp
        utils/javautils.h
        utils/javautils.cpp
        utils/utils.cpp
        utils/utils.h
        utils/jnistring.h
        utils/jnistring.cpp
        utils/libretrodroidexception.h
        utils/libretrodroidexception.cpp
        utils/rect.h
        utils/rect.cpp
        errorcodes.h
        errorcodes.cpp
        vfs/vfs.h
        vfs/vfs.cpp
        vfs/vfsfile.h
        vfs/vfsfile.cpp
        vfs/fdwrapper.h
        vfs/fdwrapper.cpp
        microphone/microphone.h
        microphone/microphone.cpp
        microphone/microphoneinterface.h
        microphone/microphoneinterface.cpp
        rewindbuffer.h
        rewindbuffer.cpp
        achievements.h
        achievements.cpp
        achievements_test.h
        achievements_test.cpp
        ${LIBRETRO_COMMON}
        ${RCHEEVOS_SOURCES}
        ${SOUNDTOUCH_SOURCES}
        rcheevos_stubs.c
)

# Support 16k pages for Android 15
target_link_options(libretrodroid PRIVATE "-Wl,-z,max-page-size=16384")

# add lib dependencies
target_link_libraries(libretrodroid
                      android
                      log 
                      EGL
                      oboe
                      GLESv3
)
