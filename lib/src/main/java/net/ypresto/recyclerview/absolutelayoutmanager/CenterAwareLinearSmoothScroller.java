package net.ypresto.recyclerview.absolutelayoutmanager;

import android.content.Context;
import android.support.v7.widget.LinearSmoothScroller;

import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_CENTER_HORIZONTAL;
import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_CENTER_VERTICAL;

abstract class CenterAwareLinearSmoothScroller extends LinearSmoothScroller {
    public static final int SNAP_TO_CENTER = Integer.MAX_VALUE;
    private final boolean mSnapToHorizontalCenter;
    private final boolean mSnapToVerticalCenter;

    public CenterAwareLinearSmoothScroller(Context context, int scrollAlignment) {
        super(context);
        mSnapToHorizontalCenter = (scrollAlignment & SCROLL_ALIGNMENT_CENTER_HORIZONTAL) != 0;
        mSnapToVerticalCenter = (scrollAlignment & SCROLL_ALIGNMENT_CENTER_VERTICAL) != 0;
    }

    @Override
    public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
        if (snapPreference == SNAP_TO_CENTER) {
            int viewCenter = Math.round((viewStart + viewEnd) / 2.0f);
            int boxCenter = Math.round((boxStart + boxEnd) / 2.0f);
            return boxCenter - viewCenter;
        }
        return super.calculateDtToFit(viewStart, viewEnd, boxStart, boxEnd, snapPreference);
    }

    @Override
    protected int getHorizontalSnapPreference() {
        if (mSnapToHorizontalCenter) {
            return SNAP_TO_CENTER;
        }
        return super.getHorizontalSnapPreference();
    }

    @Override
    protected int getVerticalSnapPreference() {
        if (mSnapToVerticalCenter) {
            return SNAP_TO_CENTER;
        }
        return super.getVerticalSnapPreference();
    }
}
