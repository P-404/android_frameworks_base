package com.google.android.systemui.smartspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import com.android.systemui.smartspace.nano.SmartspaceProto.SmartspaceUpdate;

import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import com.google.protobuf.nano.MessageNano;

public class SmartSpaceBroadcastReceiver extends BroadcastReceiver {
    private final SmartSpaceController mController;

    public SmartSpaceBroadcastReceiver(SmartSpaceController smartSpaceController) {
        mController = smartSpaceController;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SmartspaceUpdate.SmartspaceCard[] smartspaceCardArr;
        if (SmartSpaceController.DEBUG) {
            Log.d("SmartSpaceReceiver", "receiving update");
        }
        int myUserId = UserHandle.myUserId();
        if (myUserId != 0) {
            if (intent.getBooleanExtra("rebroadcast", false)) {
                return;
            }
            intent.putExtra("rebroadcast", true);
            intent.putExtra("uid", myUserId);
            context.sendBroadcastAsUser(intent, UserHandle.ALL);
            return;
        }
        if (!intent.hasExtra("uid")) {
            intent.putExtra("uid", myUserId);
        }
        byte[] byteArrayExtra =
                intent.getByteArrayExtra(
                        "com.google.android.apps.nexuslauncher.extra.SMARTSPACE_CARD");
        if (byteArrayExtra != null) {
            SmartspaceUpdate SmartspaceUpdate = new SmartspaceUpdate();
            try {
                MessageNano.mergeFrom(SmartspaceUpdate, byteArrayExtra);
                for (SmartspaceUpdate.SmartspaceCard smartspaceCard : SmartspaceUpdate.card) {
                    int i = smartspaceCard.cardPriority;
                    boolean z = i == 1;
                    boolean z2 = i == 2;
                    if (!z && !z2) {
                        Log.w(
                                "SmartSpaceReceiver",
                                "unrecognized card priority: " + smartspaceCard.cardPriority);
                    }
                    notify(smartspaceCard, context, intent, z);
                }
                return;
            } catch (InvalidProtocolBufferNanoException e) {
                Log.e("SmartSpaceReceiver", "proto", e);
                return;
            }
        }
        Log.e("SmartSpaceReceiver", "receiving update with no proto: " + intent.getExtras());
    }

    private void notify(
            SmartspaceUpdate.SmartspaceCard smartspaceCard,
            Context context,
            Intent intent,
            boolean z) {
        PackageInfo packageInfo;
        long currentTimeMillis = System.currentTimeMillis();
        try {
            packageInfo =
                    context.getPackageManager()
                            .getPackageInfo("com.google.android.googlequicksearchbox", 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("SmartSpaceReceiver", "Cannot find GSA", e);
            packageInfo = null;
        }
        mController.onNewCard(
                new NewCardInfo(smartspaceCard, intent, z, currentTimeMillis, packageInfo));
    }
}
