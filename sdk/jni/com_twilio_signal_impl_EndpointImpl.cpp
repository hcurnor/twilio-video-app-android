/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_twilio_signal_impl_EndpointImpl */

#include "com_twilio_signal_impl_EndpointImpl.h"
#include "TSCoreSDKTypes.h"
#include "TSCEndpoint.h"

using namespace twiliosdk;
/*
 * Class:     com_twilio_signal_impl_EndpointImpl
 * Method:    listen
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_twilio_signal_impl_EndpointImpl_listen
  (JNIEnv *env, jobject obj, jlong nativeEndpoint) {

	reinterpret_cast<TSCEndpointObject*>(nativeEndpoint)->registerEndpoint();
}

