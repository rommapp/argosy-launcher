/*
 *     Copyright (C) 2024  Argosy Contributors
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

#include "rewindbuffer.h"
#include <algorithm>

namespace libretrodroid {

RewindBuffer::RewindBuffer(size_t slotCount, size_t maxStateSize)
    : capacity(slotCount), maxSize(maxStateSize) {
    slots.resize(slotCount);
    for (auto& slot : slots) {
        slot.reserve(maxStateSize);
    }
}

RewindBuffer::~RewindBuffer() {
    clear();
}

bool RewindBuffer::push(const uint8_t* data, size_t size) {
    if (size > maxSize) {
        return false;
    }

    auto& slot = slots[writeIndex];
    slot.resize(size);
    std::copy(data, data + size, slot.begin());

    writeIndex = (writeIndex + 1) % capacity;
    if (validCount < capacity) {
        validCount++;
    }
    return true;
}

bool RewindBuffer::pop(uint8_t* outData, size_t* outSize) {
    if (validCount == 0) {
        return false;
    }

    size_t readIndex = (writeIndex + capacity - 1) % capacity;
    writeIndex = readIndex;
    validCount--;

    const auto& slot = slots[readIndex];
    *outSize = slot.size();
    std::copy(slot.begin(), slot.end(), outData);

    return true;
}

void RewindBuffer::clear() {
    for (auto& slot : slots) {
        slot.clear();
    }
    writeIndex = 0;
    validCount = 0;
}

}
