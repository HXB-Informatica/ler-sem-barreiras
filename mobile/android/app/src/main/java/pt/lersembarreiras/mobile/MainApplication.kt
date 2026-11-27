package pt.lersembarreiras.mobile

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(LsbNativePackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    LsbDiag.mark("app_onCreate", mapOf("memMb" to LsbDiag.memoryMb(this), "heapMb" to LsbDiag.javaHeapMb()))
    loadReactNative(this)
    LsbDiag.persist(this)
  }
}
