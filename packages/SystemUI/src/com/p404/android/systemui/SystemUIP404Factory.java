package com.p404.android.systemui;

import android.content.Context;

import com.p404.android.systemui.dagger.DaggerGlobalRootComponentP404;
import com.p404.android.systemui.dagger.GlobalRootComponentP404;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;

public class SystemUIP404Factory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponentP404.builder()
                .context(context)
                .build();
    }
}
