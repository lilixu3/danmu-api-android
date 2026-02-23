#include <jni.h>
#include <string>
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include "node.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_example_danmuapiapp_NodeBridge_startNodeWithArguments(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray arguments) {

    int argc = env->GetArrayLength(arguments);
    char **argv = new char *[argc];

    for (int i = 0; i < argc; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        int len = strlen(str);
        argv[i] = new char[len + 1];
        strcpy(argv[i], str);
        env->ReleaseStringUTFChars(jstr, str);
    }

    int result = node::Start(argc, argv);

    for (int i = 0; i < argc; i++) {
        delete[] argv[i];
    }
    delete[] argv;

    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_danmuapiapp_NodeBridge_setEnvironmentVariable(
        JNIEnv *env,
        jobject /* this */,
        jstring name,
        jstring value,
        jboolean overwrite) {
    if (name == nullptr || value == nullptr) {
        return JNI_FALSE;
    }

    const char *nameChars = env->GetStringUTFChars(name, nullptr);
    const char *valueChars = env->GetStringUTFChars(value, nullptr);
    if (nameChars == nullptr || valueChars == nullptr) {
        if (nameChars != nullptr) {
            env->ReleaseStringUTFChars(name, nameChars);
        }
        if (valueChars != nullptr) {
            env->ReleaseStringUTFChars(value, valueChars);
        }
        return JNI_FALSE;
    }

    const int rc = setenv(nameChars, valueChars, overwrite ? 1 : 0);
    env->ReleaseStringUTFChars(name, nameChars);
    env->ReleaseStringUTFChars(value, valueChars);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_danmuapiapp_NodeBridge_changeWorkingDirectory(
        JNIEnv *env,
        jobject /* this */,
        jstring path) {
    if (path == nullptr) {
        return JNI_FALSE;
    }

    const char *pathChars = env->GetStringUTFChars(path, nullptr);
    if (pathChars == nullptr) {
        return JNI_FALSE;
    }

    const int rc = chdir(pathChars);
    env->ReleaseStringUTFChars(path, pathChars);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}
