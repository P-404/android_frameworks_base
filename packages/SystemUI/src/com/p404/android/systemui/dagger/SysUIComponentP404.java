package com.p404.android.systemui.dagger;

import com.android.systemui.dagger.DefaultComponentBinder;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIBinder;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SystemUIModule;

import com.p404.android.systemui.keyguard.KeyguardSliceProviderP404;
import com.p404.android.systemui.smartspace.KeyguardSmartspaceController;

import dagger.Subcomponent;

@SysUISingleton
@Subcomponent(modules = {
        DefaultComponentBinder.class,
        DependencyProvider.class,
        SystemUIBinder.class,
        SystemUIModule.class,
        SystemUIP404Module.class})
public interface SysUIComponentP404 extends SysUIComponent {
    @SysUISingleton
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        SysUIComponentP404 build();
    }

    /**
     * Member injection into the supplied argument.
     */
    void inject(KeyguardSliceProviderP404 keyguardSliceProviderP404);

    @SysUISingleton
    KeyguardSmartspaceController createKeyguardSmartspaceController();
}
