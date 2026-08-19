package com.example.myaac.hybrid.plugins

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.ByteArrayOutputStream

/**
 * Wraps PackageManager.queryIntentActivities()/getLaunchIntentForPackage() so a board
 * button can enumerate and launch other installed apps -- there is no web equivalent
 * and no existing Capacitor community plugin for this.
 *
 * Ported from the enumeration logic in the existing native app's AppPickerDialog.kt
 * (ACTION_MAIN + CATEGORY_LAUNCHER query, excluding this app's own package, sorted by label).
 */
@CapacitorPlugin(name = "AppLauncher")
class AppLauncherPlugin : Plugin() {

    companion object {
        private const val TAG = "AppLauncherPlugin"
        private const val ICON_MAX_DIMENSION_PX = 192
    }

    @PluginMethod
    fun listLaunchableApps(call: PluginCall) {
        val packageManager = context.packageManager
        val ownPackageName = context.packageName

        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val seenPackages = HashSet<String>()
        val apps = JSArray()

        resolveInfos
            .filter { it.activityInfo.packageName != ownPackageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (!seenPackages.add(packageName)) return@forEach

                val label = resolveInfo.loadLabel(packageManager).toString()
                val iconBase64 = try {
                    drawableToBase64Png(resolveInfo.loadIcon(packageManager))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to encode icon for $packageName", e)
                    null
                }

                val appObj = JSObject()
                appObj.put("packageName", packageName)
                appObj.put("label", label)
                if (iconBase64 != null) {
                    appObj.put("iconBase64", iconBase64)
                }
                apps.put(appObj)
            }

        val ret = JSObject()
        ret.put("apps", apps)
        call.resolve(ret)
    }

    private fun drawableToBase64Png(drawable: Drawable): String {
        val bitmap: Bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_MAX_DIMENSION_PX
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_MAX_DIMENSION_PX
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        val scaled = if (bitmap.width > ICON_MAX_DIMENSION_PX || bitmap.height > ICON_MAX_DIMENSION_PX) {
            Bitmap.createScaledBitmap(bitmap, ICON_MAX_DIMENSION_PX, ICON_MAX_DIMENSION_PX, true)
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    @PluginMethod
    fun launchApp(call: PluginCall) {
        val packageName = call.getString("packageName")
        if (packageName == null) {
            call.reject("packageName is required")
            return
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val ret = JSObject()
        if (launchIntent == null) {
            ret.put("success", false)
            call.resolve(ret)
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
            ret.put("success", true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            ret.put("success", false)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun isAppInstalled(call: PluginCall) {
        val packageName = call.getString("packageName")
        if (packageName == null) {
            call.reject("packageName is required")
            return
        }

        val installed = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val ret = JSObject()
        ret.put("installed", installed)
        call.resolve(ret)
    }
}
