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
#ifndef LIBRETRODROID_BACKGROUNDFRAME_H
#define LIBRETRODROID_BACKGROUNDFRAME_H

#include <array>
#include <vector>
#include <GLES2/gl2.h>

namespace libretrodroid {

class BackgroundFrame {
public:
    void setImage(const uint8_t* rgbaData, int width, int height);
    void clearImage();
    void render(
        unsigned screenWidth,
        unsigned screenHeight,
        std::array<float, 12> backgroundVertices
    );
    bool hasImage() const;
    bool hasPendingImage() const;

private:
    void initializeShader();
    void uploadPendingTexture();

    const char* vertexShaderSource = R"(
        attribute mediump vec2 aPosition;
        attribute mediump vec2 aTexCoord;
        varying mediump vec2 vTexCoord;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
            vTexCoord = aTexCoord;
        }
    )";

    const char* fragmentShaderSource = R"(
        precision mediump float;
        varying mediump vec2 vTexCoord;
        uniform lowp sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    )";

    GLfloat textureCoords[12] = {
        0.0F, 0.0F,
        0.0F, 1.0F,
        1.0F, 0.0F,
        1.0F, 0.0F,
        0.0F, 1.0F,
        1.0F, 1.0F,
    };

    GLuint textureId = 0;
    GLuint shaderProgram = 0;
    GLint positionHandle = -1;
    GLint texCoordHandle = -1;
    GLint textureHandle = -1;
    bool shaderInitialized = false;

    // Pending image data to be uploaded on GL thread during render
    std::vector<uint8_t> pendingImageData;
    int pendingWidth = 0;
    int pendingHeight = 0;
    bool hasPending = false;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_BACKGROUNDFRAME_H
