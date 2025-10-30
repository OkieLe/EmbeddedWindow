package io.github.ole.embedded;

import android.view.SurfaceControlViewHost.SurfacePackage;

interface IAttachEmbeddedWindowCallback {
    void onEmbeddedWindowAttached(in SurfacePackage surfacePackage);
}
