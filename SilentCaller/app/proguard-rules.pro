# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/android-sdk/tools/proguard/proguard-android.txt

# Preserve Room entities
-keep class com.silentcaller.model.** { *; }
-keep interface com.silentcaller.database.** { *; }

# Keep the receivers
-keep class com.silentcaller.receiver.** { *; }
