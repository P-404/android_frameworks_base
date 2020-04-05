/*
 * Copyright (C) 2020 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.util.AttributeSet;
import android.util.Log;

import com.android.systemui.R;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ScreenRecordDrawable extends DrawableWrapper {

    private Drawable mFillDrawable;
    private int mHorizontalPadding;
    private float mIconRadius;
    private int mLevel;
    private Paint mPaint;
    private float mTextSize;

    public ScreenRecordDrawable() {
        super((Drawable) null);
    }

    @Override
    public void inflate(@NonNull Resources r, @NonNull XmlPullParser parser,
            @NonNull AttributeSet attrs, @Nullable Resources.Theme theme)
            throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        setDrawable(r.getDrawable(R.drawable.ic_screen_record_background, theme).mutate());
        mFillDrawable = r.getDrawable(R.drawable.ic_screen_record_background, theme).mutate();
        mHorizontalPadding = r.getDimensionPixelSize(R.dimen.status_bar_horizontal_padding);
        mTextSize = (float) r.getDimensionPixelSize(R.dimen.screenrecord_status_text_size);
        mIconRadius = (float) r.getDimensionPixelSize(R.dimen.screenrecord_status_icon_radius);
        mLevel = attrs.getAttributeIntValue((String) null, "level", 0);
        mPaint = new Paint();
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(-1);
        mPaint.setTextSize(mTextSize);
        mPaint.setFakeBoldText(true);
    }

    @Override
    public boolean canApplyTheme() {
        return mFillDrawable.canApplyTheme() || super.canApplyTheme();
    }

    @Override
    public void applyTheme(Resources.Theme t) {
        super.applyTheme(t);
        mFillDrawable.applyTheme(t);
    }

    @Override
    public void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mFillDrawable.setBounds(bounds);
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        mFillDrawable.setLayoutDirection(layoutDirection);
        return super.onLayoutDirectionChanged(layoutDirection);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        mFillDrawable.draw(canvas);
        Rect bounds = mFillDrawable.getBounds();
        if (mLevel > 0) {
            String valueOf = String.valueOf(mLevel);
            Rect rect = new Rect();
            mPaint.getTextBounds(valueOf, 0, valueOf.length(), rect);
            canvas.drawText(valueOf, (float) bounds.centerX(), ((float) bounds.centerY()) + ((float) (rect.height() / 4)), mPaint);
            return;
        }
        canvas.drawCircle((float) bounds.centerX(), ((float) bounds.centerY()) - (mIconRadius / 2.0f), mIconRadius, mPaint);
    }

    @Override
    public boolean getPadding(Rect padding) {
        padding.left = padding.left + mHorizontalPadding;
        padding.right += mHorizontalPadding;
        padding.top = 0;
        padding.bottom = 0;
        Log.d("ScreenRecordDrawable", "set zero top/bottom pad");
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        mFillDrawable.setAlpha(alpha);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mFillDrawable.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    @Override
    public Drawable mutate() {
        mFillDrawable.mutate();
        return super.mutate();
    }
}
