#include <jni.h>
#include <string>
#include <cstdlib>
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
