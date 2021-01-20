#include <jni.h>
#include <string>

extern "C"
jstring
Java_vn_tek4tv_radioip_ui_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
