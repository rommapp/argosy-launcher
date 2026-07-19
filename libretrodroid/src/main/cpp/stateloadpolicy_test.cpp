/*
 *     Copyright (C) 2026  Argosy
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#include "stateloadpolicy_test.h"

#include <cstdint>

#include "stateloadpolicy.h"

namespace libretrodroid::test {

int runStateLoadPolicyTests() {
    int passed = 0;
    uint8_t data = 0x42;
    bool called = false;

    auto acceptingCore = [&called](const void*, size_t) {
        called = true;
        return true;
    };
    auto rejectingCore = [&called](const void*, size_t) {
        called = true;
        return false;
    };

    called = false;
    if (attemptStateLoad(&data, 8, 8, StateLoadPolicy::StrictSize, acceptingCore) && called) {
        ++passed;
    }

    called = false;
    if (!attemptStateLoad(&data, 7, 8, StateLoadPolicy::StrictSize, acceptingCore) && !called) {
        ++passed;
    }

    called = false;
    if (attemptStateLoad(&data, 7, 8, StateLoadPolicy::CoreValidated, acceptingCore) && called) {
        ++passed;
    }

    called = false;
    if (!attemptStateLoad(&data, 7, 8, StateLoadPolicy::CoreValidated, rejectingCore) && called) {
        ++passed;
    }

    called = false;
    if (!attemptStateLoad(&data, 8, 0, StateLoadPolicy::CoreValidated, acceptingCore) && !called) {
        ++passed;
    }

    called = false;
    if (!attemptStateLoad(&data, 0, 8, StateLoadPolicy::CoreValidated, acceptingCore) && !called) {
        ++passed;
    }

    called = false;
    if (!attemptStateLoad(nullptr, 8, 8, StateLoadPolicy::CoreValidated, acceptingCore) && !called) {
        ++passed;
    }

    return passed;
}

} // namespace libretrodroid::test
