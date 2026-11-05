# NewPipeExtractor utilise Rhino pour déchiffrer les signatures YouTube
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-keep class org.schabi.newpipe.extractor.** { *; }

# youtubedl-android
-keep class com.yausername.** { *; }

# youtubedl-android lit la sortie JSON de yt-dlp avec Jackson (réflexion sur les champs et les génériques)
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# youtubedl-android décompresse Python / ffmpeg avec Apache Commons Compress, qui crée ses classes
# par réflexion (champs « extra » des zip) : R8 les cassait (« class … is not a concrete class »,
# ExceptionInInitializerError → « yt-dlp n'a pas pu être installé », 2.6.0)
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
