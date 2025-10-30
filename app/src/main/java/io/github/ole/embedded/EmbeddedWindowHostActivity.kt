package io.github.ole.embedded

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class EmbeddedWindowHostActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "EmbeddedWindowHostActivity"
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var mRemoteRegularView: SurfaceView? = null
    private var mRemoteSurfaceView: SurfaceView? = null
    private var mRemoteRegularControl: SurfaceControl? = null
    private var mRemoteSurfaceControl: SurfaceControl? = null
    private var mIAttachEmbeddedWindow: IAttachEmbeddedWindow? = null
    private val mLock = Any()
    private var mIsAttached = false
    private var mSurfacePackage: SurfacePackage? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        // Called when the connection with the service is established
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            synchronized(mLock) {
                mIAttachEmbeddedWindow = IAttachEmbeddedWindow.Stub.asInterface(service)
            }
            loadEmbeddedSurface()
            loadEmbeddedRegular()
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

        mRemoteRegularView = findViewById(R.id.remote_view)
        mRemoteSurfaceView = findViewById(R.id.remote_surface)

        mRemoteRegularView?.setZOrderOnTop(true)
        mRemoteRegularView?.holder?.addCallback(mRemoteRegularViewHolder)

        mRemoteSurfaceView?.setZOrderOnTop(true)
        mRemoteSurfaceView?.holder?.addCallback(mRemoteSurfaceViewHolder)

        val intent = Intent(this, EmbeddedWindowService::class.java)
        intent.setAction(IAttachEmbeddedWindow::class.java.getName())
        Log.d(TAG, "binding service")
        bindService(intent, mConnection, BIND_AUTO_CREATE)
    }


    override fun onDestroy() {
        unbindService(mConnection)
        super.onDestroy()
    }

    private val mRemoteRegularViewHolder: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            synchronized(mLock) {
                mRemoteRegularControl = mRemoteRegularView!!.surfaceControl
            }
            attachEmbedded()
        }

        override fun surfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int,
            height: Int
        ) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            mRemoteRegularControl = null
        }
    }

    private fun loadEmbeddedRegular() {
        if (mRemoteRegularControl == null) {
            return
        }
        try {
            mIAttachEmbeddedWindow?.attachEmbedded(
                mRemoteRegularView!!.rootSurfaceControl!!.inputTransferToken,
                1080, 1000, object : IAttachEmbeddedWindowCallback.Stub() {
                    override fun onEmbeddedWindowAttached(surfacePackage: SurfacePackage?) {
                        Log.i(TAG, "onEmbeddedWindowAttached")
                        mainHandler.post {
                            mSurfacePackage = surfacePackage
                            attachEmbedded()
                        }
                    }
                }
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to load embedded SurfaceControl", e)
        }
    }

    private fun isReadyToAttach(): Boolean {
        synchronized(mLock) {
            if (mRemoteRegularControl == null) {
                Log.d(TAG, "surface is not created")
            }
            if (mIAttachEmbeddedWindow == null) {
                Log.d(TAG, "Service is not attached")
            }
            if (mIsAttached) {
                Log.d(TAG, "Already attached")
            }
            return mRemoteRegularControl != null && mIAttachEmbeddedWindow != null && !mIsAttached && mSurfacePackage != null
        }
    }
    private fun attachEmbedded() {
        if (!isReadyToAttach()) {
            Log.i(TAG, "not ready to attach")
            return
        }

        synchronized(mLock) {
            mIsAttached = true
        }
        mSurfacePackage?.let {
            Log.i(TAG, "attach surface package")
            mRemoteRegularView?.setChildSurfacePackage(it)
        }
    }

    private val mRemoteSurfaceViewHolder: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            mRemoteSurfaceControl = mRemoteSurfaceView!!.surfaceControl
            loadEmbeddedSurface()
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
            mRemoteSurfaceControl = null
        }
    }

    private fun loadEmbeddedSurface() {
        if (mRemoteSurfaceControl == null) {
            return
        }
        try {
            mIAttachEmbeddedWindow?.attachEmbeddedSurfaceControl(
                mRemoteSurfaceControl,
                display.displayId,
                mRemoteSurfaceView!!.getRootSurfaceControl()!!.inputTransferToken
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to load embedded SurfaceControl", e)
        }
    }
}
