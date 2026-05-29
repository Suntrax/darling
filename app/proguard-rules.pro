# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep,includedescriptorclasses class com.blissless.anime.**$$serializer { *; }
-keepclassmembers class com.blissless.anime.** {
    *** Companion;
}
-keepclasseswithmembers class com.blissless.anime.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal

# Keep data classes used with Gson
-keep class com.blissless.anime.data.models.** { *; }

# Keep Widget classes (WorkManager creates workers via reflection, Glance uses reflection for widgets)
-keep class com.blissless.anime.widget.** { *; }

# Keep WorkManager workers (instantiated via reflection)
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Keep Glance widget and receiver
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Keep Room generated implementations (WorkManager uses Room internally, _Impl classes accessed by reflection)
# Keep Room database implementations (WorkManager uses Room internally, generated _Impl classes need constructors)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }

# Keep Tachiyomi anime source framework (loaded dynamically via DexClassLoader)
-keep class eu.kanade.tachiyomi.animesource.** { *; }
-keep class eu.kanade.tachiyomi.network.** { *; }
-keep class eu.kanade.tachiyomi.util.** { *; }
-keep class eu.kanade.tachiyomi.AppInfo { *; }
-dontwarn eu.kanade.tachiyomi.**

# Keep all interfaces and models used by extension sources
-keep class * extends eu.kanade.tachiyomi.animesource.AnimeCatalogueSource { *; }
-keep class * implements eu.kanade.tachiyomi.animesource.AnimeSourceFactory { *; }
-keep class * extends eu.kanade.tachiyomi.animesource.online.AnimeHttpSource { *; }

# Keep model classes accessed reflectively by extensions
-keep class eu.kanade.tachiyomi.animesource.model.** { *; }

# Keep Kotlin serializers for all serializable classes
-keep,includedescriptorclasses class com.blissless.anime.widget.**$$serializer { *; }
-keepclassmembers class com.blissless.anime.widget.** {
    *** Companion;
}
-keepclasseswithmembers class com.blissless.anime.widget.** {
    kotlinx.serialization.KSerializer serializer(...);
}
