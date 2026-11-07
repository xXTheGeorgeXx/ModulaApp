#include <jni.h>
#include <android/log.h>
#include <vector>
#include <string>
#include <sched.h>
#include <unistd.h>
#include <sys/sysconf.h>
#include <algorithm>
#include <cstring>
#include "llama.h"

#define TAG "MODULA_CPU"

// =====================================================================
// NÚCLEO DEL MOTOR
// =====================================================================

struct ModulaCore {
    struct llama_model  * model  = nullptr;
    struct llama_context* ctx    = nullptr;
    int                   n_past = 0;
};

// Verifica que un buffer de texto es un carácter UTF-8 completo.
// Evita enviar caracteres corruptos al JNI cuando un token
// multibyte llega partido entre dos llamadas.
static bool is_complete_utf8(const std::string& str) {
    if (str.empty()) return true;
    int i = (int)str.length() - 1;
    int continuation = 0;
    while (i >= 0 && (str[i] & 0xC0) == 0x80) { continuation++; i--; }
    if (i < 0) return false;
    unsigned char lead = str[i];
    int expected = 0;
    if      ((lead & 0xE0) == 0xC0) expected = 1;
    else if ((lead & 0xF0) == 0xE0) expected = 2;
    else if ((lead & 0xF8) == 0xF0) expected = 3;
    return continuation == expected;
}

// Añade un token al batch manualmente (control total sobre logits y pos)
static void batch_add(struct llama_batch& batch,
                      llama_token id, llama_pos pos, bool logits) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id  [batch.n_tokens][0] = 0;
    batch.logits  [batch.n_tokens] = logits;
    batch.n_tokens++;
}

// =====================================================================
// CALLBACK
// =====================================================================

typedef void (*modula_token_cb)(const char* token, void* user_data);

// =====================================================================
// INIT — CPU pura con afinidad a Gold cores
// =====================================================================

extern "C" __attribute__((visibility("default")))
void* modula_init(const char* model_path,
                  int /*gpu_layers*/,   // Ignorado — CPU siempre
                  int ctx_size,
                  int threads) {

    llama_backend_init();

    ModulaCore* core       = new ModulaCore();
    int       total_cores = sysconf(_SC_NPROCESSORS_ONLN);

    // En Snapdragon/Dimensity el layout es [Silver..Gold..Prime]
    // Tomamos los últimos N cores — siempre los más potentes
    int t = (threads > 0) ? threads : 3;
    t = std::min(t, total_cores);

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = total_cores - t; i < total_cores; i++) {
        if (i >= 0) CPU_SET(i, &cpuset);
    }
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);

    __android_log_print(ANDROID_LOG_INFO, TAG,
        "CPU affinity: %d hilos en cores [%d..%d]",
        t, total_cores - t, total_cores - 1);

    // Modelo — siempre 0 capas GPU
    struct llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    // NOTA (2026-11-06, build en Linux): en el llama.cpp actual
    // use_mmap/use_mlock ya no existen; se reemplazaron por load_mode.
    // LLAMA_LOAD_MODE_MMAP = mmap sí, mlock no — exactamente lo que
    // hacía el código original de Jorge (use_mmap=true, use_mlock=false).
    mparams.load_mode    = LLAMA_LOAD_MODE_MMAP;

    core->model = llama_model_load_from_file(model_path, mparams);
    if (!core->model) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "Error cargando modelo: %s", model_path);
        delete core;
        return nullptr;
    }

    // Contexto optimizado para CPU
    struct llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = ctx_size;
    cparams.n_batch         = 512;
    cparams.n_threads       = t;
    cparams.n_threads_batch = t;
    cparams.embeddings      = false;  // Solo generación, no embeddings
    // ── NUEVO: Cuantización del KV Cache ──────────────────────────
    cparams.type_k = GGML_TYPE_Q8_0;  // Keys en 8-bit
    cparams.type_v = GGML_TYPE_Q8_0;  // Values en 8-bit

    core->ctx = llama_init_from_model(core->model, cparams);
    if (!core->ctx) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "Error creando contexto (ctx=%d)", ctx_size);
        llama_model_free(core->model);
        delete core;
        return nullptr;
    }

    core->n_past = 0;

    __android_log_print(ANDROID_LOG_INFO, TAG,
        "✅ Motor listo — CPU pura | ctx=%d | hilos=%d | NEON+dotprod",
        ctx_size, t);

    return core;
}

// =====================================================================
// GENERATE — Streaming con sliding window de contexto
// =====================================================================

extern "C" __attribute__((visibility("default")))
bool modula_generate(void* handle,
                     const char* prompt,
                     float temp,
                     int max_tokens,
                     modula_token_cb callback,
                     void* user_data) {

    if (!handle) return false;
    ModulaCore* core = (ModulaCore*)handle;

    const struct llama_vocab* vocab = llama_model_get_vocab(core->model);

    // Tokenizar el prompt
    std::vector<llama_token> prompt_tokens(strlen(prompt) + 32);
    int n_tokens = llama_tokenize(
        vocab, prompt, (int)strlen(prompt),
        prompt_tokens.data(), (int)prompt_tokens.size(),
        true, true
    );
    if (n_tokens < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
            "Error tokenizando prompt");
        return false;
    }
    prompt_tokens.resize(n_tokens);

    int max_ctx = llama_n_ctx(core->ctx);
    if (n_tokens >= max_ctx - 10) {
        __android_log_print(ANDROID_LOG_WARN, TAG,
            "Prompt demasiado largo (%d tokens, max=%d)", n_tokens, max_ctx);
        return false;
    }

    // Procesar prompt en chunks de 512 tokens
    const int n_batch = 512;
    llama_batch batch = llama_batch_init(n_batch, 0, 1);

    for (int i = 0; i < n_tokens; i += n_batch) {
        int chunk = std::min(n_batch, n_tokens - i);
        batch.n_tokens = 0;
        for (int j = 0; j < chunk; j++) {
            bool is_last = (i + j == n_tokens - 1);
            batch_add(batch, prompt_tokens[i + j], core->n_past++, is_last);
        }
        if (llama_decode(core->ctx, batch) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,
                "Error en llama_decode (prefill)");
            llama_batch_free(batch);
            return false;
        }
    }

    // Sampler: top-k → top-p → temperatura → distribución
    struct llama_sampler* smpl =
        llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    int         limit       = (max_tokens <= 0) ? 999999 : max_tokens;
    int         n_keep      = (int)prompt_tokens.size();
    std::string text_buffer = "";

    for (int i = 0; i < limit; i++) {

        // Sliding window — cuando el contexto está lleno descartamos
        // la mitad de los tokens generados preservando el prompt
        if (core->n_past >= max_ctx - 4) {
            int n_discard = (core->n_past - n_keep) / 2;
            if (n_discard > 0) {
                llama_memory_t mem = llama_get_memory(core->ctx);
                llama_memory_seq_rm (mem, 0, n_keep, n_keep + n_discard);
                llama_memory_seq_add(mem, 0, n_keep + n_discard,
                                     core->n_past, -n_discard);
                core->n_past -= n_discard;
                __android_log_print(ANDROID_LOG_DEBUG, TAG,
                    "Sliding window: descartados %d tokens", n_discard);
            }
        }

        // Muestrear siguiente token
        llama_token token_id = llama_sampler_sample(smpl, core->ctx, -1);
        llama_sampler_accept(smpl, token_id);

        // Fin de secuencia
        if (llama_vocab_is_eog(vocab, token_id)) break;

        // Convertir token a texto
        char buf[128];
        int  n = llama_token_to_piece(vocab, token_id, buf, sizeof(buf), 0, true);
        if (n > 0) {
            text_buffer.append(buf, n);
            // Solo enviar cuando el carácter UTF-8 está completo
            if (is_complete_utf8(text_buffer)) {
                if (callback) callback(text_buffer.c_str(), user_data);
                text_buffer.clear();
            }
        }

        // Decode del token generado
        batch.n_tokens = 0;
        batch_add(batch, token_id, core->n_past++, true);
        if (llama_decode(core->ctx, batch) != 0) break;
    }

    // Vaciar buffer si quedó algo
    if (!text_buffer.empty() && callback) {
        callback(text_buffer.c_str(), user_data);
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
    return true;
}

// =====================================================================
// TRIM — Limpiar KV cache entre sesiones
// =====================================================================

extern "C" __attribute__((visibility("default")))
void modula_trim_memory(void* handle) {
    if (!handle) return;
    ModulaCore* core = (ModulaCore*)handle;
    if (core->ctx) {
        llama_memory_clear(llama_get_memory(core->ctx), true);
        core->n_past = 0;
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "KV cache limpiado");
    }
}

// =====================================================================
// DEINIT — Liberar recursos
// =====================================================================

extern "C" __attribute__((visibility("default")))
void modula_deinit(void* handle) {
    if (!handle) return;
    ModulaCore* core = (ModulaCore*)handle;
    if (core->ctx)   llama_free(core->ctx);
    if (core->model) llama_model_free(core->model);
    delete core;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Motor liberado");
}

// =====================================================================
// CONTEXT COUNT — Tokens procesados hasta ahora
// =====================================================================

extern "C" __attribute__((visibility("default")))
int modula_get_context_count(void* handle) {
    if (!handle) return 0;
    return ((ModulaCore*)handle)->n_past;
}

// =====================================================================
// PUENTE JNI — Kotlin ↔ C++
// =====================================================================

static void throw_java(JNIEnv* env, const char* msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls) env->ThrowNew(cls, msg);
}

// Callback bridge: C → Java
struct JniCbData {
    JNIEnv*   env;
    jobject   obj;
    jmethodID mid;
};

static void jni_cb(const char* token, void* user_data) {
    JniCbData* d  = (JniCbData*)user_data;
    jstring    js = d->env->NewStringUTF(token);
    d->env->CallVoidMethod(d->obj, d->mid, js);
    d->env->DeleteLocalRef(js);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_modulaappr1_ModulaEngine_initEngine(
        JNIEnv* env, jobject,
        jstring model_path, jint gpu_layers,
        jint ctx_size, jint threads) {

    const char* path   = env->GetStringUTFChars(model_path, nullptr);
    void*       handle = modula_init(path, (int)gpu_layers,
                                     (int)ctx_size, (int)threads);
    env->ReleaseStringUTFChars(model_path, path);

    if (!handle) throw_java(env, "modula_init falló");
    return (jlong)handle;
}

JNIEXPORT jboolean JNICALL
Java_com_modulaappr1_ModulaEngine_generateStreaming(
        JNIEnv* env, jobject,
        jlong handle, jstring prompt,
        jfloat temp, jint max_tokens, jobject callback) {

    const char* str = env->GetStringUTFChars(prompt, nullptr);

    JniCbData cb;
    cb.env = env;
    cb.obj = callback;
    cb.mid = env->GetMethodID(
                 env->GetObjectClass(callback),
                 "onToken", "(Ljava/lang/String;)V");

    bool ok = modula_generate((void*)handle, str,
                               (float)temp, (int)max_tokens,
                               jni_cb, &cb);
    env->ReleaseStringUTFChars(prompt, str);

    // if (!ok) throw_java(env, "modula_generate falló");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_modulaappr1_ModulaEngine_trimMemory(
        JNIEnv*, jobject, jlong handle) {
    modula_trim_memory((void*)handle);
}

JNIEXPORT void JNICALL
Java_com_modulaappr1_ModulaEngine_deinitEngine(
        JNIEnv*, jobject, jlong handle) {
    modula_deinit((void*)handle);
}

JNIEXPORT jint JNICALL
Java_com_modulaappr1_ModulaEngine_getContextCount(
        JNIEnv*, jobject, jlong handle) {
    return modula_get_context_count((void*)handle);
}

} // extern "C"
