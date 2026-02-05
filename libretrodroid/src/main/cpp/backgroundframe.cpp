/*
 *     Copyright (C) 2025  Filippo Scognamiglio
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

#include "backgroundframe.h"
#include "log.h"

namespace libretrodroid {

void BackgroundFrame::initializeShader() {
    if (shaderInitialized) return;

    LOGI("BackgroundFrame::initializeShader() starting");

    // Clear any pending GL errors
    GLenum err;
    while ((err = glGetError()) != GL_NO_ERROR) {
        LOGI("BackgroundFrame: Clearing GL error: 0x%x", err);
    }

    GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
    GLenum errAfterCreate = glGetError();
    LOGI("BackgroundFrame: vertexShader handle = %u, glError after create = 0x%x", vertexShader, errAfterCreate);
    if (vertexShader == 0) {
        LOGE("BackgroundFrame: glCreateShader returned 0! GL context may not be current.");
        return;
    }
    glShaderSource(vertexShader, 1, &vertexShaderSource, nullptr);
    glCompileShader(vertexShader);

    GLint vertexCompiled = 0;
    glGetShaderiv(vertexShader, GL_COMPILE_STATUS, &vertexCompiled);
    LOGI("BackgroundFrame: vertex shader compiled = %d", vertexCompiled);
    if (!vertexCompiled) {
        GLint infoLen = 0;
        glGetShaderiv(vertexShader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            char* buf = new char[infoLen];
            glGetShaderInfoLog(vertexShader, infoLen, nullptr, buf);
            LOGE("BackgroundFrame vertex shader compile error: %s", buf);
            delete[] buf;
        }
    }

    GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    LOGI("BackgroundFrame: fragmentShader handle = %u", fragmentShader);
    glShaderSource(fragmentShader, 1, &fragmentShaderSource, nullptr);
    glCompileShader(fragmentShader);

    GLint fragmentCompiled = 0;
    glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, &fragmentCompiled);
    LOGI("BackgroundFrame: fragment shader compiled = %d", fragmentCompiled);
    if (!fragmentCompiled) {
        GLint infoLen = 0;
        glGetShaderiv(fragmentShader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            char* buf = new char[infoLen];
            glGetShaderInfoLog(fragmentShader, infoLen, nullptr, buf);
            LOGE("BackgroundFrame fragment shader compile error: %s", buf);
            delete[] buf;
        }
    }

    shaderProgram = glCreateProgram();
    LOGI("BackgroundFrame: program handle = %u", shaderProgram);
    glAttachShader(shaderProgram, vertexShader);
    glAttachShader(shaderProgram, fragmentShader);
    glBindAttribLocation(shaderProgram, 0, "aPosition");
    glBindAttribLocation(shaderProgram, 1, "aTexCoord");
    glLinkProgram(shaderProgram);

    GLint linked = 0;
    glGetProgramiv(shaderProgram, GL_LINK_STATUS, &linked);
    LOGI("BackgroundFrame: program linked = %d", linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(shaderProgram, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            char* buf = new char[infoLen];
            glGetProgramInfoLog(shaderProgram, infoLen, nullptr, buf);
            LOGE("BackgroundFrame shader link error: %s", buf);
            delete[] buf;
        }
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    positionHandle = glGetAttribLocation(shaderProgram, "aPosition");
    texCoordHandle = glGetAttribLocation(shaderProgram, "aTexCoord");
    textureHandle = glGetUniformLocation(shaderProgram, "uTexture");

    LOGI("BackgroundFrame shader initialized: program=%u, pos=%d, tex=%d, uniform=%d",
         shaderProgram, positionHandle, texCoordHandle, textureHandle);

    shaderInitialized = true;
}

void BackgroundFrame::setImage(const uint8_t* rgbaData, int width, int height) {
    LOGI("BackgroundFrame::setImage: %d x %d", width, height);

    // Store image data - will be uploaded to GPU during render when GL context is guaranteed
    size_t dataSize = width * height * 4;
    pendingImageData.resize(dataSize);
    memcpy(pendingImageData.data(), rgbaData, dataSize);
    pendingWidth = width;
    pendingHeight = height;
    hasPending = true;

    LOGI("BackgroundFrame::setImage: stored %zu bytes pending", dataSize);
}

void BackgroundFrame::uploadPendingTexture() {
    if (!hasPending) return;

    LOGI("BackgroundFrame::uploadPendingTexture: %d x %d", pendingWidth, pendingHeight);

    if (textureId == 0) {
        glGenTextures(1, &textureId);
        LOGI("BackgroundFrame: Generated texture ID = %u", textureId);
    }

    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, pendingWidth, pendingHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, pendingImageData.data());
    glBindTexture(GL_TEXTURE_2D, 0);

    // Clear pending data
    pendingImageData.clear();
    pendingImageData.shrink_to_fit();
    hasPending = false;

    LOGI("BackgroundFrame::uploadPendingTexture complete, textureId=%u", textureId);
}

void BackgroundFrame::clearImage() {
    LOGI("BackgroundFrame::clearImage");
    if (textureId != 0) {
        glDeleteTextures(1, &textureId);
        textureId = 0;
    }
    // Also clear any pending data
    pendingImageData.clear();
    pendingImageData.shrink_to_fit();
    hasPending = false;
}

bool BackgroundFrame::hasImage() const {
    return textureId != 0;
}

bool BackgroundFrame::hasPendingImage() const {
    return hasPending;
}

void BackgroundFrame::render(
    unsigned screenWidth,
    unsigned screenHeight,
    std::array<float, 12> backgroundVertices
) {
    // Upload any pending texture data first (deferred from setImage)
    uploadPendingTexture();

    if (!hasImage()) return;

    // Initialize shader lazily here - GL context is guaranteed to be current during render
    initializeShader();
    if (shaderProgram == 0) {
        LOGE("BackgroundFrame::render: No valid shader program, skipping render");
        return;
    }

    LOGI("BackgroundFrame::render: screen=%dx%d, textureId=%u, shaderProgram=%u",
         screenWidth, screenHeight, textureId, shaderProgram);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, screenWidth, screenHeight);
    glUseProgram(shaderProgram);

    glVertexAttribPointer(positionHandle, 2, GL_FLOAT, GL_FALSE, 0, backgroundVertices.data());
    glEnableVertexAttribArray(positionHandle);

    glVertexAttribPointer(texCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, textureCoords);
    glEnableVertexAttribArray(texCoordHandle);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glUniform1i(textureHandle, 0);

    glDrawArrays(GL_TRIANGLES, 0, 6);

    glDisableVertexAttribArray(positionHandle);
    glDisableVertexAttribArray(texCoordHandle);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
}

} // namespace libretrodroid
