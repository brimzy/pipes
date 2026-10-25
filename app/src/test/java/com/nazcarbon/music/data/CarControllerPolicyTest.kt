package com.nazcarbon.music.data

import org.junit.Assert.*
import org.junit.Test

class CarControllerPolicyTest {
    @Test fun `a claimed Auto package is insufficient without UID and Google signature checks`() {
        val name = CarControllerPolicy.ANDROID_AUTO_PACKAGE
        assertFalse(CarControllerPolicy.canBrowse(false, false, true, name, false, true))
        assertFalse(CarControllerPolicy.canBrowse(false, false, true, name, true, false))
        assertFalse(CarControllerPolicy.canBrowse(false, false, true, "spoof", true, true))
        assertTrue(CarControllerPolicy.canBrowse(false, false, true, name, true, true))
    }
    @Test fun `only trusted car hosts receive browsing rather than arbitrary trusted controllers`() {
        assertFalse(CarControllerPolicy.canBrowse(true, false, false, "notification.listener", true, false))
        assertFalse(CarControllerPolicy.canBrowse(false, true, false, "car.host", true, false))
        assertTrue(CarControllerPolicy.canBrowse(true, true, false, "car.host", true, false))
    }
}
