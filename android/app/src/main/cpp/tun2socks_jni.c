#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "Tun2SocksJni"

typedef int (*hev_tunnel_main_from_file_fn)(const char *config_path, int tun_fd);
typedef void (*hev_tunnel_quit_fn)(void);
typedef void (*hev_tunnel_stats_fn)(size_t *tx_packets, size_t *tx_bytes, size_t *rx_packets, size_t *rx_bytes);

typedef struct Tun2SocksLibraryState {
    void *handle;
    char *library_path;
    hev_tunnel_main_from_file_fn main_from_file;
    hev_tunnel_quit_fn quit;
    hev_tunnel_stats_fn stats;
} Tun2SocksLibraryState;

static Tun2SocksLibraryState g_state = {0};
static pthread_mutex_t g_state_mutex = PTHREAD_MUTEX_INITIALIZER;

static void throw_java_exception(JNIEnv *env, const char *class_name, const char *message)
{
    jclass clazz = (*env)->FindClass(env, class_name);
    if (clazz == NULL) {
        return;
    }

    (*env)->ThrowNew(env, clazz, message);
}

static void throw_illegal_state(JNIEnv *env, const char *message)
{
    throw_java_exception(env, "java/lang/IllegalStateException", message);
}

static void throw_runtime_exception(JNIEnv *env, const char *message)
{
    throw_java_exception(env, "java/lang/RuntimeException", message);
}

static char *duplicate_cstring(const char *source)
{
    size_t length = strlen(source);
    char *copy = (char *) malloc(length + 1);
    if (copy == NULL) {
        return NULL;
    }

    memcpy(copy, source, length + 1);
    return copy;
}

static bool copy_jstring(JNIEnv *env, jstring source, char **target, const char *label)
{
    const char *raw = NULL;
    size_t length = 0;
    char *buffer = NULL;

    if (source == NULL) {
        char message[128];
        snprintf(message, sizeof(message), "%s must not be null", label);
        throw_illegal_state(env, message);
        return false;
    }

    raw = (*env)->GetStringUTFChars(env, source, NULL);
    if (raw == NULL) {
        return false;
    }

    length = strlen(raw);
    buffer = (char *) malloc(length + 1);
    if (buffer == NULL) {
        (*env)->ReleaseStringUTFChars(env, source, raw);
        throw_runtime_exception(env, "Failed to allocate native string buffer");
        return false;
    }

    memcpy(buffer, raw, length + 1);
    (*env)->ReleaseStringUTFChars(env, source, raw);

    *target = buffer;
    return true;
}

static bool resolve_symbol(void *handle, const char *symbol_name, void **target, JNIEnv *env)
{
    dlerror();
    *target = dlsym(handle, symbol_name);
    const char *error = dlerror();
    if (error != NULL) {
        char message[512];
        snprintf(message, sizeof(message), "Failed to resolve %s: %s", symbol_name, error);
        throw_illegal_state(env, message);
        return false;
    }

    return true;
}

static bool ensure_library_loaded(JNIEnv *env, const char *library_path)
{
    bool success = false;

    pthread_mutex_lock(&g_state_mutex);

    if (g_state.handle != NULL && g_state.library_path != NULL && strcmp(g_state.library_path, library_path) == 0) {
        success = true;
        goto done;
    }

    if (g_state.handle != NULL && g_state.library_path != NULL && strcmp(g_state.library_path, library_path) != 0) {
        throw_illegal_state(env, "tun2socks JNI shim is already bound to a different shared library path");
        goto done;
    }

    void *handle = dlopen(library_path, RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        char message[512];
        snprintf(message, sizeof(message), "Failed to load tun2socks shared library %s: %s", library_path, dlerror());
        throw_illegal_state(env, message);
        goto done;
    }

    hev_tunnel_main_from_file_fn main_from_file = NULL;
    hev_tunnel_quit_fn quit = NULL;
    hev_tunnel_stats_fn stats = NULL;

    if (!resolve_symbol(handle, "hev_socks5_tunnel_main_from_file", (void **) &main_from_file, env)) {
        dlclose(handle);
        goto done;
    }
    if (!resolve_symbol(handle, "hev_socks5_tunnel_quit", (void **) &quit, env)) {
        dlclose(handle);
        goto done;
    }
    if (!resolve_symbol(handle, "hev_socks5_tunnel_stats", (void **) &stats, env)) {
        dlclose(handle);
        goto done;
    }

    g_state.handle = handle;
    g_state.library_path = duplicate_cstring(library_path);
    g_state.main_from_file = main_from_file;
    g_state.quit = quit;
    g_state.stats = stats;
    success = g_state.library_path != NULL;

    if (!success) {
        dlclose(handle);
        g_state.handle = NULL;
        g_state.main_from_file = NULL;
        g_state.quit = NULL;
        g_state.stats = NULL;
        throw_runtime_exception(env, "Failed to persist tun2socks shared library path");
    }

done:
    pthread_mutex_unlock(&g_state_mutex);
    return success;
}

static bool get_library_functions(JNIEnv *env, const char *library_path, hev_tunnel_main_from_file_fn *main_from_file,
    hev_tunnel_quit_fn *quit, hev_tunnel_stats_fn *stats)
{
    bool success = false;

    if (!ensure_library_loaded(env, library_path)) {
        return false;
    }

    pthread_mutex_lock(&g_state_mutex);
    if (g_state.handle == NULL || g_state.main_from_file == NULL || g_state.quit == NULL || g_state.stats == NULL) {
        throw_illegal_state(env, "tun2socks JNI shim did not initialize required native symbols");
        goto done;
    }

    *main_from_file = g_state.main_from_file;
    *quit = g_state.quit;
    *stats = g_state.stats;
    success = true;

done:
    pthread_mutex_unlock(&g_state_mutex);
    return success;
}

JNIEXPORT jint JNICALL
Java_com_swimvpn_app_runtime_Tun2SocksNativeBridge_nativeStart(
    JNIEnv *env,
    jobject thiz,
    jstring shared_library_path,
    jstring config_path,
    jint tun_fd)
{
    (void) thiz;

    char *library_path = NULL;
    char *config_path_value = NULL;
    hev_tunnel_main_from_file_fn main_from_file = NULL;
    hev_tunnel_quit_fn quit = NULL;
    hev_tunnel_stats_fn stats = NULL;
    int result = -1;

    if (!copy_jstring(env, shared_library_path, &library_path, "sharedLibraryPath")) {
        goto done;
    }
    if (!copy_jstring(env, config_path, &config_path_value, "configPath")) {
        goto done;
    }
    if (!get_library_functions(env, library_path, &main_from_file, &quit, &stats)) {
        goto done;
    }

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Starting tun2socks with %s", library_path);
    result = main_from_file(config_path_value, tun_fd);

done:
    free(library_path);
    free(config_path_value);
    return result;
}

JNIEXPORT void JNICALL
Java_com_swimvpn_app_runtime_Tun2SocksNativeBridge_nativeStop(
    JNIEnv *env,
    jobject thiz,
    jstring shared_library_path)
{
    (void) thiz;

    char *library_path = NULL;
    hev_tunnel_main_from_file_fn main_from_file = NULL;
    hev_tunnel_quit_fn quit = NULL;
    hev_tunnel_stats_fn stats = NULL;

    if (!copy_jstring(env, shared_library_path, &library_path, "sharedLibraryPath")) {
        goto done;
    }
    if (!get_library_functions(env, library_path, &main_from_file, &quit, &stats)) {
        goto done;
    }

    quit();

done:
    free(library_path);
}

JNIEXPORT jlongArray JNICALL
Java_com_swimvpn_app_runtime_Tun2SocksNativeBridge_nativeStats(
    JNIEnv *env,
    jobject thiz,
    jstring shared_library_path)
{
    (void) thiz;

    char *library_path = NULL;
    hev_tunnel_main_from_file_fn main_from_file = NULL;
    hev_tunnel_quit_fn quit = NULL;
    hev_tunnel_stats_fn stats = NULL;
    size_t tx_packets = 0;
    size_t tx_bytes = 0;
    size_t rx_packets = 0;
    size_t rx_bytes = 0;
    jlong values[4];
    jlongArray result = NULL;

    if (!copy_jstring(env, shared_library_path, &library_path, "sharedLibraryPath")) {
        goto done;
    }
    if (!get_library_functions(env, library_path, &main_from_file, &quit, &stats)) {
        goto done;
    }

    stats(&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);

    values[0] = (jlong) tx_packets;
    values[1] = (jlong) tx_bytes;
    values[2] = (jlong) rx_packets;
    values[3] = (jlong) rx_bytes;

    result = (*env)->NewLongArray(env, 4);
    if (result == NULL) {
        goto done;
    }

    (*env)->SetLongArrayRegion(env, result, 0, 4, values);

done:
    free(library_path);
    return result;
}
