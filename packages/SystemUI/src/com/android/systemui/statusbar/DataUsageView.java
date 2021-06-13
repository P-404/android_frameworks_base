package com.android.systemui.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.settingslib.net.DataUsageController;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NetworkController;

public class DataUsageView extends TextView {

    private boolean shouldUpdateData;
    private boolean shouldUpdateDataTextView;
    private NetworkController mNetworkController;
    private DataUsageController mDataUsageController;
    private Context mContext;
    private String mFormatedInfo;

    public DataUsageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mNetworkController = Dependency.get(NetworkController.class);
        mDataUsageController = mNetworkController.getMobileDataController();
    }

    public void updateUsage() {
        shouldUpdateData = true;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (shouldUpdateData) {
            shouldUpdateData = false;
            AsyncTask.execute(this::updateUsageData);
        }
        if (shouldUpdateDataTextView) {
            shouldUpdateDataTextView = false;
            setText(mFormatedInfo);
        }
    }

    private void updateUsageData() {
        mDataUsageController.setSubscriptionId(
                SubscriptionManager.getDefaultDataSubscriptionId());
        final DataUsageController.DataUsageInfo info = mDataUsageController.getDailyDataUsageInfo();
        String carrier = info.carrier;
        String formatedInfo = "";
        if (carrier != null && !carrier.isEmpty()) {
            String[] parts = carrier.split("\\|");
            carrier = parts[0];
            formatedInfo = carrier + ": ";
        }
        formatedInfo += formatDataUsage(info.usageLevel) + " " + mContext.getResources().getString(R.string.usage_data);
        if (formatedInfo.equals(mFormatedInfo)) {
            return;
        }
        mFormatedInfo = formatedInfo;
        shouldUpdateDataTextView = true;
        invalidate();
    }

    private CharSequence formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(mContext.getString(
                com.android.internal.R.string.fileSizeSuffix, res.value, res.units));
    }
}
