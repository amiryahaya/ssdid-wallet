package my.ssdid.wallet.ui.components

import android.view.HapticFeedbackConstants
import android.view.View

object HapticManager {
    fun success(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun error(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    fun selection(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
