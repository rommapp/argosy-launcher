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

#include <GLES2/gl2.h>
#include <EGL/egl.h>
#include <cstdlib>
#include <string>
#include <cmath>
#include <utility>
#include <sstream>

#include "log.h"

#include "video.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es3/imagerendereres3.h"
#include "renderers/es2/imagerendereres2.h"

namespace libretrodroid {

static void printGLString(const char *name, GLenum s) {
    const char *v = (const char *) glGetString(s);
    LOGI("GL %s = %s\n", name, v);
}

GLuint loadShader(GLenum shaderType, const char* pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, nullptr);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, nullptr, buf);
                    LOGE("Could not compile shader %d:\n%s\n",
                         shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}

GLuint createProgram(const char* pVertexSource, const char* pFragmentSource) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) {
        return 0;
    }

    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader) {
        return 0;
    }

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        glAttachShader(program, pixelShader);
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength) {
                char* buf = (char*) malloc(bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, nullptr, buf);
                    LOGE("Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

void Video::updateProgram() {
    if (loadedShaderType.has_value() && loadedShaderType.value() == requestedShaderConfig) {
        return;
    }

    loadedShaderType = requestedShaderConfig;

    auto shaders = ShaderManager::getShader(requestedShaderConfig);

    // Apply filter mode override if set
    if (filterMode == 0) {
        shaders.linearTexture = false;  // Nearest
    } else if (filterMode == 1) {
        shaders.linearTexture = true;   // Linear/Bilinear
    }
    // filterMode == -1 means auto (use shader's default)

    shadersChain = {};

    std::for_each(shaders.passes.begin(), shaders.passes.end(), [&](const auto& item){
        auto shader = ShaderChainEntry { };

        shader.gProgram = createProgram(item.vertex.data(), item.fragment.data());
        if (!shader.gProgram) {
            LOGE("Could not create gl program.");
            throw std::runtime_error("Cannot create gl program");
        }

        shader.gvPositionHandle = glGetAttribLocation(shader.gProgram, "vPosition");
        if (shader.gvPositionHandle == -1)
            shader.gvPositionHandle = glGetAttribLocation(shader.gProgram, "VertexCoord");

        shader.gvCoordinateHandle = glGetAttribLocation(shader.gProgram, "vCoordinate");
        if (shader.gvCoordinateHandle == -1)
            shader.gvCoordinateHandle = glGetAttribLocation(shader.gProgram, "TexCoord");

        shader.gTextureHandle = glGetUniformLocation(shader.gProgram, "texture");
        if (shader.gTextureHandle == -1)
            shader.gTextureHandle = glGetUniformLocation(shader.gProgram, "Texture");

        shader.gPreviousPassTextureHandle = glGetUniformLocation(shader.gProgram, "previousPass");

        shader.gTextureSizeHandle = glGetUniformLocation(shader.gProgram, "textureSize");
        if (shader.gTextureSizeHandle == -1)
            shader.gTextureSizeHandle = glGetUniformLocation(shader.gProgram, "TextureSize");

        shader.gScreenDensityHandle = glGetUniformLocation(shader.gProgram, "screenDensity");

        shader.gInputSizeHandle = glGetUniformLocation(shader.gProgram, "InputSize");
        shader.gOutputSizeHandle = glGetUniformLocation(shader.gProgram, "OutputSize");
        shader.gFrameCountHandle = glGetUniformLocation(shader.gProgram, "FrameCount");
        shader.gFrameDirectionHandle = glGetUniformLocation(shader.gProgram, "FrameDirection");
        shader.gMVPMatrixHandle = glGetUniformLocation(shader.gProgram, "MVPMatrix");

        shadersChain.push_back(shader);
    });

    renderer->setShaders(shaders);
}

void Video::renderFrame() {
    LOGD("Video::renderFrame: skipDuplicateFrames=%d bfiEnabled=%d isDirty=%d shadersChain.size=%zu",
         skipDuplicateFrames, bfiEnabled, isDirty, shadersChain.size());
    if (skipDuplicateFrames && !bfiEnabled && !isDirty) {
        LOGD("Video::renderFrame: SKIPPING (not dirty)");
        return;
    }
    isDirty = false;
    frameCount++;

    glDisable(GL_DEPTH_TEST);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    if (!backgroundFrame.hasImage() && immersiveModeEnabled) {
        immersiveMode.renderBackground(
            videoLayout.getScreenWidth(),
            videoLayout.getScreenHeight(),
            videoLayout.getBackgroundVertices(),
            videoLayout.getRelativeForegroundBounds(),
            videoLayout.getFramebufferVertices().data(),
            renderer->getTexture()
        );
    }

    updateProgram();
    for (int i = 0; i < shadersChain.size(); ++i) {
        auto shader = shadersChain[i];
        auto passData = renderer->getPassData(i);
        auto isLastPass = i == shadersChain.size() - 1;

        glBindFramebuffer(GL_FRAMEBUFFER, passData.framebuffer.value_or(0));

        glViewport(
            0,
            0,
            passData.width.value_or(videoLayout.getScreenWidth()),
            passData.height.value_or(videoLayout.getScreenHeight())
        );

        glUseProgram(shader.gProgram);

        auto vertices = isLastPass ? videoLayout.getForegroundVertices() : videoLayout.getFramebufferVertices();
        glVertexAttribPointer(shader.gvPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices.data());
        glEnableVertexAttribArray(shader.gvPositionHandle);

        auto coordinates = videoLayout.getTextureCoordinates();
        glVertexAttribPointer(shader.gvCoordinateHandle, 2, GL_FLOAT, GL_FALSE, 0, coordinates.data());
        glEnableVertexAttribArray(shader.gvCoordinateHandle);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
        glUniform1i(shader.gTextureHandle, 0);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, passData.texture.value());
            glUniform1i(shader.gPreviousPassTextureHandle, 1);
        }

        glUniform2f(shader.gTextureSizeHandle, getTextureWidth(), getTextureHeight());

        glUniform1f(shader.gScreenDensityHandle, getScreenDensity());

        if (shader.gInputSizeHandle != -1)
            glUniform2f(shader.gInputSizeHandle, getTextureWidth(), getTextureHeight());

        auto passWidth = static_cast<float>(passData.width.value_or(videoLayout.getScreenWidth()));
        auto passHeight = static_cast<float>(passData.height.value_or(videoLayout.getScreenHeight()));

        if (shader.gOutputSizeHandle != -1)
            glUniform2f(shader.gOutputSizeHandle, passWidth, passHeight);

        if (shader.gFrameCountHandle != -1)
            glUniform1i(shader.gFrameCountHandle, static_cast<GLint>(frameCount));

        if (shader.gFrameDirectionHandle != -1)
            glUniform1i(shader.gFrameDirectionHandle, 1);

        if (shader.gMVPMatrixHandle != -1) {
            static const GLfloat identity[16] = {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
            };
            glUniformMatrix4fv(shader.gMVPMatrixHandle, 1, GL_FALSE, identity);
        }

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(shader.gvPositionHandle);
        glDisableVertexAttribArray(shader.gvCoordinateHandle);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glUseProgram(0);
    }

    // Render background frame ON TOP of game content with alpha blending
    // Check both hasImage (texture uploaded) and hasPendingImage (data waiting to be uploaded)
    bool hasFrame = backgroundFrame.hasImage() || backgroundFrame.hasPendingImage();
    LOGI("Video::renderFrame: hasFrame = %d (hasImage=%d, hasPending=%d)",
         hasFrame, backgroundFrame.hasImage(), backgroundFrame.hasPendingImage());
    if (hasFrame) {
        LOGI("Video::renderFrame: Rendering backgroundFrame, screen=%dx%d",
             videoLayout.getScreenWidth(), videoLayout.getScreenHeight());
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        backgroundFrame.render(
            videoLayout.getScreenWidth(),
            videoLayout.getScreenHeight(),
            videoLayout.getBackgroundVertices()
        );
        glDisable(GL_BLEND);
    }
}

float Video::getScreenDensity() {
    return std::min(videoLayout.getScreenWidth() / getTextureWidth(), videoLayout.getScreenHeight() / getTextureHeight());
}

float Video::getTextureWidth() {
    return renderer->lastFrameSize.first;
}

float Video::getTextureHeight() {
    return renderer->lastFrameSize.second;
}

std::vector<uint8_t> Video::captureRawFrame(int& outWidth, int& outHeight) {
    outWidth = (int)getTextureWidth();
    outHeight = (int)getTextureHeight();
    if (outWidth == 0 || outHeight == 0) return {};

    GLuint fbo;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
        GL_TEXTURE_2D, renderer->getTexture(), 0);

    std::vector<uint8_t> pixels(outWidth * outHeight * 4);
    glReadPixels(0, 0, outWidth, outHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &fbo);
    return pixels;
}

void Video::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    LOGD("Video::onNewFrame: data=%p width=%u height=%u pitch=%zu", data, width, height, pitch);
    if (data != nullptr) {
        renderer->onNewFrame(data, width, height, pitch);
        videoLayout.updateContentSize(width, height);
        isDirty = true;
        LOGD("Video::onNewFrame: isDirty set to true");
    } else {
        LOGD("Video::onNewFrame: data is NULL, skipping");
    }
}

void Video::updateScreenSize(unsigned width, unsigned height) {
    videoLayout.updateScreenSize(width, height);
}

void Video::updateViewportSize(Rect viewportRect) {
    videoLayout.updateViewportSize(viewportRect);
}

void Video::updateRendererSize(unsigned int width, unsigned int height) {
    LOGD("Updating renderer size: %d x %d", width, height);
    renderer->updateRenderedResolution(width, height);
}

void Video::updateRotation(float rotation) {
    videoLayout.updateRotation(rotation);
}

Video::Video(
    RenderingOptions renderingOptions,
    ShaderManager::Config shaderConfig,
    bool bottomLeftOrigin,
    float rotation,
    bool skipDuplicateFrames,
    bool immersiveModeEnabled,
    Rect viewportRect,
    ImmersiveMode::Config immersiveModeConfig
) :
    requestedShaderConfig(std::move(shaderConfig)),
    skipDuplicateFrames(skipDuplicateFrames),
    immersiveModeEnabled(immersiveModeEnabled),
    immersiveMode(immersiveModeConfig),
    videoLayout(bottomLeftOrigin, rotation, viewportRect) {

    printGLString("Version", GL_VERSION);
    printGLString("Vendor", GL_VENDOR);
    printGLString("Renderer", GL_RENDERER);
    printGLString("Extensions", GL_EXTENSIONS);
    initializeGLESLogCallbackIfNeeded();

    LOGI("Initializing graphics");

    glViewport(0, 0, videoLayout.getScreenWidth(), videoLayout.getScreenHeight());

    glUseProgram(0);

    initializeRenderer(renderingOptions);
}

void Video::updateShaderType(ShaderManager::Config shaderConfig) {
    requestedShaderConfig = std::move(shaderConfig);
}

void Video::setFilterMode(int mode) {
    if (filterMode != mode) {
        filterMode = mode;
        loadedShaderType = std::nullopt;  // Force shader rebuild on next frame
    }
}

void Video::setIntegerScaling(bool enabled) {
    videoLayout.setIntegerScaling(enabled);
}

void Video::setBlackFrameInsertion(bool enabled) {
    bfiEnabled = enabled;
    bfiFrameCounter = 0;
}

void Video::setBackgroundFrame(const uint8_t* data, int width, int height) {
    backgroundFrame.setImage(data, width, height);
}

void Video::clearBackgroundFrame() {
    backgroundFrame.clearImage();
}

void Video::renderBlackFrame() {
    glDisable(GL_DEPTH_TEST);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
}

void Video::initializeRenderer(RenderingOptions renderingOptions) {
    auto shaders = ShaderManager::getShader(requestedShaderConfig);

    if (renderingOptions.hardwareAccelerated) {
        renderer = new FramebufferRenderer(
            renderingOptions.width,
            renderingOptions.height,
            renderingOptions.useDepth,
            renderingOptions.useStencil,
            std::move(shaders)
        );
    } else {
        if (renderingOptions.openglESVersion >= 3) {
            renderer = new ImageRendererES3();
        } else {
            renderer = new ImageRendererES2();
        }
    }

    renderer->setPixelFormat(renderingOptions.pixelFormat);
    updateProgram();
}

void Video::updateAspectRatio(float aspectRatio) {
    videoLayout.updateAspectRatio(aspectRatio);
}

} //namespace libretrodroid
