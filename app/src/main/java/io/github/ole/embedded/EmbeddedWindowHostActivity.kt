package io.github.ole.embedded

import android.os.Bundle
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManagerGlobal
import androidx.appcompat.app.AppCompatActivity
import io.github.ole.builtin.BuiltInContracts
import io.github.ole.builtin.IBuiltIn

class EmbeddedWindowHostActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EmbeddedWindowHostActivity"
    }

    private var mLocalMirrorView: SurfaceView? = null
    private var mRemoteDisplayView: SurfaceView? = null
    private var mLocalMirrorDisplay: SurfaceControl? = null
    private var mLocalMirrorControl: SurfaceControl? = null
    private var mRemoteDisplayHolder: SurfaceHolder? = null
    private var mRemoteDisplayControl: SurfaceControl? = null
    private var mRemoteDisplayAttached: Boolean = false
    private var mBuiltInService: IBuiltIn? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedded_host)
        findViewById<ViewGroup>(android.R.id.content).fitsSystemWindows = true

        mLocalMirrorView = findViewById(R.id.remote_view)
        mRemoteDisplayView = findViewById(R.id.remote_surface)
        mRemoteDisplayView?.setOnClickListener {
            Log.i(TAG, "User click $mRemoteDisplayAttached")
            if (mRemoteDisplayAttached) {
                unloadRemoteDisplay()
            } else {
                loadRemoteDisplay()
            }
        }

        mLocalMirrorView?.setZOrderOnTop(true)
        mLocalMirrorView?.holder?.addCallback(mLocalMirrorViewHolder)

        mRemoteDisplayView?.setZOrderOnTop(true)
        mRemoteDisplayView?.holder?.addCallback(mRemoteDisplayViewHolder)

        val binder = ServiceManager.getService("builtin")
        if (binder == null) {
            Log.d(TAG, "Cannot find builtin")
        }
        mBuiltInService = IBuiltIn.Stub.asInterface(binder)
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

    private val mRemoteDisplayViewHolder: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            mRemoteDisplayControl = mRemoteDisplayView!!.surfaceControl
            mRemoteDisplayHolder = holder
            loadRemoteDisplay()
        }

        override fun surfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int,
            height: Int
        ) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            clearRemoteDisplay()
            mRemoteDisplayHolder = null
            mRemoteDisplayControl = null
        }
    }

    private fun clearRemoteDisplay() {
        try {
            val result = mBuiltInService?.performAction(
                BuiltInContracts.VirtualDisplay.ID,
                BuiltInContracts.VirtualDisplay.ACTION_RESET,
                Bundle.EMPTY
            )
            mRemoteDisplayAttached = false
            Log.w(TAG, "Result of reset display surface $result")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to detach embedded SurfaceControl", e)
        }
    }

    private fun unloadRemoteDisplay() {
        try {
            val result = mBuiltInService?.performAction(
                BuiltInContracts.VirtualDisplay.ID,
                BuiltInContracts.VirtualDisplay.ACTION_REPARENT,
                Bundle.EMPTY
            )
            mRemoteDisplayAttached = result != true
            Log.w(TAG, "Result of reset display surface $result")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to detach embedded SurfaceControl", e)
        }
    }

    private fun loadRemoteDisplay() {
        if (mRemoteDisplayControl == null || mRemoteDisplayHolder == null) {
            return
        }
        try {
            val options = Bundle().apply {
                putParcelable(BuiltInContracts.VirtualDisplay.ARG_PARENT_SC, mRemoteDisplayHolder!!.surface)
            }
            val result = mBuiltInService?.performAction(
                BuiltInContracts.VirtualDisplay.ID,
                BuiltInContracts.VirtualDisplay.ACTION_REPARENT,
                options
            )
            mRemoteDisplayAttached = result == true
            Log.w(TAG, "Result of reparent display surface $result")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to attach embedded SurfaceControl", e)
        }
    }
}
