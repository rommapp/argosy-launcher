/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#include "input.h"
#include "log.h"

#include <cmath>

#include <android/input.h>
#include <android/keycodes.h>

#include "../../libretro-common/include/libretro.h"

namespace libretrodroid {

int16_t Input::getInputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4 || port < 0) return 0;

    std::lock_guard<std::mutex> lock(inputMutex);
    switch (device) {
        case RETRO_DEVICE_JOYPAD: {
            if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
                int16_t result = 0;
                for (unsigned i = 0; i <= RETRO_DEVICE_ID_JOYPAD_R3; i++) {
                    if (getButtonState(port, i)) {
                        result |= (1 << i);
                    }
                }
                return result;
            }
            return getButtonState(port, id) ? 1 : 0;
        }

        case RETRO_DEVICE_ANALOG: {
            switch (index) {
                case RETRO_DEVICE_INDEX_ANALOG_LEFT:
                    switch (id) {
                        case RETRO_DEVICE_ID_ANALOG_X:
                            return (int16_t) (pads[port].joypadLeftXAxis * MAX_RANGE_MOTION);
                        case RETRO_DEVICE_ID_ANALOG_Y:
                            return (int16_t) (pads[port].joypadLeftYAxis * MAX_RANGE_MOTION);
                        default:
                            return 0;
                    }
                case RETRO_DEVICE_INDEX_ANALOG_RIGHT:
                    switch (id) {
                        case RETRO_DEVICE_ID_ANALOG_X:
                            return (int16_t) (pads[port].joypadRightXAxis * MAX_RANGE_MOTION);
                        case RETRO_DEVICE_ID_ANALOG_Y:
                            return (int16_t) (pads[port].joypadRightYAxis * MAX_RANGE_MOTION);
                        default:
                            return 0;
                    }
                default:
                    return 0;
            }
        }

        case RETRO_DEVICE_POINTER: {
            // TODO... Here we should hanlde multitouch...
            if (index > 0) {
                return 0;
            }

            switch (id) {
                case RETRO_DEVICE_ID_POINTER_PRESSED: {
                    bool isXActive = pads[port].pointerScreenXAxis >= 0;
                    bool isYActive = pads[port].pointerScreenYAxis >= 0;
                    return (int16_t) (isXActive && isYActive ? 1 : 0);
                }

                case RETRO_DEVICE_ID_POINTER_X:
                    return (int16_t) (2.0 * (pads[port].pointerScreenXAxis - 0.5f) * MAX_RANGE_MOTION);

                case RETRO_DEVICE_ID_POINTER_Y:
                    return (int16_t) (2.0 * (pads[port].pointerScreenYAxis - 0.5f) * MAX_RANGE_MOTION);

                default:
                    return 0;
            }
        }

        default:
            return 0;
    }
}

int Input::convertAndroidToLibretroKey(int keyCode) const {
    switch (keyCode) {
        case AKEYCODE_BUTTON_START:
            return RETRO_DEVICE_ID_JOYPAD_START;
        case AKEYCODE_BUTTON_SELECT:
            return RETRO_DEVICE_ID_JOYPAD_SELECT;
        case AKEYCODE_BUTTON_A:
            return RETRO_DEVICE_ID_JOYPAD_A;
        case AKEYCODE_BUTTON_X:
            return RETRO_DEVICE_ID_JOYPAD_X;
        case AKEYCODE_BUTTON_Y:
            return RETRO_DEVICE_ID_JOYPAD_Y;
        case AKEYCODE_BUTTON_B:
            return RETRO_DEVICE_ID_JOYPAD_B;
        case AKEYCODE_BUTTON_L1:
            return RETRO_DEVICE_ID_JOYPAD_L;
        case AKEYCODE_BUTTON_L2:
            return RETRO_DEVICE_ID_JOYPAD_L2;
        case AKEYCODE_BUTTON_R1:
            return RETRO_DEVICE_ID_JOYPAD_R;
        case AKEYCODE_BUTTON_R2:
            return RETRO_DEVICE_ID_JOYPAD_R2;
        case AKEYCODE_BUTTON_THUMBL:
            return RETRO_DEVICE_ID_JOYPAD_L3;
        case AKEYCODE_BUTTON_THUMBR:
            return RETRO_DEVICE_ID_JOYPAD_R3;
        case AKEYCODE_DPAD_UP:
            return RETRO_DEVICE_ID_JOYPAD_UP;
        case AKEYCODE_DPAD_DOWN:
            return RETRO_DEVICE_ID_JOYPAD_DOWN;
        case AKEYCODE_DPAD_LEFT:
            return RETRO_DEVICE_ID_JOYPAD_LEFT;
        case AKEYCODE_DPAD_RIGHT:
            return RETRO_DEVICE_ID_JOYPAD_RIGHT;
        case AKEYCODE_DPAD_UP_RIGHT:
            return Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT;
        case AKEYCODE_DPAD_UP_LEFT:
            return Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT;
        case AKEYCODE_DPAD_DOWN_RIGHT:
            return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT;
        case AKEYCODE_DPAD_DOWN_LEFT:
            return Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT;
        default:
            return UNKNOWN_KEY;
    }
}

void Input::onKeyEvent(unsigned int port, int action, int keyCode) {
    int retroKeyCode = convertAndroidToLibretroKey(keyCode);
    if (retroKeyCode == UNKNOWN_KEY) {
        return;
    }

    std::lock_guard<std::mutex> lock(inputMutex);
    if (action == AKEY_EVENT_ACTION_DOWN) {
        pads[port].pressedKeys.insert(retroKeyCode);
        pads[port].pendingReleases.erase(retroKeyCode);
    } else if (action == AKEY_EVENT_ACTION_UP) {
        pads[port].pendingReleases.insert(retroKeyCode);
    }
}

void Input::setInputPortState(unsigned int port, uint32_t bitmask) {
    if (port >= 4) return;

    std::lock_guard<std::mutex> lock(inputMutex);
    pads[port].pressedKeys.clear();
    pads[port].pendingReleases.clear();
    for (unsigned i = 0; i <= RETRO_DEVICE_ID_JOYPAD_R3; i++) {
        if ((bitmask & (1u << i)) != 0u) {
            pads[port].pressedKeys.insert(static_cast<int>(i));
        }
    }
}

void Input::flushPendingReleases() {
    std::lock_guard<std::mutex> lock(inputMutex);
    for (unsigned p = 0; p < 4; p++) {
        for (int key : pads[p].pendingReleases) {
            pads[p].pressedKeys.erase(key);
        }
        pads[p].pendingReleases.clear();

        if (pads[p].dpadReleasePending) {
            pads[p].dpadXAxis = pads[p].pendingDpadX;
            pads[p].dpadYAxis = pads[p].pendingDpadY;
            pads[p].dpadReleasePending = false;
        }
    }
}

bool Input::getButtonState(unsigned port, unsigned id) const {
    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_LEFT: {
            bool axis = pads[port].dpadXAxis == -1;
            return axis || anyPressed(port, RETRO_DEVICE_ID_JOYPAD_LEFT,
                Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT,
                Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT);
        }
        case RETRO_DEVICE_ID_JOYPAD_RIGHT: {
            bool axis = pads[port].dpadXAxis == 1;
            return axis || anyPressed(port, RETRO_DEVICE_ID_JOYPAD_RIGHT,
                Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT,
                Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT);
        }
        case RETRO_DEVICE_ID_JOYPAD_UP: {
            bool axis = pads[port].dpadYAxis == -1;
            return axis || anyPressed(port, RETRO_DEVICE_ID_JOYPAD_UP,
                Input::RETRO_DEVICE_ID_JOYPAD_UP_LEFT,
                Input::RETRO_DEVICE_ID_JOYPAD_UP_RIGHT);
        }
        case RETRO_DEVICE_ID_JOYPAD_DOWN: {
            bool axis = pads[port].dpadYAxis == 1;
            return axis || anyPressed(port, RETRO_DEVICE_ID_JOYPAD_DOWN,
                Input::RETRO_DEVICE_ID_JOYPAD_DOWN_LEFT,
                Input::RETRO_DEVICE_ID_JOYPAD_DOWN_RIGHT);
        }
        default:
            return anyPressed(port, id);
    }
}

void Input::onMotionEvent(int port, int motionSource, float xAxis, float yAxis) {
    std::lock_guard<std::mutex> lock(inputMutex);
    switch (motionSource) {
        case Input::MOTION_SOURCE_DPAD: {
            int newX = (int) round(xAxis);
            int newY = (int) round(yAxis);
            if (newX == 0 && newY == 0 && (pads[port].dpadXAxis != 0 || pads[port].dpadYAxis != 0)) {
                pads[port].dpadReleasePending = true;
                pads[port].pendingDpadX = 0;
                pads[port].pendingDpadY = 0;
            } else {
                pads[port].dpadXAxis = newX;
                pads[port].dpadYAxis = newY;
                pads[port].dpadReleasePending = false;
            }
            break;
        }

        case Input::MOTION_SOURCE_ANALOG_LEFT:
            pads[port].joypadLeftXAxis = xAxis;
            pads[port].joypadLeftYAxis = yAxis;
            break;

        case Input::MOTION_SOURCE_ANALOG_RIGHT:
            pads[port].joypadRightXAxis = xAxis;
            pads[port].joypadRightYAxis = yAxis;
            break;

        case Input::MOTION_SOURCE_POINTER:
            pads[port].pointerScreenXAxis = xAxis;
            pads[port].pointerScreenYAxis = yAxis;
            break;
    }
}

template<typename... T>
bool Input::anyPressed(unsigned int port, unsigned int id, T &... args) const {
    return anyPressed(port, id) || anyPressed(port, args...);
}

bool Input::anyPressed(unsigned int port, unsigned int id) const {
    return pads[port].pressedKeys.count(id) > 0;
}

} //namespace libretrodroid
