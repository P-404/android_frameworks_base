package com.p404.android.systemui;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;

import com.p404.android.systemui.dagger.DaggerGlobalRootComponentP404;
import com.p404.android.systemui.dagger.GlobalRootComponentP404;
import com.p404.android.systemui.dagger.SysUIComponentP404;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class SystemUIP404Factory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponentP404.builder()
                .context(context)
                .build();
    }

    @Override
    public void init(Context context, boolean fromTest) throws ExecutionException, InterruptedException {
        super.init(context, fromTest);
        if (shouldInitializeComponents()) {
            ((SysUIComponentP404) getSysUIComponent()).createKeyguardSmartspaceController();
        }
    }
}
