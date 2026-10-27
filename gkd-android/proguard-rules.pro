# http://developer.android.com/guide/developing/tools/proguard.html

-dontwarn **

# Rhino JS 引擎(P3 flow.js): 运行时反射+自举加载自身类, 禁止混淆/裁剪
-keep class org.mozilla.javascript.** { *; }
-keepclassmembers class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-keepattributes InnerClasses,Signature,**Annotation*
