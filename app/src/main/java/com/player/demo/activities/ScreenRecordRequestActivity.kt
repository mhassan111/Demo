package com.player.demo.activities

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import com.player.demo.R
import com.player.demo.services.ScreenCaptureService
import com.player.demo.util.Util


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenRecordRequestActivity : Activity() {

    private val mMediaProjection: MediaProjection? = null
    private var mProjectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_record_request)
        mProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (mMediaProjection == null)
            startActivityForResult(mProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            finish()
            return
        }
        if (resultCode != RESULT_OK) {
            finish()
            return
        }
        Util.screenRecordIntent = data
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 100
    }
}
