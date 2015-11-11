/*
 * Copyright (C) 2015 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ypresto.recyclerview.absolutelayoutmanager;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

// TODO: predictive item animations
public class AbsoluteLayoutManager extends RecyclerView.LayoutManager {
    private static final String TAG = "AbsoluteLayoutManager";
    private static final float MINIMUM_FILL_SCALE_FACTOR = 0.0f;
    private static final float MAXIMUM_FILL_SCALE_FACTOR = 0.33f; // MAX_SCROLL_FACTOR of LinearLayoutManager
    private static final int NO_POSITION = RecyclerView.NO_POSITION;
    private static final String STATE_SCROLL_OFFSET = "scrollOffset";
    private static boolean DEBUG = false;

    private final LayoutProvider mLayoutProvider;
    private boolean mIsLayoutProviderDirty;
    private Point mCurrentScrollOffset = new Point(0, 0);
    // NOTE: Size class is only on API >= 22.
    private int mScrollContentWidth = 0;
    private int mScrollContentHeight = 0;
    private Rect mFilledRect = new Rect();
    private int mPendingScrollPosition = NO_POSITION;

    public AbsoluteLayoutManager(LayoutProvider layoutProvider) {
        mLayoutProvider = layoutProvider;
        mIsLayoutProviderDirty = true;
    }

    private static Rect createRect(int x, int y, int width, int height) {
        return new Rect(x, y, x + width, y + height);
    }

    private static boolean checkIfRectsIntersect(Rect rect1, Rect rect2) {
        // other intersect family methods are destructive...
        return rect1.intersects(rect2.left, rect2.top, rect2.right, rect2.bottom);
    }

    /**
     * Absolute rect with offset of current scroll position. It doesn't includes padding.
     */
    private Rect getCurrentScrollOffsetRect() {
        return createRect(mCurrentScrollOffset.x, mCurrentScrollOffset.y, getWidth(), getHeight());
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int actualDx;
        Rect currentScrollOffsetRect = getCurrentScrollOffsetRect();
        if (dx > 0) {
            int remainingX = mScrollContentWidth - currentScrollOffsetRect.right;
            actualDx = Math.min(dx, remainingX);
        } else {
            int remainingX = currentScrollOffsetRect.left;
            actualDx = -Math.min(-dx, remainingX);
        }
        mCurrentScrollOffset.offset(actualDx, 0);
        offsetChildrenHorizontal(-actualDx);
        fillCurrentScrollRect(currentScrollOffsetRect, dx < 0 ? Direction.LEFT : Direction.RIGHT, recycler);
        return actualDx;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int actualDy;
        Rect currentScrollFrameRect = getCurrentScrollOffsetRect();
        if (dy > 0) {
            int remainingY = mScrollContentHeight - currentScrollFrameRect.bottom;
            actualDy = Math.min(dy, remainingY);
        } else {
            int remainingY = currentScrollFrameRect.top;
            actualDy = -Math.min(-dy, remainingY);
        }
        mCurrentScrollOffset.offset(0, actualDy);
        offsetChildrenVertical(-actualDy);
        fillCurrentScrollRect(currentScrollFrameRect, dy < 0 ? Direction.TOP : Direction.BOTTOM, recycler);
        return actualDy;
    }

    /**
     * Fills child views around current scroll rect.
     *
     * @param currentScrollRect Current scroll rect by {@link #getCurrentScrollOffsetRect()}.
     * @param scrollDirection   Fills views by extending current filled rect to specified direction if possible.
     *                          Fills whole rect if {@code null} is passed or rect could not be extended.
     * @param recycler          Recycler for creating and recycling views.
     */
    private void fillCurrentScrollRect(Rect currentScrollRect, Direction scrollDirection, RecyclerView.Recycler recycler) {
        Rect minimumRectToFill = getExtendedRectWithScaleFactor(currentScrollRect, MINIMUM_FILL_SCALE_FACTOR);
        if (mFilledRect.contains(minimumRectToFill)) {
            return;
        }

        Rect maximumRectToFill = getExtendedRectWithScaleFactor(currentScrollRect, MAXIMUM_FILL_SCALE_FACTOR);
        Rect rectToFill = maximumRectToFill;
        Rect newFilledRect = rectToFill;
        boolean isIncrementalFill = false;
        if (scrollDirection != null) {
            // shortcut by extending rect
            Rect incrementalFillRect = calculateExtendRectToDirection(mFilledRect, maximumRectToFill, scrollDirection);
            Rect incrementallyFilledRect = new Rect(mFilledRect);
            incrementallyFilledRect.union(incrementalFillRect);
            if (incrementallyFilledRect.contains(minimumRectToFill)) {
                rectToFill = incrementalFillRect;
                isIncrementalFill = true;
                // shrink rect to maximum
                boolean isIntersected = incrementallyFilledRect.intersect(maximumRectToFill);
                if (!isIntersected) {
                    throw new IllegalStateException("Unexpected non-intersect rect while calculating filled rect.");
                }
                newFilledRect = incrementallyFilledRect;
            }
        }
        if (isIncrementalFill) {
            removeChildViewsOutsideOfScrollRect(newFilledRect, recycler); // recycle first
            fillChildViewsInScrollRect(rectToFill, mFilledRect, recycler); // fill views only not previously placed
        } else {
            detachAndScrapAttachedViews(recycler); // detach all views and fill entire rect
            fillChildViewsInScrollRect(rectToFill, null, recycler);
        }
        mFilledRect = newFilledRect;
    }

    private Rect calculateExtendRectToDirection(Rect currentRect, Rect maximumRect, Direction direction) {
        boolean isIntersected = new Rect().setIntersect(currentRect, maximumRect);
        if (!isIntersected) return new Rect(0, 0, 0, 0);
        Rect extendedRect = new Rect(currentRect);
        switch (direction) {
            case LEFT:
                extendedRect.left = maximumRect.left;
                extendedRect.right = currentRect.left - 1;
                break;
            case TOP:
                extendedRect.top = maximumRect.top;
                extendedRect.bottom = currentRect.top - 1;
                break;
            case RIGHT:
                extendedRect.right = maximumRect.right;
                extendedRect.left = currentRect.right + 1;
                break;
            case BOTTOM:
                extendedRect.bottom = maximumRect.bottom;
                extendedRect.top = currentRect.bottom + 1;
                break;
            default:
                throw new AssertionError();
        }
        return extendedRect;
    }

    private Rect getExtendedRectWithScaleFactor(Rect rect, float scaleFactor) {
        int deltaWidth = Math.round(rect.width() * scaleFactor);
        int deltaHeight = Math.round(rect.height() * scaleFactor);
        int x = (int) Math.round(rect.left - deltaWidth / 2.0);
        int y = (int) Math.round(rect.top - deltaHeight / 2.0);
        return createRect(x, y, rect.width() + deltaWidth, rect.height() + deltaHeight);
    }

    private void fillChildViewsInScrollRect(Rect scrollRect, Rect excludeScrollRect, RecyclerView.Recycler recycler) {
        Rect layoutAttributeRect = new Rect(scrollRect);
        if (DEBUG) {
            Log.v(TAG, "filling for layout manager rect: " + scrollRect + ", layout provider rect: " + layoutAttributeRect);
        }
        offsetScrollRectToLayoutAttributeRect(layoutAttributeRect);
        List<LayoutProvider.LayoutAttribute> layoutAttributes = mLayoutProvider.getLayoutAttributesInRect(layoutAttributeRect);
        for (LayoutProvider.LayoutAttribute layoutAttribute : layoutAttributes) {
            View childView = recycler.getViewForPosition(layoutAttribute.mPosition);
            addView(childView);
            Rect rect = layoutAttribute.copyRect();
            offsetLayoutAttributeRectToScrollRect(rect);
            if (excludeScrollRect != null && checkIfRectsIntersect(rect, excludeScrollRect)) {
                continue;
            }
            offsetScrollRectToChildViewRect(rect);
            // TODO: decoration margins
            childView.measure(View.MeasureSpec.makeMeasureSpec(rect.width(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(rect.height(), View.MeasureSpec.EXACTLY));
            layoutDecorated(childView, rect.left, rect.top, rect.right, rect.bottom);
        }
    }

    private void updateRectWithView(Rect rect, View view) {
        rect.left = view.getLeft();
        rect.top = view.getTop();
        rect.right = view.getRight();
        rect.bottom = view.getBottom();
    }

    private void removeChildViewsOutsideOfScrollRect(Rect scrollRect, RecyclerView.Recycler recycler) {
        Rect retainChildViewRect = new Rect(scrollRect);
        offsetScrollRectToChildViewRect(retainChildViewRect);

        int childCount = getChildCount();
        Rect viewRect = new Rect();
        int removed = 0;
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i - removed);
            updateRectWithView(viewRect, childView);
            if (!checkIfRectsIntersect(retainChildViewRect, viewRect)) {
                removeAndRecycleView(childView, recycler);
                removed++;
            }
        }
    }

    private void detachAndScrapChildViewsInScrollRect(Rect scrollRect, RecyclerView.Recycler recycler) {
        Rect retainChildViewRect = new Rect(scrollRect);
        offsetScrollRectToChildViewRect(retainChildViewRect);

        int childCount = getChildCount();
        Rect viewRect = new Rect();
        int removed = 0;
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i - removed);
            updateRectWithView(viewRect, childView);
            if (checkIfRectsIntersect(retainChildViewRect, viewRect)) {
                detachAndScrapView(childView, recycler);
                removed++;
            }
        }
    }
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        prepareLayoutProvider(state);
        if (mPendingScrollPosition != NO_POSITION) {
            Point scrollOffset = calculateScrollOffsetToShowPositionIfPossible(mPendingScrollPosition);
            if (scrollOffset != null) {
                mCurrentScrollOffset = scrollOffset;
            }
            mPendingScrollPosition = NO_POSITION;
        }
        normalizeCurrentScrollOffset();
        fillCurrentScrollRect(getCurrentScrollOffsetRect(), null, recycler);
    }

    /**
     * Limit scroll offset to possible value according to current layout.
     */
    private void normalizeCurrentScrollOffset() {
        int paddedScrollWidth = mScrollContentWidth + getPaddingLeft() + getPaddingRight();
        int paddedScrollHeight = mScrollContentHeight + getPaddingTop() + getPaddingBottom();
        int x = Math.max(0, Math.min(paddedScrollWidth - getWidth(), mCurrentScrollOffset.x));
        int y = Math.max(0, Math.min(paddedScrollHeight - getHeight(), mCurrentScrollOffset.y));
        mCurrentScrollOffset.set(x, y);
        if (DEBUG) {
            Log.v(TAG, "normalized scroll offset: " + mCurrentScrollOffset);
        }
    }

    /**
     * Convert coordinate of rect from layout manager to {@link LayoutProvider}.
     */
    private void offsetScrollRectToLayoutAttributeRect(Rect rect) {
        rect.offset(-getPaddingLeft(), -getPaddingTop());
    }

    /**
     * Convert coordinate of rect from {@link LayoutAttribute} to layout manager.
     */
    private void offsetLayoutAttributeRectToScrollRect(Rect rect) {
        rect.offset(getPaddingLeft(), getPaddingTop());
    }

    /**
     * Convert coordinate of point from {@link LayoutAttribute} to layout manager.
     */
    private void offsetLayoutAttributePointToScrollPoint(Point point) {
        point.offset(getPaddingLeft(), getPaddingTop());
    }

    /**
     * Convert coordinate of rect from layout manager to child view position.
     */
    private void offsetScrollRectToChildViewRect(Rect rect) {
        rect.offset(-mCurrentScrollOffset.x, -mCurrentScrollOffset.y);
    }

    private Point calculateScrollOffsetToShowPositionIfPossible(int position) {
        if (position >= getItemCount()) return null;
        LayoutProvider.LayoutAttribute layoutAttribute = mLayoutProvider.getLayoutAttributeForItemAtPosition(position);
        return calculateScrollOffsetToShowItem(layoutAttribute, getCurrentScrollOffsetRect());
    }

    private Point calculateScrollOffsetToShowItem(LayoutProvider.LayoutAttribute layoutAttribute, Rect fromScrollRect) {
        // Calculate in layout attributes coordinate to exclude padding.

        //noinspection UnnecessaryLocalVariable
        Rect fromRect = fromScrollRect;
        offsetScrollRectToLayoutAttributeRect(fromRect);
        Rect itemRect = layoutAttribute.copyRect();
        Point layoutAttributesScrollOffset = new Point(fromRect.left, fromRect.top); // defaults to current position
        if (itemRect.left < fromRect.left) {
            layoutAttributesScrollOffset.x = itemRect.left;
        } else if (itemRect.right > fromRect.right) {
            layoutAttributesScrollOffset.x = itemRect.right - fromRect.width();
        }
        if (itemRect.top < fromRect.top) {
            layoutAttributesScrollOffset.y = itemRect.top;
        } else if (itemRect.bottom > fromRect.bottom) {
            layoutAttributesScrollOffset.y = itemRect.bottom - fromRect.height();
        }

        // Then restore to layout manager coordinate.
        Point scrollOffset = new Point(layoutAttributesScrollOffset);
        offsetLayoutAttributePointToScrollPoint(scrollOffset);
        return scrollOffset;
    }

    private int getLayoutSpaceWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getLayoutSpaceHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    public LayoutProvider getLayoutProvider() {
        return mLayoutProvider;
    }

    private void prepareLayoutProvider(RecyclerView.State state) {
        if (state.didStructureChange()) {
            mIsLayoutProviderDirty = true;
        }
        if (mLayoutProvider.mLayoutSpaceWidth != getLayoutSpaceWidth() || mLayoutProvider.mLayoutSpaceHeight != getLayoutSpaceHeight()) {
            mIsLayoutProviderDirty = true;
        }

        if (!mIsLayoutProviderDirty) return;
        mFilledRect = new Rect(); // invalidate cache
        mLayoutProvider.mLayoutSpaceWidth = getLayoutSpaceWidth();
        mLayoutProvider.mLayoutSpaceHeight = getLayoutSpaceHeight();
        mLayoutProvider.mItemCount = getItemCount();
        mLayoutProvider.prepareLayout();
        mScrollContentWidth = mLayoutProvider.getScrollContentWidth();
        mScrollContentHeight = mLayoutProvider.getScrollContentHeight();
        mIsLayoutProviderDirty = false;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        Point scrollOffset = mCurrentScrollOffset;
        if (mPendingScrollPosition != NO_POSITION) {
            Point pendingScrollOffset = calculateScrollOffsetToShowPositionIfPossible(mPendingScrollPosition);
            if (pendingScrollOffset != null) {
                scrollOffset = pendingScrollOffset;
            }
        }
        state.putParcelable(STATE_SCROLL_OFFSET, scrollOffset);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof Bundle)) {
            Log.e(TAG, "Invalid state object is passed for onRestoreInstanceState: " + state.getClass().getName());
            return;
        }
        Bundle bundle = (Bundle) state;
        Point scrollOffset = bundle.getParcelable(STATE_SCROLL_OFFSET);
        if (scrollOffset == null) {
            Log.e(TAG, "Invalid state object is passed, value not found for " + STATE_SCROLL_OFFSET);
            return;
        }
        mCurrentScrollOffset = scrollOffset;
        requestLayout();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public boolean canScrollHorizontally() {
        return mScrollContentWidth > getLayoutSpaceWidth();
    }

    @Override
    public boolean canScrollVertically() {
        return mScrollContentHeight > getLayoutSpaceHeight();
    }

    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return mCurrentScrollOffset.x;
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return mCurrentScrollOffset.y;
    }

    @Override
    public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return getWidth();
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return getHeight();
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        return mScrollContentWidth + getPaddingLeft() + getPaddingRight();
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return mScrollContentHeight + getPaddingTop() + getPaddingBottom();
    }

    @Override
    public void scrollToPosition(int position) {
        mPendingScrollPosition = position;
        requestLayout();
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state,
                                       int position) {
        prepareLayoutProvider(state);
        LinearSmoothScroller linearSmoothScroller =
                new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public PointF computeScrollVectorForPosition(int targetPosition) {
                        Point targetScrollOffset = calculateScrollOffsetToShowPositionIfPossible(targetPosition);
                        if (targetScrollOffset == null) return null;
                        return calculateUnitVectorFromPoints(mCurrentScrollOffset, targetScrollOffset);
                    }
                };
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    private PointF calculateUnitVectorFromPoints(Point currentScrollOffset, Point targetScrollOffset) {
        PointF vector = new PointF();
        vector.x = targetScrollOffset.x - currentScrollOffset.x;
        vector.y = targetScrollOffset.y - currentScrollOffset.y;

        //noinspection SuspiciousNameCombination
        double norm = Math.sqrt(Math.pow(vector.x, 2) + Math.pow(vector.y, 2));
        if (norm == 0.0) return vector;
        vector.x /= norm;
        vector.y /= norm;
        return vector;
    }

    private enum Direction {
        LEFT, TOP, RIGHT, BOTTOM
    }

    public abstract static class LayoutProvider {
        private int mLayoutSpaceWidth;
        private int mLayoutSpaceHeight;
        private int mItemCount;

        public int getLayoutSpaceWidth() {
            return mLayoutSpaceWidth;
        }

        public int getLayoutSpaceHeight() {
            return mLayoutSpaceHeight;
        }

        public int getItemCount() {
            return mItemCount;
        }

        public abstract void prepareLayout();

        public abstract int getScrollContentWidth();

        public abstract int getScrollContentHeight();

        public abstract List<LayoutAttribute> getLayoutAttributesInRect(Rect rect);

        public abstract LayoutAttribute getLayoutAttributeForItemAtPosition(int position);

        public static class LayoutAttribute {
            private final int mPosition;
            private final Rect mRect;

            /**
             * Create layout attributes for item at position.
             *
             * @param position Adapter position of item.
             * @param rect     Absolute rect of item. Will be copied to keep from mutation.
             */
            public LayoutAttribute(int position, Rect rect) {
                mPosition = position;
                mRect = new Rect(rect);
            }

            /**
             * Get adapter position of item.
             */
            public int getPosition() {
                return mPosition;
            }

            /**
             * Copy absolute rect of item.
             * There is no getter because {@link Rect} is mutable type.
             */
            public Rect copyRect() {
                return new Rect(mRect);
            }

            public int getWidth() {
                return mRect.width();
            }

            public int getHeight() {
                return mRect.height();
            }

            public int getLeft() {
                return mRect.left;
            }

            public int getTop() {
                return mRect.top;
            }

            public int getRight() {
                return mRect.right;
            }

            public int getBottom() {
                return mRect.bottom;
            }

            public boolean isIntersectWithRect(Rect rect) {
                return AbsoluteLayoutManager.checkIfRectsIntersect(mRect, rect);
            }
        }
    }
}
