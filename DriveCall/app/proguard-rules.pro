# Keep DriveCall application class
-keep class com.drivecall.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Android SpeechRecognizer
-keep class android.speech.** { *; }
