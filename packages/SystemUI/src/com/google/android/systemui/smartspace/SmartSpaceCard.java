package com.google.android.systemui.smartspace;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.smartspace.nano.SmartspaceProto.CardWrapper;
import com.android.systemui.smartspace.nano.SmartspaceProto.SmartspaceUpdate;

public class SmartSpaceCard {
    private static int sRequestCode;
    private final SmartspaceUpdate.SmartspaceCard mCard;
    private final Context mContext;
    private Bitmap mIcon;
    private boolean mIconProcessed;
    private final Intent mIntent;
    private final boolean mIsIconGrayscale;
    private final boolean mIsWeather;
    private final long mPublishTime;
    private int mRequestCode;

    public SmartSpaceCard(
            Context context,
            SmartspaceUpdate.SmartspaceCard smartspaceCard,
            Intent intent,
            boolean z,
            Bitmap bitmap,
            boolean z2,
            long j) {
        mContext = context.getApplicationContext();
        mCard = smartspaceCard;
        mIsWeather = z;
        mIntent = intent;
        mIcon = bitmap;
        mPublishTime = j;
        mIsIconGrayscale = z2;
        int i = sRequestCode + 1;
        sRequestCode = i;
        if (i > 2147483646) {
            sRequestCode = 0;
        }
        mRequestCode = sRequestCode;
    }

    public boolean isSensitive() {
        return mCard.isSensitive;
    }

    public boolean isWorkProfile() {
        return mCard.isWorkProfile;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public void setIcon(Bitmap bitmap) {
        mIcon = bitmap;
    }

    public void setIconProcessed(boolean z) {
        mIconProcessed = z;
    }

    public boolean isIconProcessed() {
        return mIconProcessed;
    }

    public String getTitle() {
        return substitute(true);
    }

    public CharSequence getFormattedTitle() {
        SmartspaceUpdate.SmartspaceCard.Message.FormattedText formattedText;
        String str;
        SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam[] formatParamArr;
        SmartspaceUpdate.SmartspaceCard.Message message = getMessage();
        if (message == null
                || (formattedText = message.title) == null
                || (str = formattedText.text) == null) {
            return "";
        }
        if (!hasParams(formattedText)) {
            return str;
        }
        String str2 = null;
        String str3 = null;
        int i = 0;
        while (true) {
            formatParamArr = formattedText.formatParam;
            if (i >= formatParamArr.length) {
                break;
            }
            SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam formatParam =
                    formatParamArr[i];
            if (formatParam != null) {
                int i2 = formatParam.formatParamArgs;
                if (i2 == 1 || i2 == 2) {
                    str3 = getDurationText(formatParam);
                } else if (i2 == 3) {
                    str2 = formatParam.text;
                }
            }
            i++;
        }
        SmartspaceUpdate.SmartspaceCard smartspaceCard = mCard;
        if (smartspaceCard.cardType == 3 && formatParamArr.length == 2) {
            str3 = formatParamArr[0].text;
            str2 = formatParamArr[1].text;
        }
        if (str2 == null) {
            return "";
        }
        if (str3 == null) {
            if (message != smartspaceCard.duringEvent) {
                return str;
            }
            str3 = mContext.getString(R.string.smartspace_now);
        }
        return mContext.getString(R.string.smartspace_pill_text_format, str3, str2);
    }

    public String getSubtitle() {
        return substitute(false);
    }

    private SmartspaceUpdate.SmartspaceCard.Message getMessage() {
        SmartspaceUpdate.SmartspaceCard.Message message;
        SmartspaceUpdate.SmartspaceCard.Message message2;
        long currentTimeMillis = System.currentTimeMillis();
        SmartspaceUpdate.SmartspaceCard smartspaceCard = mCard;
        long j = smartspaceCard.eventTimeMillis;
        long j2 = smartspaceCard.eventDurationMillis + j;
        if (currentTimeMillis >= j || (message2 = smartspaceCard.preEvent) == null) {
            if (currentTimeMillis > j2 && (message = smartspaceCard.postEvent) != null) {
                return message;
            }
            SmartspaceUpdate.SmartspaceCard.Message message3 = smartspaceCard.duringEvent;
            if (message3 == null) {
                return null;
            }
            return message3;
        }
        return message2;
    }

    private SmartspaceUpdate.SmartspaceCard.Message.FormattedText getFormattedText(boolean z) {
        SmartspaceUpdate.SmartspaceCard.Message message = getMessage();
        if (message != null) {
            return z ? message.title : message.subtitle;
        }
        return null;
    }

    private boolean hasParams(SmartspaceUpdate.SmartspaceCard.Message.FormattedText formattedText) {
        SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam[] formatParamArr;
        return (formattedText == null
                        || formattedText.text == null
                        || (formatParamArr = formattedText.formatParam) == null
                        || formatParamArr.length <= 0)
                ? false
                : true;
    }

    long getMillisToEvent(
            SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam formatParam) {
        long j;
        if (formatParam.formatParamArgs == 2) {
            SmartspaceUpdate.SmartspaceCard smartspaceCard = mCard;
            j = smartspaceCard.eventTimeMillis + smartspaceCard.eventDurationMillis;
        } else {
            j = mCard.eventTimeMillis;
        }
        return Math.abs(System.currentTimeMillis() - j);
    }

    private int getMinutesToEvent(
            SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam formatParam) {
        return (int) Math.ceil(getMillisToEvent(formatParam) / 60000.0d);
    }

    private String substitute(boolean z) {
        return substitute(z, null);
    }

    private String[] getTextArgs(
            SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam[] formatParamArr,
            String str) {
        int length = formatParamArr.length;
        String[] strArr = new String[length];
        for (int i = 0; i < length; i++) {
            int i2 = formatParamArr[i].formatParamArgs;
            if (i2 == 1 || i2 == 2) {
                strArr[i] = getDurationText(formatParamArr[i]);
            } else {
                String str2 = "";
                if (i2 == 3) {
                    if (str != null && formatParamArr[i].truncateLocation != 0) {
                        strArr[i] = str;
                    } else {
                        if (formatParamArr[i].text != null) {
                            str2 = formatParamArr[i].text;
                        }
                        strArr[i] = str2;
                    }
                } else {
                    strArr[i] = str2;
                }
            }
        }
        return strArr;
    }

    private String getDurationText(
            SmartspaceUpdate.SmartspaceCard.Message.FormattedText.FormatParam formatParam) {
        int minutesToEvent = getMinutesToEvent(formatParam);
        if (minutesToEvent >= 60) {
            int i = minutesToEvent / 60;
            int i2 = minutesToEvent % 60;
            String quantityString =
                    mContext.getResources()
                            .getQuantityString(R.plurals.smartspace_hours, i, Integer.valueOf(i));
            if (i2 <= 0) {
                return quantityString;
            }
            return mContext.getString(
                    R.string.smartspace_hours_mins,
                    quantityString,
                    mContext.getResources()
                            .getQuantityString(
                                    R.plurals.smartspace_minutes, i2, Integer.valueOf(i2)));
        }
        return mContext.getResources()
                .getQuantityString(
                        R.plurals.smartspace_minutes,
                        minutesToEvent,
                        Integer.valueOf(minutesToEvent));
    }

    private String substitute(boolean z, String str) {
        String str2;
        SmartspaceUpdate.SmartspaceCard.Message.FormattedText formattedText = getFormattedText(z);
        return (formattedText == null || (str2 = formattedText.text) == null)
                ? ""
                : hasParams(formattedText)
                        ? String.format(str2, (Object) getTextArgs(formattedText.formatParam, str))
                        : str2;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > getExpiration();
    }

    public long getExpiration() {
        SmartspaceUpdate.SmartspaceCard.ExpiryCriteria expiryCriteria;
        SmartspaceUpdate.SmartspaceCard smartspaceCard = mCard;
        if (smartspaceCard == null || (expiryCriteria = smartspaceCard.expiryCriteria) == null) {
            return 0L;
        }
        return expiryCriteria.expirationTimeMillis;
    }

    public String toString() {
        return "title:"
                + getTitle()
                + " subtitle:"
                + getSubtitle()
                + " expires:"
                + getExpiration()
                + " published:"
                + mPublishTime;
    }

    public static SmartSpaceCard fromWrapper(Context context, CardWrapper cardWrapper, boolean z) {
        if (cardWrapper == null) {
            return null;
        }
        try {
            SmartspaceUpdate.SmartspaceCard.TapAction tapAction = cardWrapper.card.tapAction;
            Intent parseUri =
                    (tapAction == null || TextUtils.isEmpty(tapAction.intent))
                            ? null
                            : Intent.parseUri(cardWrapper.card.tapAction.intent, 0);
            byte[] bArr = cardWrapper.icon;
            Bitmap decodeByteArray =
                    bArr != null ? BitmapFactory.decodeByteArray(bArr, 0, bArr.length, null) : null;
            int dimensionPixelSize =
                    context.getResources().getDimensionPixelSize(R.dimen.header_icon_size);
            if (decodeByteArray != null && decodeByteArray.getHeight() > dimensionPixelSize) {
                decodeByteArray =
                        Bitmap.createScaledBitmap(
                                decodeByteArray,
                                (int)
                                        (decodeByteArray.getWidth()
                                                * (dimensionPixelSize
                                                        / decodeByteArray.getHeight())),
                                dimensionPixelSize,
                                true);
            }
            return new SmartSpaceCard(
                    context,
                    cardWrapper.card,
                    parseUri,
                    z,
                    decodeByteArray,
                    cardWrapper.isIconGrayscale,
                    cardWrapper.publishTime);
        } catch (Exception e) {
            Log.e("SmartspaceCard", "from proto", e);
            return null;
        }
    }

    public PendingIntent getPendingIntent() {
        if (mCard.tapAction == null) {
            return null;
        }
        Intent intent = new Intent(getIntent());
        int i = mCard.tapAction.actionType;
        if (i != 1) {
            if (i == 2) {
                return PendingIntent.getActivity(mContext, mRequestCode, intent, 67108864);
            }
            return null;
        }
        intent.addFlags(268435456);
        intent.setPackage("com.google.android.googlequicksearchbox");
        return PendingIntent.getBroadcast(mContext, mRequestCode, intent, 0);
    }
}
