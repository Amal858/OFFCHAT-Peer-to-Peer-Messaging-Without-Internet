-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.offchat.bluetooth.** { *; }
-keep class com.offchat.data.model.** { *; }
-keep class com.offchat.data.db.** { *; }
