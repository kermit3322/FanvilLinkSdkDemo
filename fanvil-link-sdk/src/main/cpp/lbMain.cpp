#define LOG_TAG "lbMain"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include "LB/include/lb.h"

/**
 * TODO 动态注册
*/

/**
 * 对应java类的全路径名，.用/代替
 */
const char *classPathName = "com/fanvil/link/sdk/sip/LoopBackManager";

//Java虚拟机实例指针
JavaVM *gJavaVM = NULL;

//RinoCallManager源码结构体
typedef struct {
    jclass clazz;
    jobject globalObject;
    jweak weakObject;

    jmethodID onMqttMessageCallback;
} LoopBackManagerClassInfo;

static LoopBackManagerClassInfo loopBackManagerClassInfo;

//jni方法结构体
typedef struct jniMethod {
    const char *className;
    const char *methodName;
    const char *methodType;
    jmethodID *jmethod;
} jniMethod_t;


JNIEnv *get_env(JavaVM *vm, int *attach) {
    if (vm == NULL) return NULL;
    JNIEnv *jni_env = NULL;
    int status = vm->GetEnv((void **) &jni_env, JNI_VERSION_1_6);

    if (status == JNI_EDETACHED || jni_env == NULL) {
        status = vm->AttachCurrentThread(&jni_env, NULL);
        if (status < 0) {
            jni_env = NULL;
        } else {
            *attach = 1;
        }
    }
    return jni_env;
}

int mqttLinkCheck()
{
    LOGD("%s:mqttcb", __FUNCTION__);
    return 1;
}

int mqttSend(char *data, lbSipHeader_t *sipHeader)
{
    if (NULL != sipHeader) {
        LOGD("%s:mqttcb from:%s,to:%s,callID:%s,method:%d ", __FUNCTION__, sipHeader->from,
             sipHeader->to, sipHeader->callID, sipHeader->method);
    } else {
        LOGD("%s:mqttcb data:%s ", __FUNCTION__, data);
    }

    int attach = 0;
    jstring jmessage = NULL;
    jstring jfrom = NULL;
    jstring jto = NULL;
    jstring jcallID = NULL;
    int method = 0;
    JNIEnv *env = get_env(gJavaVM, &attach);
    if (env == NULL) {
        LOGE("%s: JNIEnv is NULL.", __FUNCTION__);
        return 0;
    }
    if (NULL != sipHeader && NULL != sipHeader->from) {
        jfrom = env->NewStringUTF(sipHeader->from);
    }
    if (NULL != sipHeader && NULL != sipHeader->to) {
        jto = env->NewStringUTF(sipHeader->to);
    }
    if (NULL != sipHeader && NULL != sipHeader->callID) {
        jcallID = env->NewStringUTF(sipHeader->callID);
    }
    if (NULL != sipHeader && NULL != sipHeader->method) {
        method = sipHeader->method;
    }
    if (NULL != data) {
        jmessage = env->NewStringUTF(data);
        env->CallVoidMethod(loopBackManagerClassInfo.globalObject,
                            loopBackManagerClassInfo.onMqttMessageCallback, jmessage, jfrom, jto, jcallID, method);
    }
    if (NULL != jfrom) {
        env->DeleteLocalRef(jfrom);
    }
    if (NULL != jto) {
        env->DeleteLocalRef(jto);
    }
    if (NULL != jmessage) {
        env->DeleteLocalRef(jmessage);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
nativeLbInit(JNIEnv *env, jobject obj, jint jlistenPort) {
    LOGD("%s: listenPort: %d", __FUNCTION__, jlistenPort);
    int status = lbInit(jlistenPort);
    lbMqttCB_t cb;
    cb.mqttLinkCheck = mqttLinkCheck;
    cb.mqttSend = mqttSend;
    lbMqttCallBackRegister(&cb);
    return status;
}

extern "C" JNIEXPORT jint JNICALL
nativeLbUnInit(JNIEnv *env, jobject obj) {
    LOGD("%s:", __FUNCTION__);
    int status = lbUnInit();
    return status;
}

extern "C" JNIEXPORT jint JNICALL
nativeMqttMsgIncoming(JNIEnv *env, jobject obj, jstring jmessage) {
    char *message = (NULL != jmessage) ? (char *) env->GetStringUTFChars(jmessage, NULL) : NULL;
    lbMqttMsgIncoming(message, strlen(message));
    LOGD("%s: message: %s size:%d", __FUNCTION__, message, strlen(message));
    return 1;
}

extern "C" JNIEXPORT jint JNICALL
nativeMqttRegStatus(JNIEnv *env, jobject obj, jint regStatus) {
    LOGD("%s: regStatus: %d", __FUNCTION__, regStatus);
    return 1;
}

//初始化函数,注册native和java方法
extern "C" JNIEXPORT jint JNICALL
nativeInitClass(JNIEnv *env, jobject obj) {
    LOGD("%s:", __FUNCTION__);
    loopBackManagerClassInfo.globalObject = env->NewGlobalRef(obj);
    loopBackManagerClassInfo.weakObject = env->NewWeakGlobalRef(obj);
    if (NULL == loopBackManagerClassInfo.globalObject) {
        LOGD("%s: failed!", __FUNCTION__);
        env->DeleteLocalRef(obj);
        return JNI_ERR;
    }
    return JNI_OK;
}

/**
 * native对应注册方法,需要再次扩充相关方法
 */
static const JNINativeMethod jniNativeMethod[] =
        {
                {"nativeInitClass",       "()I",                   (void *) (nativeInitClass)},
                {"nativeLbInit",          "(I)I",                  (void *) (nativeLbInit)},
                {"nativeLbUnInit",        "()I",                   (void *) (nativeLbUnInit)},
                {"nativeMqttMsgIncoming", "(Ljava/lang/String;)I", (void *) (nativeMqttMsgIncoming)},
                {"nativeMqttRegStatus",   "(I)I",                  (void *) (nativeMqttRegStatus)}
        };

/**
 *回调Java上层方法
 */
static jniMethod_t jniJavaMethods[] =
        {
                {classPathName, "onMqttMessageCallback", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V", &loopBackManagerClassInfo.onMqttMessageCallback}
        };

/**
 * jni方法的绑定
 */
jint jniFindMethods(JNIEnv *env, jniMethod_t *methods, int count) {
    LOGD("%s:", __FUNCTION__);
    int i = 0;
    if (NULL == env) {
        LOGE("%s: JNIEnv is null.", __FUNCTION__);
        return JNI_ERR;
    }

    if (NULL == methods) {
        LOGE("%s: jniMethod_t is null.", __FUNCTION__);
        return JNI_ERR;
    }

    for (i = 0; i < count; i++) {
        jniMethod_t *f = &methods[i];
        jclass clazz = env->FindClass(f->className);
        if (NULL == clazz) {
            LOGE("%s: Can't find %s", __FUNCTION__, f->className);
            return JNI_ERR;
        }

        jmethodID method = env->GetMethodID(clazz, f->methodName, f->methodType);
        if (NULL == method) {
            LOGE("%s: Can't find %s.%s", __FUNCTION__, f->className, f->methodName);
            return JNI_ERR;
        }
        *(f->jmethod) = method;
        env->DeleteLocalRef(clazz);
    }
    return JNI_OK;
}

/**
 *开始注册native接口
 */
jint registerNativeMethod(JNIEnv *env) {
    jclass rinoCallManagerClass = env->FindClass(classPathName);
    env->RegisterNatives(rinoCallManagerClass, jniNativeMethod,
                         sizeof(jniNativeMethod) / sizeof(JNINativeMethod));//动态注册的数量
    jniFindMethods(env, jniJavaMethods, NELEM(jniJavaMethods));
    if (NULL == rinoCallManagerClass) {
        env->DeleteLocalRef(rinoCallManagerClass);
        return JNI_ERR;
    }
    return JNI_OK;
}

void setJavaVM(JavaVM *vm) {
    gJavaVM = vm;
}

/**
 * 这个函数会在库被加载的时候被调用
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("%s:", __FUNCTION__);
    JNIEnv *jniEnv = get_env(vm, 0);
    if (jniEnv == NULL) {
        LOGE("%s: JNIEnv is NULL.", __FUNCTION__);
        return JNI_ERR;
    }
    setJavaVM(vm);
    registerNativeMethod(jniEnv);
    return JNI_VERSION_1_6;
}