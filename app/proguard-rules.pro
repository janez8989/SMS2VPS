# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep JSch classes to prevent ClassNotFoundException
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Keep Google API classes
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-keep class com.google.auth.** { *; }

# Keep Gson classes
-keep class com.google.gson.** { *; }

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Retrofit classes
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Ignore desktop/server classes that aren't available on Android
-dontwarn com.sun.net.httpserver.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Keep Google OAuth classes but ignore desktop dependencies
-keep class com.google.api.client.extensions.jetty.** { *; }
-keep class com.google.api.client.extensions.java6.** { *; }
-dontwarn com.google.api.client.extensions.jetty.**
-dontwarn com.google.api.client.extensions.java6.**

# Keep Apache HTTP classes but ignore missing desktop dependencies
-keep class org.apache.http.** { *; }
-dontwarn org.apache.http.**
