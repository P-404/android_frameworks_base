package com.google.android.systemui.smartspace;

import android.util.Log;

public class SmartSpaceData {
    SmartSpaceCard mCurrentCard;
    SmartSpaceCard mWeatherCard;

    public boolean hasWeather() {
        return mWeatherCard != null;
    }

    public boolean hasCurrent() {
        return mCurrentCard != null;
    }

    public long getExpirationRemainingMillis() {
        long expiration;
        long currentTimeMillis = System.currentTimeMillis();
        if (hasCurrent() && hasWeather()) {
            expiration = Math.min(mCurrentCard.getExpiration(), mWeatherCard.getExpiration());
        } else if (hasCurrent()) {
            expiration = mCurrentCard.getExpiration();
        } else if (!hasWeather()) {
            return 0L;
        } else {
            expiration = mWeatherCard.getExpiration();
        }
        return expiration - currentTimeMillis;
    }

    public long getExpiresAtMillis() {
        if (hasCurrent() && hasWeather()) {
            return Math.min(mCurrentCard.getExpiration(), mWeatherCard.getExpiration());
        }
        if (hasCurrent()) {
            return mCurrentCard.getExpiration();
        }
        if (!hasWeather()) {
            return 0L;
        }
        return mWeatherCard.getExpiration();
    }

    public void clear() {
        mWeatherCard = null;
        mCurrentCard = null;
    }

    public boolean handleExpire() {
        boolean z;
        if (!hasWeather() || !mWeatherCard.isExpired()) {
            z = false;
        } else {
            if (SmartSpaceController.DEBUG) {
                Log.d("SmartspaceData", "weather expired " + mWeatherCard.getExpiration());
            }
            mWeatherCard = null;
            z = true;
        }
        if (!hasCurrent() || !mCurrentCard.isExpired()) {
            return z;
        }
        if (SmartSpaceController.DEBUG) {
            Log.d("SmartspaceData", "current expired " + mCurrentCard.getExpiration());
        }
        mCurrentCard = null;
        return true;
    }

    public String toString() {
        return "{" + mCurrentCard + "," + mWeatherCard + "}";
    }

    public SmartSpaceCard getWeatherCard() {
        return mWeatherCard;
    }

    public SmartSpaceCard getCurrentCard() {
        return mCurrentCard;
    }
}
