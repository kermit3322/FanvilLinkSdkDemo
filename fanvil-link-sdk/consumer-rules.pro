-keep class com.fanvil.link.sdk.** { *; }
-keep class com.fanvil.link.sdk.sip.LoopBackManager { *; }
-keepclassmembers class com.fanvil.link.sdk.sip.LoopBackManager {
  void onMqttMessageCallback(java.lang.String, java.lang.String, java.lang.String, java.lang.String, int);
}
