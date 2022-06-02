package com.p404.android.systemui;

import android.content.Context;
import android.content.res.AssetManager;

import com.p404.android.systemui.dagger.P404GlobalRootComponent;
import com.p404.android.systemui.dagger.DaggerP404GlobalRootComponent;
import com.p404.android.systemui.dagger.P404SysUIComponent;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class P404SystemUIFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerP404GlobalRootComponent.builder()
                .context(context)
                .build();
    }

    @Override
    public void init(Context context, boolean fromTest) throws ExecutionException, InterruptedException {
        super.init(context, fromTest);
        if (shouldInitializeComponents()) {
            ((P404SysUIComponent) getSysUIComponent()).createKeyguardSmartspaceController();
        }
    }
}
