/*
 *     Copyright (C) 2020  Filippo Scognamiglio
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

#define MODULE_NAME_CORE "Libretro Core"

#include <utility>
#include <vector>
#include <string>
#include <cstring>
#include <cmath>
#include <EGL/egl.h>
#include <unordered_map>

#include "../../libretro-common/include/libretro.h"
#include "log.h"
#include "environment.h"
#include "vfs/vfs.h"
#include "microphone/microphoneinterface.h"

void Environment::initialize(
    const std::string &requiredSystemDirectory,
    const std::string &requiredSavesDirectory,
    retro_hw_get_current_framebuffer_t required_callback_get_current_framebuffer
) {
    callback_get_current_framebuffer = required_callback_get_current_framebuffer;
    systemDirectory = requiredSystemDirectory;
    savesDirectory = requiredSavesDirectory;
}

void Environment::deinitialize() {
    callback_get_current_framebuffer = nullptr;
    hw_context_reset = nullptr;
    hw_context_destroy = nullptr;

    retro_disk_control_callback = nullptr;

    savesDirectory = std::string();
    systemDirectory = std::string();
    language = RETRO_LANGUAGE_ENGLISH;

    pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    useHWAcceleration = false;
    useDepth = false;
    useStencil = false;
    bottomLeftOrigin = false;
    screenRotation = 0;

    gameGeometryUpdated = false;
    gameGeometryWidth = 0;
    gameGeometryHeight = 0;
    gameGeometryAspectRatio = -1.0f;

    gameTimingUpdated = false;
    gameTimingFps = 0.0;
    gameTimingSampleRate = 0.0;

    memoryDescriptors.clear();
    memoryMapStorage.descriptors = nullptr;
    memoryMapStorage.num_descriptors = 0;
    hasMemoryMap = false;

    rumbleStates.fill(libretrodroid::RumbleState());
}

void Environment::updateVariable(const std::string& key, const std::string& value) {
    auto current = variables[key];
    current.key = key;

    if (value != current.value) {
        current.value = value;
        variables[key] = current;
        dirtyVariables = true;
    }
}

bool Environment::environment_handle_set_variables(const struct retro_variable* received) {
    unsigned count = 0;
    while (received[count].key != nullptr) {
        LOGD("Core registered variable: %s = %s", received[count].key, received[count].value);

        std::string key(received[count].key);
        std::string description(received[count].value);
        std::string value(received[count].value);

        auto firstValueStart = value.find(';') + 2;
        auto firstValueEnd = value.find('|', firstValueStart);
        value = value.substr(firstValueStart, firstValueEnd - firstValueStart);

        auto currentVariable = variables[key];
        currentVariable.key = key;
        currentVariable.description = description;

        if (currentVariable.value.empty()) {
            currentVariable.value = value;
        }

        variables[key] = currentVariable;
        LOGD("Assigning variable %s: %s", currentVariable.key.c_str(), currentVariable.value.c_str());

        count++;
    }

    return true;
}

bool Environment::environment_handle_get_variable(struct retro_variable* requested) {
    auto foundVariable = variables.find(std::string(requested->key));

    if (foundVariable == variables.end()) {
        LOGD("Variable GET miss: %s (not found)", requested->key);
        return false;
    }

    requested->value = foundVariable->second.value.c_str();
    LOGD("Variable GET hit: %s = %s", requested->key, requested->value);
    return true;
}

bool Environment::environment_handle_set_controller_info(const struct retro_controller_info* received) {
    controllers.clear();

    unsigned player = 0;
    while (received[player].types != nullptr) {

        auto currentPlayer = received[player];

        controllers.emplace_back();

        unsigned controller = 0;
        while (controller < currentPlayer.num_types && currentPlayer.types[controller].desc != nullptr) {
            auto currentController = currentPlayer.types[controller];
            LOGD("Received controller for player %d: %d %s", player, currentController.id, currentController.desc);

            controllers[player].push_back(Controller { currentController.id, currentController.desc });
            controller++;
        }

        player++;
    }

    return true;
}

bool Environment::environment_handle_set_hw_render(struct retro_hw_render_callback* hw_render_callback) {
    useHWAcceleration = true;
    useDepth = hw_render_callback->depth;
    useStencil = hw_render_callback->stencil;
    bottomLeftOrigin = hw_render_callback->bottom_left_origin;

    hw_context_destroy = hw_render_callback->context_destroy;
    hw_context_reset = hw_render_callback->context_reset;
    hw_render_callback->get_current_framebuffer = callback_get_current_framebuffer;
    hw_render_callback->get_proc_address = &eglGetProcAddress;

    return true;
}

bool Environment::environment_handle_get_vfs_interface(struct retro_vfs_interface_info* vfsInterfaceInfo) {
    if (!useVirtualFileSystem) {
        return false;
    }

    vfsInterfaceInfo->required_interface_version = libretrodroid::VFS::SUPPORTED_VERSION;
    vfsInterfaceInfo->iface = libretrodroid::VFS::getInterface();
    return true;
}

bool Environment::environment_handle_get_microphone_interface(struct retro_microphone_interface* microphone_interface) {
    if (!enableMicrophone) {
        return false;
    }

    *microphone_interface = *libretrodroid::MicrophoneInterface::getInterface();
    return true;
}

void Environment::callback_retro_log(enum retro_log_level level, const char *fmt, ...) {
    va_list argptr;
    va_start(argptr, fmt);

    switch (level) {
#if VERBOSE_LOGGING
        case RETRO_LOG_DEBUG:
            __android_log_vprint(ANDROID_LOG_DEBUG, MODULE_NAME_CORE, fmt, argptr);
            break;
#endif
        case RETRO_LOG_INFO:
            __android_log_vprint(ANDROID_LOG_INFO, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_WARN:
            __android_log_vprint(ANDROID_LOG_WARN, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_ERROR:
            __android_log_vprint(ANDROID_LOG_ERROR, MODULE_NAME_CORE, fmt, argptr);
            break;
        default:
            // Log nothing in here.
            break;
    }
}

bool Environment::callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    return Environment::getInstance().handle_callback_set_rumble_state(port, effect, strength);
}

bool Environment::handle_callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    LOGV("Setting rumble strength for port %i to %i", port, strength);
    if (port < 0 || port > 3) return false;

    if (effect == RETRO_RUMBLE_STRONG) {
        rumbleStates[port].strengthStrong = strength;
    } else if (effect == RETRO_RUMBLE_WEAK) {
        rumbleStates[port].strengthWeak = strength;
    }

    return true;
}

bool Environment::callback_environment(unsigned cmd, void *data) {
    return Environment::getInstance().handle_callback_environment(cmd, data);
}

bool Environment::handle_callback_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *((bool*) data) = true;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            LOGD("Called SET_PIXEL_FORMAT");
            pixelFormat = *static_cast<enum retro_pixel_format *>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
            LOGD("Called SET_INPUT_DESCRIPTORS");
            return false;

        case RETRO_ENVIRONMENT_GET_VARIABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE");
            return environment_handle_get_variable(static_cast<struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            LOGD("Called RETRO_ENVIRONMENT_SET_VARIABLES");
            return environment_handle_set_variables(static_cast<const struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE. Is dirty?: %d", dirtyVariables);
            *((bool*) data) = dirtyVariables;
            dirtyVariables = false;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: {
            LOGD("Called RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER");
            *((unsigned*) data) = retro_hw_context_type::RETRO_HW_CONTEXT_OPENGLES3;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_RENDER");
            return environment_handle_set_hw_render(static_cast<struct retro_hw_render_callback*>(data));

        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE");
            ((struct retro_rumble_interface*) data)->set_rumble_state = &callback_set_rumble_state;
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LOG_INTERFACE");
            ((struct retro_log_callback*) data)->log = &callback_retro_log;
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY");
            *(const char**) data = savesDirectory.c_str();
            return !savesDirectory.empty();

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY");
            *(const char**) data = systemDirectory.c_str();
            return !systemDirectory.empty();

        case RETRO_ENVIRONMENT_SET_ROTATION: {
            LOGD("Called RETRO_ENVIRONMENT_SET_ROTATION");
            unsigned screenRotationIndex = (*static_cast<unsigned*>(data));
            screenRotation = screenRotationIndex * (float) (-M_PI / 2.0);
            screenRotationUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_SET_ROTATION");
            retro_disk_control_callback = static_cast<struct retro_disk_control_callback*>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_PERF_INTERFACE");
            return false;

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            LOGD("Called RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO");
            auto *avInfo = static_cast<const struct retro_system_av_info *>(data);
            gameGeometryHeight = avInfo->geometry.base_height;
            gameGeometryWidth = avInfo->geometry.base_width;
            gameGeometryAspectRatio = avInfo->geometry.aspect_ratio;
            gameGeometryUpdated = true;

            if (avInfo->timing.fps > 0 && avInfo->timing.sample_rate > 0) {
                if (avInfo->timing.fps != gameTimingFps
                    || avInfo->timing.sample_rate != gameTimingSampleRate) {
                    gameTimingFps = avInfo->timing.fps;
                    gameTimingSampleRate = avInfo->timing.sample_rate;
                    gameTimingUpdated = true;
                    LOGI("Timing updated: fps=%f sample_rate=%f", gameTimingFps, gameTimingSampleRate);
                }
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            LOGD("Called RETRO_ENVIRONMENT_SET_GEOMETRY");
            auto *geometry = static_cast<const struct retro_game_geometry *>(data);
            gameGeometryHeight = geometry->base_height;
            gameGeometryWidth = geometry->base_width;
            gameGeometryAspectRatio = geometry->aspect_ratio;
            gameGeometryUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            LOGD("Called RETRO_ENVIRONMENT_SET_CONTROLLER_INFO");
            return environment_handle_set_controller_info(static_cast<const struct retro_controller_info*>(data));

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE");
            return false;

        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LANGUAGE");
            *((unsigned*) data) = language;
            return true;

        case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VFS_INTERFACE");
            return environment_handle_get_vfs_interface(static_cast<struct retro_vfs_interface_info*>(data));

        case RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE");
            return environment_handle_get_microphone_interface(static_cast<struct retro_microphone_interface*>(data));

        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
            LOGD("Called RETRO_ENVIRONMENT_SET_MEMORY_MAPS");
            auto* mmap = static_cast<const struct retro_memory_map*>(data);
            if (mmap && mmap->num_descriptors > 0) {
                // Deep copy the memory map - the core's pointer becomes invalid after this callback
                memoryDescriptors.clear();
                memoryDescriptors.reserve(mmap->num_descriptors);
                for (unsigned i = 0; i < mmap->num_descriptors; i++) {
                    memoryDescriptors.push_back(mmap->descriptors[i]);
                }
                memoryMapStorage.descriptors = memoryDescriptors.data();
                memoryMapStorage.num_descriptors = mmap->num_descriptors;
                hasMemoryMap = true;
                LOGI("Memory map set with %u descriptors", mmap->num_descriptors);
            }
            return true;
        }

        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;

        default:
            LOGD("callback environment has been called: %u", cmd);
            return false;
    }
}

void Environment::setLanguage(const std::string& androidLanguage) {
    std::unordered_map<std::string, unsigned> languages {
            { "en", RETRO_LANGUAGE_ENGLISH },
            { "jp", RETRO_LANGUAGE_JAPANESE },
            { "fr", RETRO_LANGUAGE_FRENCH },
            { "es", RETRO_LANGUAGE_SPANISH },
            { "de", RETRO_LANGUAGE_GERMAN },
            { "it", RETRO_LANGUAGE_ITALIAN },
            { "nl", RETRO_LANGUAGE_DUTCH },
            { "pt", RETRO_LANGUAGE_PORTUGUESE_PORTUGAL },
            { "ru", RETRO_LANGUAGE_RUSSIAN },
            { "ko", RETRO_LANGUAGE_KOREAN },
            { "zh", RETRO_LANGUAGE_CHINESE_TRADITIONAL },
            { "eo", RETRO_LANGUAGE_ESPERANTO },
            { "pl", RETRO_LANGUAGE_POLISH },
            { "vi", RETRO_LANGUAGE_VIETNAMESE },
            { "ar", RETRO_LANGUAGE_ARABIC },
            { "el", RETRO_LANGUAGE_GREEK },
            { "tr", RETRO_LANGUAGE_TURKISH }
    };

    if (languages.find(androidLanguage) != languages.end()) {
        language = languages[androidLanguage];
    }
}

retro_hw_context_reset_t Environment::getHwContextReset() const {
    return hw_context_reset;
}

retro_hw_context_reset_t Environment::getHwContextDestroy() const {
    return hw_context_destroy;
}

struct retro_disk_control_callback* Environment::getRetroDiskControlCallback() const {
    return retro_disk_control_callback;
}

int Environment::getPixelFormat() const {
    return pixelFormat;
}

bool Environment::isUseHwAcceleration() const {
    return useHWAcceleration;
}

bool Environment::isUseDepth() const {
    return useDepth;
}

bool Environment::isUseStencil() const {
    return useStencil;
}

bool Environment::isBottomLeftOrigin() const {
    return bottomLeftOrigin;
}

float Environment::getScreenRotation() const {
    if (manualRotation >= 0) {
        return manualRotation;
    }
    return screenRotation;
}

void Environment::setManualRotation(int degrees) {
    if (degrees < 0) {
        manualRotation = -1;
    } else {
        manualRotation = degrees * (float) (-M_PI / 180.0);
    }
    screenRotationUpdated = true;
}

bool Environment::isGameGeometryUpdated() const {
    return gameGeometryUpdated;
}

void Environment::clearGameGeometryUpdated() {
    gameGeometryUpdated = false;
}

unsigned int Environment::getGameGeometryWidth() const {
    return gameGeometryWidth;
}

unsigned int Environment::getGameGeometryHeight() const {
    return gameGeometryHeight;
}

float Environment::getGameGeometryAspectRatio() const {
    return gameGeometryAspectRatio;
}

double Environment::getGameTimingFps() const {
    return gameTimingFps;
}

double Environment::getGameTimingSampleRate() const {
    return gameTimingSampleRate;
}

bool Environment::isGameTimingUpdated() const {
    return gameTimingUpdated;
}

void Environment::clearGameTimingUpdated() {
    gameTimingUpdated = false;
}

const std::vector<struct Variable> Environment::getVariables() const {
    std::vector<struct Variable> result;

    std::for_each(
        variables.begin(),
        variables.end(),
        [&](std::pair<std::string, struct Variable> item) {
            result.push_back(item.second);
        }
    );

    std::sort(
        result.begin(),
        result.end(),
        [](struct Variable v1, struct Variable v2) {
            return v1.key < v2.key;
        }
    );

    return result;
}

const std::vector<std::vector<struct Controller>> &Environment::getControllers() const {
    return controllers;
}

float Environment::retrieveGameSpecificAspectRatio() {
    if (aspectRatioOverride > 0) {
        return aspectRatioOverride;
    }

    if (getGameGeometryAspectRatio() > 0) {
        return getGameGeometryAspectRatio();
    }

    if (getGameGeometryWidth() > 0 && getGameGeometryHeight() > 0) {
        return (float) getGameGeometryWidth() / (float) getGameGeometryHeight();
    }

    return -1.0f;
}

void Environment::setAspectRatioOverride(float ratio) {
    aspectRatioOverride = ratio;
}

void Environment::clearAspectRatioOverride() {
    aspectRatioOverride = -1.0f;
}

bool Environment::isScreenRotationUpdated() const {
    return screenRotationUpdated;
}

void Environment::clearScreenRotationUpdated() {
    screenRotationUpdated = false;
}

std::array<libretrodroid::RumbleState, 4>& Environment::getLastRumbleStates() {
    return rumbleStates;
}

void Environment::setEnableVirtualFileSystem(bool value) {
    this->useVirtualFileSystem = value;
}

void Environment::setEnableMicrophone(bool value) {
    this->enableMicrophone = value;
}

const struct retro_memory_map* Environment::getMemoryMap() const {
    return hasMemoryMap ? &memoryMapStorage : nullptr;
}
