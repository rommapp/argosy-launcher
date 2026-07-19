/*
 *     Copyright (C) 2026  Argosy
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#ifndef LIBRETRODROID_STATELOADPOLICY_H
#define LIBRETRODROID_STATELOADPOLICY_H

#include <cstddef>
#include <functional>

namespace libretrodroid {

enum class StateLoadPolicy {
    // Runtime and peer-provided snapshots must match the active core contract.
    StrictSize,
    // Persisted states may have a valid historical size; let the core decide.
    CoreValidated
};

using StateUnserializer = std::function<bool(const void*, size_t)>;

bool attemptStateLoad(
    const void* data,
    size_t stateSize,
    size_t currentSerializeSize,
    StateLoadPolicy policy,
    const StateUnserializer& unserialize
);

} // namespace libretrodroid

#endif // LIBRETRODROID_STATELOADPOLICY_H
