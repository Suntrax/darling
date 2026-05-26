package com.blissless.anime.stream

import android.content.Context
import dalvik.system.DexClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import com.blissless.anime.extensions.Extension

class ParentFirstClassLoader(apkPath: String, dexOutput: String, parent: ClassLoader) :
    DexClassLoader(apkPath, dexOutput, null, parent) {

    private val parentFirstPackages = emptyList<String>()

    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        for (pkg in parentFirstPackages) {
            if (name.startsWith(pkg)) {
                return try {
                    parent.loadClass(name)
                } catch (_: ClassNotFoundException) {
                    super.loadClass(name, resolve)
                }
            }
        }
        return super.loadClass(name, resolve)
    }
}

class ExtensionLoader(private val context: Context) {

    fun loadSources(extension: Extension): List<AnimeCatalogueSource> {
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(extension.packageName, 0)
        val apkPath = ai.sourceDir
        val dexOutput = context.codeCacheDir

        val loader = ParentFirstClassLoader(
            apkPath,
            dexOutput.absolutePath,
            context.classLoader
        )

        val sourceClass = extension.sourceClass
        if (sourceClass == null) {
            return emptyList()
        }

        val sourceClassName = if (sourceClass.startsWith(".")) {
            val pkg = pm.getPackageInfo(extension.packageName, 0).packageName
            "$pkg$sourceClass"
        } else {
            sourceClass
        }

        return try {
            val clazz = loader.loadClass(sourceClassName)
            when {
                AnimeSourceFactory::class.java.isAssignableFrom(clazz) -> {
                    val factory = clazz.getDeclaredConstructor().newInstance() as AnimeSourceFactory
                    factory.createSources().filterIsInstance<AnimeCatalogueSource>()
                }
                AnimeCatalogueSource::class.java.isAssignableFrom(clazz) -> {
                    val source = clazz.getDeclaredConstructor().newInstance() as AnimeCatalogueSource
                    listOf(source)
                }
                else -> {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
