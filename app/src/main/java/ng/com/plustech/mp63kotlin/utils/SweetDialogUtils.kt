package ng.com.plustech.mp63kotlin.utils

import android.app.Activity
import android.graphics.Color
import cn.pedant.SweetAlert.SweetAlertDialog

object SweetDialogUtils {
    private var pDialog: SweetAlertDialog? = null
    fun showNormal(context: Activity, contextText: String?) {
        context.runOnUiThread {
            SweetAlertDialog(context, SweetAlertDialog.NORMAL_TYPE)
                .setContentText(contextText)
                .show()
        }
    }

    fun showSuccess(context: Activity, contextText: String?) {
        context.runOnUiThread {
            SweetAlertDialog(context, SweetAlertDialog.SUCCESS_TYPE)
                .setContentText(contextText)
                .show()
        }
    }

    fun showError(context: Activity, contextText: String?) {
        context.runOnUiThread {
            SweetAlertDialog(context, SweetAlertDialog.ERROR_TYPE)
                .setContentText(contextText)
                .show()
        }
    }

    fun showProgress(context: Activity, text: String?, cancelable: Boolean) {
        context.runOnUiThread {
            pDialog = SweetAlertDialog(context, SweetAlertDialog.PROGRESS_TYPE)
            pDialog!!.progressHelper.barColor =
                Color.parseColor("#A5DC86")
            pDialog!!.titleText = text
            pDialog!!.setCancelable(cancelable)
            pDialog!!.show()
        }
    }

    fun changeAlertType(activity: Activity, text: String?, type: Int) {
        activity.runOnUiThread {
            if (pDialog != null) {
                pDialog!!.changeAlertType(type)
                pDialog!!.titleText = text
            }
            pDialog = null
        }
    }

    fun setProgressText(activity: Activity, text: String?) {
        activity.runOnUiThread { pDialog!!.titleText = text }
    }

    fun cancel(activity: Activity) {
        if(pDialog != null) {
            pDialog!!.cancel()
        }
    }
}
