/*
 *     Copyright (C) 2026  Argosy
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#include "stateloadpolicy.h"

namespace libretrodroid {

bool attemptStateLoad(
    const void* data,
    size_t stateSize,
    size_t currentSerializeSize,
    StateLoadPolicy policy,
    const StateUnserializer& unserialize
) {
    if (!data || stateSize == 0 || currentSerializeSize == 0) {
        return false;
    }
    if (stateSize != currentSerializeSize && policy == StateLoadPolicy::StrictSize) {
        return false;
    }
    return unserialize(data, stateSize);
}

} // namespace libretrodroid
