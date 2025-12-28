\
#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdlib>
#include "node.h"

// Entry: com.example.danmuapiapp.NodeBridge.startNodeWithArguments(String[] args)
//
// NOTE:
// The GitHub Actions workflow you provided will patch the node::Start call to cast away const:
//   return node::Start(argc, const_cast<char**>(argv.data()));
// So keep the original line (without const_cast) here on purpose.
extern "C"
JNIEXPORT jint JNICALL
Java_com_example_danmuapiapp_NodeBridge_startNodeWithArguments(
        JNIEnv *env,
        jobject /* this */,
        jobjectArray arguments) {

    const int argc = (int) env->GetArrayLength(arguments);

    // 1) Read all Java strings, compute total bytes for a contiguous C buffer.
    size_t totalBytes = 0;
    std::vector<std::string> args;
    args.reserve(argc);

    for (int i = 0; i < argc; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(arguments, i);
        const char* cstr = env->GetStringUTFChars(jstr, nullptr);
        args.emplace_back(cstr ? cstr : "");
        env->ReleaseStringUTFChars(jstr, cstr);
        totalBytes += args.back().size() + 1; // +1 for '\0'
    }

    // 2) Create one contiguous buffer holding all args.
    std::vector<char> buffer(totalBytes);
    std::vector<const char*> argv;
    argv.reserve(argc);

    char* cursor = buffer.data();
    for (const auto& s : args) {
        std::memcpy(cursor, s.c_str(), s.size());
        cursor[s.size()] = '\0';
        argv.push_back(cursor);
        cursor += s.size() + 1;
    }

    // 3) Start Node.js (blocks until Node exits)
    return node::Start(argc, argv.data());
}
