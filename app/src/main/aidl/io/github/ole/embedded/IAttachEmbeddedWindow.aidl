package io.github.ole.embedded;

import android.os.IBinder;
import android.view.WindowManager.LayoutParams;
import android.view.SurfaceControl;
import android.window.InputTransferToken;
import io.github.ole.embedded.IAttachEmbeddedWindowCallback;

interface IAttachEmbeddedWindow {
    void attachEmbedded(in InputTransferToken hostToken, int width, int height,
            in IAttachEmbeddedWindowCallback callback);
    void relayout(in LayoutParams lp);
    oneway void attachEmbeddedSurfaceControl(in SurfaceControl parentSurfaceControl, int displayId,
            in InputTransferToken inputTransferToken);
    oneway void tearDownEmbeddedSurfaceControl();
}
