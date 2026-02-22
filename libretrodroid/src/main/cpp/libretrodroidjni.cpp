/*
 *     Copyright (C) 2021  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <jni.h>

#include <EGL/egl.h>

#include <memory>
#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <optional>

#include "libretrodroid.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "errorcodes.h"
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/jnistring.h"
#include "rewindbuffer.h"
#include "achievements_test.h"
#include <rc_hash.h>

namespace libretrodroid {

extern "C" {
#include "utils/utils.h"
#include "../../libretro-common/include/libretro.h"
#include "utils/libretrodroidexception.h"
}

extern "C" {

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_availableDisks(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().availableDisks();
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_currentDisk(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().currentDisk();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_changeDisk(
    JNIEnv* env,
    jclass obj,
    jint index
) {
    return LibretroDroid::getInstance().changeDisk(index);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_updateVariable(
    JNIEnv* env,
    jclass obj,
    jobject variable
) {
    Variable v = JavaUtils::variableFromJava(env, variable);
    Environment::getInstance().updateVariable(v.key, v.value);
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getVariables(
    JNIEnv* env,
    jclass obj
) {
    jclass variableClass = env->FindClass("com/swordfish/libretrodroid/Variable");
    jmethodID variableMethodID = env->GetMethodID(variableClass, "<init>", "()V");

    auto variables = Environment::getInstance().getVariables();
    jobjectArray result = env->NewObjectArray(variables.size(), variableClass, nullptr);

    for (int i = 0; i < variables.size(); i++) {
        jobject jVariable = env->NewObject(variableClass, variableMethodID);

        jfieldID jKeyField = env->GetFieldID(variableClass, "key", "Ljava/lang/String;");
        jfieldID jValueField = env->GetFieldID(variableClass, "value", "Ljava/lang/String;");
        jfieldID jDescriptionField = env->GetFieldID(
            variableClass,
            "description",
            "Ljava/lang/String;"
        );

        env->SetObjectField(jVariable, jKeyField, env->NewStringUTF(variables[i].key.data()));
        env->SetObjectField(jVariable, jValueField, env->NewStringUTF(variables[i].value.data()));
        env->SetObjectField(
            jVariable,
            jDescriptionField,
            env->NewStringUTF(variables[i].description.data()));

        env->SetObjectArrayElement(result, i, jVariable);
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getControllers(
    JNIEnv* env,
    jclass obj
) {
    jclass variableClass = env->FindClass("[Lcom/swordfish/libretrodroid/Controller;");

    auto controllers = Environment::getInstance().getControllers();
    jobjectArray result = env->NewObjectArray(controllers.size(), variableClass, nullptr);

    for (int i = 0; i < controllers.size(); i++) {
        jclass variableClass2 = env->FindClass("com/swordfish/libretrodroid/Controller");
        jobjectArray controllerArray = env->NewObjectArray(
            controllers[i].size(),
            variableClass2,
            nullptr
        );
        jmethodID variableMethodID = env->GetMethodID(variableClass2, "<init>", "()V");

        for (int j = 0; j < controllers[i].size(); j++) {
            jobject jController = env->NewObject(variableClass2, variableMethodID);

            jfieldID jIdField = env->GetFieldID(variableClass2, "id", "I");
            jfieldID jDescriptionField = env->GetFieldID(
                variableClass2,
                "description",
                "Ljava/lang/String;"
            );

            env->SetIntField(jController, jIdField, (int) controllers[i][j].id);
            env->SetObjectField(
                jController,
                jDescriptionField,
                env->NewStringUTF(controllers[i][j].description.data()));

            env->SetObjectArrayElement(controllerArray, j, jController);
        }

        env->SetObjectArrayElement(result, i, controllerArray);
    }
    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setControllerType(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint type
) {
    LibretroDroid::getInstance().setControllerType(port, type);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeState(
    JNIEnv* env,
    jclass obj,
    jbyteArray state
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(state, &isCopy);
        jsize size = env->GetArrayLength(state);

        bool result = LibretroDroid::getInstance().unserializeState(data, size);
        env->ReleaseByteArrayElements(state, data, JNI_ABORT);

        return result ? JNI_TRUE : JNI_FALSE;

    } catch (std::exception &exception) {
        LOGE("Error in unserializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeState(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeState();

        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, data);

        delete[] data;

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_captureRawFrame(
    JNIEnv* env,
    jclass obj
) {
    int w, h;
    auto pixels = LibretroDroid::getInstance().captureRawFrame(w, h);
    if (pixels.empty()) return nullptr;

    jsize totalSize = 8 + (jsize)pixels.size();
    jbyteArray result = env->NewByteArray(totalSize);
    int32_t dims[2] = { w, h };
    env->SetByteArrayRegion(result, 0, 8, reinterpret_cast<jbyte*>(dims));
    env->SetByteArrayRegion(result, 8, (jsize)pixels.size(),
        reinterpret_cast<const jbyte*>(pixels.data()));
    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setCheat(
    JNIEnv* env,
    jclass obj,
    jint index,
    jboolean enabled,
    jstring code
) {
    try {
        auto codeString = JniString(env, code);
        LibretroDroid::getInstance().setCheat(index, enabled, codeString.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in setCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resetCheat(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resetCheat();
    } catch (std::exception &exception) {
        LOGE("Error in resetCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeSRAM(
    JNIEnv* env,
    jclass obj,
    jbyteArray sram
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(sram, &isCopy);
        jsize size = env->GetArrayLength(sram);

        LibretroDroid::getInstance().unserializeSRAM(data, size);

        env->ReleaseByteArrayElements(sram, data, JNI_ABORT);

    } catch (std::exception &exception) {
        LOGE("Error in unserializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeSRAM(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeSRAM();

        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, (jbyte *) data);

        delete[] data;

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getMemoryData(
    JNIEnv* env,
    jclass obj,
    jint memoryType
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().getMemoryData(memoryType);
        if (data == nullptr) {
            return nullptr;
        }

        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, (jbyte*) data);
        delete[] data;
        return result;

    } catch (std::exception &exception) {
        LOGE("Error in getMemoryData: %s", exception.what());
    }

    return nullptr;
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getMemorySize(
    JNIEnv* env,
    jclass obj,
    jint memoryType
) {
    try {
        return LibretroDroid::getInstance().getMemorySize(memoryType);
    } catch (std::exception &exception) {
        LOGE("Error in getMemorySize: %s", exception.what());
    }
    return 0;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_reset(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().reset();
    } catch (std::exception &exception) {
        LOGE("Error in clear: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceChanged(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height
) {
    LibretroDroid::getInstance().onSurfaceChanged(width, height);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceCreated(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().onSurfaceCreated();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onMotionEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint source,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onMotionEvent(port, source, xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onTouchEvent(
    JNIEnv* env,
    jclass obj,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onTouchEvent(xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onKeyEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint action,
    jint keyCode
) {
    LibretroDroid::getInstance().onKeyEvent(port, action, keyCode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_create(
    JNIEnv* env,
    jclass obj,
    jint GLESVersion,
    jstring soFilePath,
    jstring systemDir,
    jstring savesDir,
    jobjectArray jVariables,
    jobject shaderConfig,
    jfloat refreshRate,
    jboolean preferLowLatencyAudio,
    jboolean forceSoftwareTiming,
    jboolean enableVirtualFileSystem,
    jboolean enableMicrophone,
    jboolean skipDuplicateFrames,
    jobject immersiveMode,
    jstring language
) {
    try {
        auto corePath = JniString(env, soFilePath);
        auto deviceLanguage = JniString(env, language);
        auto systemDirectory = JniString(env, systemDir);
        auto savesDirectory = JniString(env, savesDir);

        std::vector<Variable> variables;
        int size = env->GetArrayLength(jVariables);
        for (int i = 0; i < size; i++) {
            auto jVariable = (jobject) env->GetObjectArrayElement(jVariables, i);
            auto variable = JavaUtils::variableFromJava(env, jVariable);
            variables.push_back(variable);
        }

        std::optional<ImmersiveMode::Config> parsedConfig = std::nullopt;
        if (immersiveMode != nullptr) {
            jclass configClass = env->GetObjectClass(immersiveMode);
            jfieldID downscaledWidthField = env->GetFieldID(configClass, "downscaledWidth", "I");
            jfieldID downscaledHeightField = env->GetFieldID(configClass, "downscaledHeight", "I");
            jfieldID blurMaskSizeField = env->GetFieldID(configClass, "blurMaskSize", "I");
            jfieldID blurBrightnessField = env->GetFieldID(configClass, "blurBrightness", "F");
            jfieldID blurSkipUpdateField = env->GetFieldID(configClass, "blurSkipUpdate", "I");
            jfieldID blendFactorField = env->GetFieldID(configClass, "blendFactor", "F");

            ImmersiveMode::Config config {};
            config.downscaledWidth = env->GetIntField(immersiveMode, downscaledWidthField);
            config.downscaledHeight = env->GetIntField(immersiveMode, downscaledHeightField);
            config.blurMaskSize = env->GetIntField(immersiveMode, blurMaskSizeField);
            config.blurBrightness = env->GetFloatField(immersiveMode, blurBrightnessField);
            config.blurSkipUpdate = env->GetIntField(immersiveMode, blurSkipUpdateField);
            config.blendFactor = env->GetFloatField(immersiveMode, blendFactorField);
            parsedConfig = config;
        }

        LibretroDroid::getInstance().create(
            GLESVersion,
            corePath.stdString(),
            systemDirectory.stdString(),
            savesDirectory.stdString(),
            variables,
            JavaUtils::shaderFromJava(env, shaderConfig),
            refreshRate,
            preferLowLatencyAudio,
            forceSoftwareTiming,
            enableVirtualFileSystem,
            enableMicrophone,
            skipDuplicateFrames,
            parsedConfig,
            deviceLanguage.stdString()
        );

    } catch (libretrodroid::LibretroDroidError& exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
    } catch (std::exception &exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_LIBRARY);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromPath(
    JNIEnv* env,
    jclass obj,
    jstring gameFilePath
) {
    auto gamePath = JniString(env, gameFilePath);

    try {
        LibretroDroid::getInstance().loadGameFromPath(gamePath.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromPath: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromBytes(
    JNIEnv* env,
    jclass obj,
    jbyteArray gameFileBytes
) {
    try {
        size_t size = env->GetArrayLength(gameFileBytes);
        auto* data = new int8_t[size];
        env->GetByteArrayRegion(
            gameFileBytes,
            0,
            size,
            reinterpret_cast<int8_t*>(data)
        );
        LibretroDroid::getInstance().loadGameFromBytes(data, size);
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromBytes: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromVirtualFiles(
        JNIEnv* env,
        jclass obj,
        jobject virtualFileList
) {

    try {
        jmethodID getVirtualFileMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getVirtualPath",
                "()Ljava/lang/String;"
        );
        jmethodID getFileDescriptorMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getFileDescriptor",
                "()I"
        );

        std::vector<VFSFile> virtualFiles;

        JavaUtils::forEachOnJavaIterable(env, virtualFileList, [&](jobject item) {
            JniString virtualFileName(env,(jstring) env->CallObjectMethod(
                item,
                getVirtualFileMethodID
            ));

            int fileDescriptor = env->CallIntMethod(item, getFileDescriptorMethodID);
            virtualFiles.emplace_back(VFSFile(virtualFileName.stdString(), fileDescriptor));
        });

        LibretroDroid::getInstance().loadGameFromVirtualFiles(std::move(virtualFiles));
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromDescriptors: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_destroy(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().destroy();
    } catch (std::exception &exception) {
        LOGE("Error in destroy: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resume(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resume();
    } catch (std::exception &exception) {
        LOGE("Error in resume: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_pause(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().pause();
    } catch (std::exception &exception) {
        LOGE("Error in pause: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_step(
    JNIEnv* env,
    jclass obj,
    jobject glRetroView
) {
    LibretroDroid::getInstance().step();

    if (LibretroDroid::getInstance().requiresVideoRefresh()) {
        LibretroDroid::getInstance().clearRequiresVideoRefresh();
        jclass cls = env->GetObjectClass(glRetroView);
        jmethodID requestAspectRatioUpdate = env->GetMethodID(cls, "refreshAspectRatio", "()V");
        env->CallVoidMethod(glRetroView, requestAspectRatioUpdate);
    }

    if (LibretroDroid::getInstance().isRumbleEnabled()) {
        LibretroDroid::getInstance().handleRumbleUpdates([&](int port, float weak, float strong) {
            jclass cls = env->GetObjectClass(glRetroView);
            jmethodID sendRumbleStrengthMethodID = env->GetMethodID(cls, "sendRumbleEvent", "(IFF)V");
            env->CallVoidMethod(glRetroView, sendRumbleStrengthMethodID, port, weak, strong);
        });
    }

    LibretroDroid::getInstance().handleAchievementUnlocks([&](uint32_t achievementId) {
        jclass cls = env->GetObjectClass(glRetroView);
        jmethodID onAchievementUnlockedMethodID = env->GetMethodID(cls, "onAchievementUnlocked", "(J)V");
        env->CallVoidMethod(glRetroView, onAchievementUnlockedMethodID, static_cast<jlong>(achievementId));
    });
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_renderFrameOnly(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().renderFrameOnly();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRumbleEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setRumbleEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFrameSpeed(
    JNIEnv* env,
    jclass obj,
    jint speed
) {
    LibretroDroid::getInstance().setFrameSpeed(speed);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAudioEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setAudioEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setShaderConfig(
    JNIEnv* env,
    jclass obj,
    jobject shaderConfig
) {
    LibretroDroid::getInstance().setShaderConfig(JavaUtils::shaderFromJava(env, shaderConfig));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFilterMode(
    JNIEnv* env,
    jclass obj,
    jint mode
) {
    LibretroDroid::getInstance().setFilterMode(mode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setIntegerScaling(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setIntegerScaling(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setBlackFrameInsertion(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setBlackFrameInsertion(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_renderBlackFrame(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().renderBlackFrame();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setBackgroundFrame(
    JNIEnv* env,
    jclass obj,
    jbyteArray rgbaData,
    jint width,
    jint height
) {
    jbyte* data = env->GetByteArrayElements(rgbaData, nullptr);
    LibretroDroid::getInstance().setBackgroundFrame(
        reinterpret_cast<const uint8_t*>(data), width, height
    );
    env->ReleaseByteArrayElements(rgbaData, data, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_clearBackgroundFrame(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().clearBackgroundFrame();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setViewport(
    JNIEnv* env,
    jclass obj,
    jfloat x,
    jfloat y,
    jfloat width,
    jfloat height
) {
    LibretroDroid::getInstance().setViewport(Rect(x, y, width, height));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setTextureCrop(
    JNIEnv* env,
    jclass obj,
    jfloat left,
    jfloat top,
    jfloat right,
    jfloat bottom
) {
    LibretroDroid::getInstance().setTextureCrop(left, top, right, bottom);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_refreshAspectRatio(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().refreshAspectRatio();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAspectRatioOverride(
    JNIEnv* env,
    jclass obj,
    jfloat ratio
) {
    LibretroDroid::getInstance().setAspectRatioOverride(ratio);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRotation(
    JNIEnv* env,
    jclass obj,
    jint degrees
) {
    Environment::getInstance().setManualRotation(degrees);
}

static std::unique_ptr<RewindBuffer> rewindBuffer = nullptr;
static std::vector<uint8_t> rewindTempBuffer;

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_initRewindBuffer(
    JNIEnv* env,
    jclass obj,
    jint slotCount,
    jint maxStateSize
) {
    rewindBuffer = std::make_unique<RewindBuffer>(slotCount, maxStateSize);
    rewindTempBuffer.resize(maxStateSize);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_captureRewindState(
    JNIEnv* env,
    jclass obj
) {
    if (!rewindBuffer) {
        return JNI_FALSE;
    }

    try {
        auto [data, size] = LibretroDroid::getInstance().serializeState();
        bool pushed = rewindBuffer->push(reinterpret_cast<uint8_t*>(data), size);
        delete[] data;
        if (!pushed) {
            LOGW("Rewind state too large (%zu bytes), skipping capture", size);
        }
        return pushed ? JNI_TRUE : JNI_FALSE;
    } catch (std::exception &exception) {
        LOGE("Error in captureRewindState: %s", exception.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_rewindFrame(
    JNIEnv* env,
    jclass obj
) {
    if (!rewindBuffer) {
        return JNI_FALSE;
    }

    try {
        size_t size = 0;
        if (!rewindBuffer->pop(rewindTempBuffer.data(), &size)) {
            return JNI_FALSE;
        }

        bool result = LibretroDroid::getInstance().unserializeState(
            reinterpret_cast<int8_t*>(rewindTempBuffer.data()),
            size
        );
        return result ? JNI_TRUE : JNI_FALSE;
    } catch (std::exception &exception) {
        LOGE("Error in rewindFrame: %s", exception.what());
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_clearRewindBuffer(
    JNIEnv* env,
    jclass obj
) {
    if (rewindBuffer) {
        rewindBuffer->clear();
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_destroyRewindBuffer(
    JNIEnv* env,
    jclass obj
) {
    rewindBuffer.reset();
    rewindTempBuffer.clear();
    rewindTempBuffer.shrink_to_fit();
}

JNIEXPORT jfloat JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getRewindBufferUsage(
    JNIEnv* env,
    jclass obj
) {
    if (!rewindBuffer) {
        return 0.0f;
    }
    return rewindBuffer->getUsage();
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getRewindBufferValidCount(
    JNIEnv* env,
    jclass obj
) {
    if (!rewindBuffer) {
        return 0;
    }
    return static_cast<jint>(rewindBuffer->getValidCount());
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_initAchievements(
    JNIEnv* env,
    jclass obj,
    jobjectArray achievementArray,
    jint consoleId
) {
    std::vector<AchievementDef> achievements;

    jsize count = env->GetArrayLength(achievementArray);
    LOGI("initAchievements JNI called: count=%d, consoleId=%d", count, consoleId);
    if (count == 0) {
        LOGI("No achievements to initialize - empty array");
        return;
    }

    jclass achClass = env->FindClass("com/swordfish/libretrodroid/AchievementDef");
    jfieldID idField = env->GetFieldID(achClass, "id", "J");
    jfieldID memAddrField = env->GetFieldID(achClass, "memAddr", "Ljava/lang/String;");

    for (jsize i = 0; i < count; i++) {
        jobject achObj = env->GetObjectArrayElement(achievementArray, i);

        AchievementDef def;
        def.id = static_cast<uint32_t>(env->GetLongField(achObj, idField));

        jstring memAddr = static_cast<jstring>(env->GetObjectField(achObj, memAddrField));
        const char* memAddrStr = env->GetStringUTFChars(memAddr, nullptr);
        def.memAddr = memAddrStr;
        env->ReleaseStringUTFChars(memAddr, memAddrStr);

        achievements.push_back(def);
        env->DeleteLocalRef(achObj);
        env->DeleteLocalRef(memAddr);
    }

    LOGI("Initializing %zu achievements in native for console %d", achievements.size(), consoleId);
    LibretroDroid::getInstance().initAchievements(achievements, static_cast<uint32_t>(consoleId));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_clearAchievements(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().clearAchievements();
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_runAchievementTests(
    JNIEnv* env,
    jclass obj
) {
    test::AchievementTester tester;
    auto results = tester.runAllTests();

    int passed = 0;
    for (const auto& result : results) {
        if (result.passed) passed++;
    }

    return static_cast<jint>(passed);
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_computeRomHash(
    JNIEnv* env,
    jclass obj,
    jstring romPath,
    jint consoleId
) {
    if (romPath == nullptr) {
        LOGE("computeRomHash: null romPath");
        return nullptr;
    }

    JniString pathStr(env, romPath);
    const char* path = pathStr.stdString().c_str();

    char hash[33] = {0};
    int result = rc_hash_generate_from_file(hash, static_cast<uint32_t>(consoleId), path);

    if (result == 0) {
        LOGW("Failed to compute hash for %s (console %d)", path, consoleId);
        return nullptr;
    }

    LOGI("Computed hash for %s: %s", path, hash);
    return env->NewStringUTF(hash);
}

}

}
