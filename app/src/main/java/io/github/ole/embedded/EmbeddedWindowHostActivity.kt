package io.github.ole.embedded

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManagerGlobal
import androidx.appcompat.app.AppCompatActivity

class EmbeddedWindowHostActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EmbeddedWindowHostActivity"
    }

    private var mLocalMirrorView: SurfaceView? = null
    private var mRemoteMirrorView: SurfaceView? = null
    private var mLocalMirrorDisplay: SurfaceControl? = null
    private var mLocalMirrorControl: SurfaceControl? = null
    private var mRemoteMirrorControl: SurfaceControl? = null
    private var mIAttachEmbeddedWindow: IAttachEmbeddedWindow? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        // Called when the connection with the service is established
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            mIAttachEmbeddedWindow = IAttachEmbeddedWindow.Stub.asInterface(service)
            loadRemoteMirror()
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            Log.d(TAG, "Service Disconnected")
            mIAttachEmbeddedWindow = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedded_host)
        findViewById<ViewGroup>(android.R.id.content).fitsSystemWindows = true

        mLocalMirrorView = findViewById(R.id.remote_view)
        mRemoteMirrorView = findViewById(R.id.remote_surface)

        mLocalMirrorView?.setZOrderOnTop(true)
        mLocalMirrorView?.holder?.addCallback(mLocalMirrorViewHolder)

        mRemoteMirrorView?.setZOrderOnTop(true)
        mRemoteMirrorView?.holder?.addCallback(mRemoteMirrorViewHolder)

        val intent = Intent(this, EmbeddedWindowService::class.java)
        intent.setAction(IAttachEmbeddedWindow::class.java.getName())
        Log.d(TAG, "binding service")
        bindService(intent, mConnection, BIND_AUTO_CREATE)
    }


    override fun onDestroy() {
        unbindService(mConnection)
        super.onDestroy()
    }

    private val mLocalMirrorViewHolder: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            mLocalMirrorControl = mLocalMirrorView!!.surfaceControl
            loadLocalMirror()
        }

        override fun surfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int,
            height: Int
        ) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            mLocalMirrorDisplay?.let {
                SurfaceControl.Transaction().remove(it).apply()
            }
            mLocalMirrorDisplay = null
            mLocalMirrorControl = null
        }
    }

    private fun loadLocalMirror() {
        if (mLocalMirrorControl == null) {
            return
        }
        mLocalMirrorDisplay = SurfaceControl()
        val success: Boolean = try {
            WindowManagerGlobal.getWindowManagerService()!!
                .mirrorDisplay(2, mLocalMirrorDisplay)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to mirror display ", e)
            false
        }
        Log.i(TAG, "mirrorDisplay " + success + " valid " + mLocalMirrorDisplay?.isValid)
        if (success) {
            mLocalMirrorDisplay?.takeIf { it.isValid }?.let {
                SurfaceControl.Transaction().show(it)
                    .reparent(it, mLocalMirrorControl)
                    .apply()
            }
        }
    }

    private val mRemoteMirrorViewHolder: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            mRemoteMirrorControl = mRemoteMirrorView!!.surfaceControl
            loadRemoteMirror()
        }

        override fun surfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int,
            height: Int
        ) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            try {
                mIAttachEmbeddedWindow?.tearDownEmbeddedSurfaceControl()
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to tear down embedded SurfaceControl", e)
            }
            mRemoteMirrorControl = null
        }
    }

    private fun loadRemoteMirror() {
        if (mRemoteMirrorControl == null) {
            return
        }
        try {
            mIAttachEmbeddedWindow?.attachEmbeddedSurfaceControl(
                mRemoteMirrorControl, 2,
                mRemoteMirrorView!!.getRootSurfaceControl()!!.inputTransferToken
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to load embedded SurfaceControl", e)
        }
    }
}
