package com.blissless.anime.extensions

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

class ExtensionDetector(private val context: Context) {

    @Suppress("DEPRECATION")
    private val packageFlags = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES else 0)

    fun detectInstalledExtensions(): List<Extension> {
        return try {
            val pm = context.packageManager
            val installedPackages = getInstalledPackages(pm)
            installedPackages
                .filter { isExtension(it) }
                .map { toExtension(it, pm) }
                .sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getInstalledPackages(pm: PackageManager): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(packageFlags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(packageFlags)
        }
    }

    private fun isExtension(pkgInfo: PackageInfo): Boolean {
        try {
            if (pkgInfo.reqFeatures.orEmpty().any { it.name != null && it.name in EXTENSION_FEATURES }) {
                return true
            }
            val metaData = pkgInfo.applicationInfo?.metaData ?: return false
            return metaData.containsKey(METADATA_SOURCE_CLASS) ||
                    metaData.containsKey(METADATA_ANIME_SOURCE_CLASS) ||
                    metaData.containsKey(METADATA_SOURCE_FACTORY)
        } catch (_: Exception) {
            return false
        }
    }

    private fun toExtension(pkgInfo: PackageInfo, pm: PackageManager): Extension {
        val ai = pkgInfo.applicationInfo ?: return createFallbackExtension(pkgInfo)
        val metaData = ai.metaData
        val sourceClass = metaData?.getString(METADATA_SOURCE_CLASS)
            ?: metaData?.getString(METADATA_ANIME_SOURCE_CLASS)
            ?: metaData?.getString(METADATA_SOURCE_FACTORY)

        return Extension(
            packageName = pkgInfo.packageName,
            name = pm.getApplicationLabel(ai)?.toString() ?: pkgInfo.packageName,
            versionName = pkgInfo.versionName ?: "",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            icon = ai.loadIcon(pm),
            sourceClass = sourceClass,
            isNsfw = isMetadataTrue(metaData, METADATA_NSFW) ||
                    isMetadataTrue(metaData, METADATA_ANIME_NSFW),
            isInstalled = true,
            installTime = pkgInfo.firstInstallTime
        )
    }

    private fun createFallbackExtension(pkgInfo: PackageInfo): Extension {
        return Extension(
            packageName = pkgInfo.packageName,
            name = pkgInfo.packageName,
            versionName = pkgInfo.versionName ?: "",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            },
            icon = null,
            sourceClass = null,
            isNsfw = false,
            isInstalled = true,
            installTime = pkgInfo.firstInstallTime
        )
    }

    private fun isMetadataTrue(metaData: android.os.Bundle?, key: String): Boolean {
        if (metaData == null) return false
        return try {
            metaData.getBoolean(key)
        } catch (_: ClassCastException) {
            try {
                metaData.getInt(key) != 0
            } catch (_: ClassCastException) {
                false
            }
        }
    }
}
