#define _GNU_SOURCE
#include <jni.h>
#include <sched.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "ThreadAffinity-JNI"

JNIEXPORT void JNICALL
Java_com_autotarget_util_ThreadAffinityHelper_setThreadAffinityMask(
        JNIEnv *env, jclass cls, jint threadId, jint mask) {

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);

    for (int i = 0; i < 8; i++) {
        if (mask & (1 << i)) {
            CPU_SET(i, &cpuset);
        }
    }

    int result = sched_setaffinity((pid_t) threadId, sizeof(cpu_set_t), &cpuset);

    if (result == 0) {
        __android_log_print(ANDROID_LOG_INFO, TAG,
            "Thread %d afinidade definida: mask=0x%02X", threadId, mask);
    } else {
        __android_log_print(ANDROID_LOG_WARN, TAG,
            "Falha ao definir afinidade para thread %d: errno=%d", threadId, result);
    }
}
