#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include "libchdr/chd.h"

#define TAG "ChdJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define SECTOR_SIZE 2048
#define MAX_HUNK_BYTES (16 * 1024 * 1024)

static const uint8_t CD_SYNC_PATTERN[12] = {
    0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00
};

typedef struct {
    chd_file *chd;
    uint8_t *hunk_buffer;
    uint32_t hunk_bytes;
    uint32_t unit_bytes;
    uint32_t frames_per_hunk;
    uint32_t total_frames;
    uint32_t data_offset;
    int last_hunk;
} chd_context;

JNIEXPORT jlong JNICALL
Java_com_nendo_argosy_libchdr_ChdReader_nativeOpen(JNIEnv *env, jclass clazz, jstring path) {
    const char *path_str = (*env)->GetStringUTFChars(env, path, NULL);
    if (!path_str) return 0;

    chd_file *chd = NULL;
    chd_error err = chd_open(path_str, CHD_OPEN_READ, NULL, &chd);
    (*env)->ReleaseStringUTFChars(env, path, path_str);

    if (err != CHDERR_NONE) {
        LOGE("chd_open failed: %d", err);
        return 0;
    }

    const chd_header *header = chd_get_header(chd);

    if (header->unitbytes == 0 || header->hunkbytes == 0 ||
        header->hunkbytes > MAX_HUNK_BYTES) {
        LOGE("Invalid CHD header: unitbytes=%u hunkbytes=%u",
             header->unitbytes, header->hunkbytes);
        chd_close(chd);
        return 0;
    }

    chd_context *ctx = (chd_context *)calloc(1, sizeof(chd_context));
    if (!ctx) {
        chd_close(chd);
        return 0;
    }

    ctx->hunk_buffer = (uint8_t *)malloc(header->hunkbytes);
    if (!ctx->hunk_buffer) {
        chd_close(chd);
        free(ctx);
        return 0;
    }

    ctx->chd = chd;
    ctx->hunk_bytes = header->hunkbytes;
    ctx->unit_bytes = header->unitbytes;
    ctx->last_hunk = -1;
    ctx->frames_per_hunk = ctx->hunk_bytes / ctx->unit_bytes;
    ctx->total_frames = (uint32_t)(header->logicalbytes / ctx->unit_bytes);

    // Determine data offset within each unit by probing sector 16 (PVD).
    // CD CHDs use 2448 unit_bytes regardless of track type; user data position depends on the
    // mode byte at offset 15 of each 2352-byte raw sector:
    //   MODE1: [12 sync][3 hdr addr][1 mode=1][2048 data][288 EDC/ECC]      -> offset 16
    //   MODE2: [12 sync][3 hdr addr][1 mode=2][8 subheader][2048 data][...] -> offset 24
    ctx->data_offset = 0;
    if (ctx->unit_bytes > SECTOR_SIZE) {
        int hi = 16 / ctx->frames_per_hunk;
        err = chd_read(ctx->chd, hi, ctx->hunk_buffer);
        if (err == CHDERR_NONE) {
            int fi = 16 % ctx->frames_per_hunk;
            uint8_t *frame = ctx->hunk_buffer + fi * ctx->unit_bytes;
            if (memcmp(frame, CD_SYNC_PATTERN, 12) == 0) {
                uint8_t mode = frame[15];
                if (mode == 1) {
                    ctx->data_offset = 16;
                } else if (mode == 2) {
                    ctx->data_offset = 24;
                }
            }
        }
        if (ctx->data_offset + SECTOR_SIZE > ctx->unit_bytes) {
            ctx->data_offset = 0;
        }
        ctx->last_hunk = -1;
    }

    return (jlong)(intptr_t)ctx;
}

JNIEXPORT jbyteArray JNICALL
Java_com_nendo_argosy_libchdr_ChdReader_nativeReadSector(JNIEnv *env, jclass clazz,
                                                          jlong handle, jint lba) {
    chd_context *ctx = (chd_context *)(intptr_t)handle;
    if (!ctx || !ctx->chd) return NULL;

    if ((uint32_t)lba >= ctx->total_frames) return NULL;

    int hunk_index = lba / ctx->frames_per_hunk;
    int frame_in_hunk = lba % ctx->frames_per_hunk;

    if (hunk_index != ctx->last_hunk) {
        chd_error err = chd_read(ctx->chd, hunk_index, ctx->hunk_buffer);
        if (err != CHDERR_NONE) {
            LOGE("chd_read hunk %d failed: %d", hunk_index, err);
            return NULL;
        }
        ctx->last_hunk = hunk_index;
    }

    uint8_t *sector_data = ctx->hunk_buffer + (frame_in_hunk * ctx->unit_bytes) + ctx->data_offset;

    jbyteArray result = (*env)->NewByteArray(env, SECTOR_SIZE);
    if (result) {
        (*env)->SetByteArrayRegion(env, result, 0, SECTOR_SIZE, (jbyte *)sector_data);
    }
    return result;
}

JNIEXPORT jint JNICALL
Java_com_nendo_argosy_libchdr_ChdReader_nativeGetTotalFrames(JNIEnv *env, jclass clazz,
                                                              jlong handle) {
    chd_context *ctx = (chd_context *)(intptr_t)handle;
    if (!ctx) return 0;
    return (jint)ctx->total_frames;
}

JNIEXPORT void JNICALL
Java_com_nendo_argosy_libchdr_ChdReader_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    chd_context *ctx = (chd_context *)(intptr_t)handle;
    if (!ctx) return;

    if (ctx->chd) {
        chd_close(ctx->chd);
    }
    free(ctx->hunk_buffer);
    free(ctx);
}
