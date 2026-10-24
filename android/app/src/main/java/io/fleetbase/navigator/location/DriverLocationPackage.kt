package io.fleetbase.navigator.location

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class DriverLocationPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == DriverLocationModule.NAME) DriverLocationModule(reactContext) else null

    override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
        mapOf(DriverLocationModule.NAME to ReactModuleInfo(DriverLocationModule.NAME, DriverLocationModule.NAME, false, false, false, true))
    }
}
