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

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.LayoutProvider.LayoutAttribute;

import java.util.List;

// TODO: predictive item animations
public class AbsoluteLayoutManager extends RecyclerView.LayoutManager {
    public static final int SCROLL_ALIGNMENT_NONE = 0;
    public static final int SCROLL_ALIGNMENT_LEFT = 1 << 1;
    public static final int SCROLL_ALIGNMENT_CENTER_HORIZONTAL = 1 << 2;
    public static final int SCROLL_ALIGNMENT_RIGHT = 1 << 3;
    public static final int SCROLL_ALIGNMENT_TOP = 1 << 4;
    public static final int SCROLL_ALIGNMENT_CENTER_VERTICAL = 1 << 5;
    public static final int SCROLL_ALIGNMENT_BOTTOM = 1 << 6;
    public static final int SCROLL_ALIGNMENT_CENTER = SCROLL_ALIGNMENT_CENTER_HORIZONTAL | SCROLL_ALIGNMENT_CENTER_VERTICAL;

    private static final String TAG = "AbsoluteLayoutManager";
    private static final float MINIMUM_FILL_SCALE_FACTOR = 0.0f;
    private static final float MAXIMUM_FILL_SCALE_FACTOR = 0.33f; // MAX_SCROLL_FACTOR of LinearLayoutManager
    private static final int NO_POSITION = RecyclerView.NO_POSITION;
    private static boolean DEBUG = false;

    private final LayoutProvider mLayoutProvider;
    private boolean mIsLayoutProviderDirty;
    private final Point mCurrentScrollOffset = new Point(0, 0);
    // NOTE: Size class is only on API >= 22.
    private int mScrollContentWidth = 0;
    private int mScrollContentHeight = 0;
    private Rect mFilledRect = new Rect();
    private int mPendingScrollPosition = NO_POSITION;
    private int mPendingScrollAlignment = SCROLL_ALIGNMENT_NONE;
    private SavedState mPendingSavedState;

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
     * Get visible rect of layout provider coordinate. Returned rect contains padding area.
     */
    private Rect getVisibleRect() {
        return createRect(mCurrentScrollOffset.x - getPaddingLeft(), mCurrentScrollOffset.y - getPaddingTop(), getWidth(), getHeight());
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int actualDx;
        if (dx > 0) {
            int remainingX = getScrollableWidth() - getWidth() - mCurrentScrollOffset.x;
            actualDx = Math.min(dx, remainingX);
        } else {
            actualDx = -Math.min(-dx, mCurrentScrollOffset.x);
        }
        mCurrentScrollOffset.offset(actualDx, 0);
        offsetChildrenHorizontal(-actualDx);
        fillRect(getVisibleRect(), dx < 0 ? Direction.LEFT : Direction.RIGHT, recycler);
        return actualDx;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int actualDy;
        if (dy > 0) {
            int remainingY = getScrollableHeight() - getHeight() - mCurrentScrollOffset.y;
            actualDy = Math.min(dy, remainingY);
        } else {
            actualDy = -Math.min(-dy, mCurrentScrollOffset.y);
        }
        mCurrentScrollOffset.offset(0, actualDy);
        offsetChildrenVertical(-actualDy);
        fillRect(getVisibleRect(), dy < 0 ? Direction.TOP : Direction.BOTTOM, recycler);
        return actualDy;
    }

    /**
     * Fills child views around current scroll rect.
     *
     * @param visibleRect     Current visible rect by {@link #getVisibleRect()}.
     * @param scrollDirection Fills views by extending current filled rect to specified direction if possible.
     *                        Fills whole rect if {@code null} is passed or rect could not be extended.
     * @param recycler        Recycler for creating and recycling views.
     */
    private void fillRect(Rect visibleRect, Direction scrollDirection, RecyclerView.Recycler recycler) {
        Rect minimumRectToFill = getExtendedRectWithScaleFactor(visibleRect, MINIMUM_FILL_SCALE_FACTOR);
        if (mFilledRect.contains(minimumRectToFill)) {
            return;
        }

        Rect maximumRectToFill = getExtendedRectWithScaleFactor(visibleRect, MAXIMUM_FILL_SCALE_FACTOR);
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
                if (DEBUG) {
                    Log.v(TAG, "Incrementally filling rect: " + newFilledRect);
                }
            }
        }
        if (isIncrementalFill) {
            removeChildViewsOutsideOfScrollRect(newFilledRect, recycler); // recycle first
            fillChildViewsInRect(rectToFill, mFilledRect, recycler); // fill views only not previously placed
        } else {
            detachAndScrapAttachedViews(recycler); // detach all views and fill entire rect
            fillChildViewsInRect(rectToFill, null, recycler);
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

    private void fillChildViewsInRect(Rect rectToFill, Rect rectToExclude, RecyclerView.Recycler recycler) {
        if (DEBUG) {
            Log.v(TAG, "filling for rect: " + rectToFill);
        }
        List<LayoutAttribute> layoutAttributes = mLayoutProvider.getLayoutAttributesInRect(rectToFill);
        for (LayoutAttribute layoutAttribute : layoutAttributes) {
            if (rectToExclude != null && layoutAttribute.isIntersectWithRect(rectToExclude)) {
                continue;
            }
            View childView = recycler.getViewForPosition(layoutAttribute.mPosition);
            addView(childView);
            Rect rect = layoutAttribute.copyRect();
            offsetLayoutAttributeRectToChildViewRect(rect);
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
        offsetLayoutAttributeRectToChildViewRect(retainChildViewRect);

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

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        prepareLayoutProvider();
        if (mPendingSavedState != null) {
            // TODO: Consider deferring restore until getItemCount() > 0.
            restoreFromSavedState(mPendingSavedState);
            mPendingSavedState = null;
        }

        if (mPendingScrollPosition != NO_POSITION) {
            Point scrollOffset = calculateScrollOffsetToShowPositionIfPossible(mPendingScrollPosition, mPendingScrollAlignment);
            if (scrollOffset != null) {
                mCurrentScrollOffset.set(scrollOffset.x, scrollOffset.y);
            }
            mPendingScrollPosition = NO_POSITION;
        }
        normalizeScrollOffset(mCurrentScrollOffset);
        detachAndScrapAttachedViews(recycler);
        mFilledRect.setEmpty();
        fillRect(getVisibleRect(), null, recycler);
    }

    /**
     * Limit scroll offset to possible value according to current layout.
     */
    private void normalizeScrollOffset(Point scrollOffset) {
        int x = Math.max(0, Math.min(getScrollableWidth() - getWidth(), scrollOffset.x));
        int y = Math.max(0, Math.min(getScrollableHeight() - getHeight(), scrollOffset.y));
        scrollOffset.set(x, y);
        if (DEBUG) {
            Log.v(TAG, "normalized scroll offset: " + scrollOffset);
        }
    }

    private int getScrollableHeight() {
        return mScrollContentHeight + getPaddingTop() + getPaddingBottom();
    }

    private int getScrollableWidth() {
        return mScrollContentWidth + getPaddingLeft() + getPaddingRight();
    }

    /**
     * Convert coordinate of rect from layout provider to child view position.
     */
    private void offsetLayoutAttributeRectToChildViewRect(Rect rect) {
        rect.offset(-mCurrentScrollOffset.x + getPaddingLeft(), -mCurrentScrollOffset.y + getPaddingTop());
    }

    private Point calculateScrollOffsetToShowPositionIfPossible(int position, int scrollAlignment) {
        if (position >= getItemCount()) return null;
        LayoutAttribute layoutAttribute = mLayoutProvider.getLayoutAttributeForItemAtPosition(position);
        Rect layoutSpaceRect = createRect(mCurrentScrollOffset.x, mCurrentScrollOffset.y, getLayoutSpaceWidth(), getLayoutSpaceHeight());
        return ScrollHelper.calculateScrollOffsetToShowItem(layoutAttribute, layoutSpaceRect, scrollAlignment);
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

    /**
     * Explicitly requests to call {@link LayoutProvider#prepareLayout()} on next layout cycle.
     * Note that any changes to adapter implicitly requests {@code prepareLayout()} call.
     */
    public void invalidateLayout() {
        mIsLayoutProviderDirty = true;
        requestLayout();
    }

    private void prepareLayoutProvider() {
        if (mLayoutProvider.mLayoutManagerState.mLayoutSpaceWidth != getLayoutSpaceWidth()
                || mLayoutProvider.mLayoutManagerState.mLayoutSpaceHeight != getLayoutSpaceHeight()) {
            mIsLayoutProviderDirty = true;
        }

        if (!mIsLayoutProviderDirty) return;
        mFilledRect = new Rect(); // invalidate cache
        mLayoutProvider.mLayoutManagerState = new LayoutProvider.LayoutManagerState(
                getLayoutSpaceWidth(),
                getLayoutSpaceHeight(),
                getItemCount());
        mLayoutProvider.prepareLayout();
        mScrollContentWidth = mLayoutProvider.getScrollContentWidth();
        mScrollContentHeight = mLayoutProvider.getScrollContentHeight();
        mIsLayoutProviderDirty = false;
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        // It is happen when setLayoutManager() is called.
        mIsLayoutProviderDirty = true;
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        mIsLayoutProviderDirty = true;
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        mIsLayoutProviderDirty = true;
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        mIsLayoutProviderDirty = true;
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        mIsLayoutProviderDirty = true;
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount) {
        mIsLayoutProviderDirty = true;
    }

    @Override
    public SavedState onSaveInstanceState() {
        if (mPendingSavedState != null) {
            return mPendingSavedState;
        }

        if (getItemCount() == 0) {
            return SavedState.empty();
        }

        prepareLayoutProvider();
        AnchorHelper.AnchorInfo anchor = AnchorHelper.calculateAnchorItemInRect(mLayoutProvider, mCurrentScrollOffset, getVisibleRect());
        Point point = anchor.getPoint();
        int relativeOffsetX = mCurrentScrollOffset.x - point.x;
        int relativeOffsetY = mCurrentScrollOffset.y - point.y;
        if (DEBUG) {
            Log.v(TAG, "Saving AnchorInfo, position: " + anchor.mLayoutAttribute.getPosition()
                    + ", corner: " + anchor.mCorner
                    + " relativeOffsetX: " + relativeOffsetX
                    + " relativeOffsetY: " + relativeOffsetY);
        }
        return new SavedState(
                anchor.mLayoutAttribute.getPosition(),
                anchor.mCorner,
                relativeOffsetX,
                relativeOffsetY,
                mPendingScrollPosition,
                mPendingScrollAlignment);
    }

    private void restoreFromSavedState(SavedState savedState) {
        if (savedState.mAnchorPosition != NO_POSITION) {
            LayoutAttribute layoutAttribute = mLayoutProvider.getLayoutAttributeForItemAtPosition(savedState.mAnchorPosition);
            Point point = savedState.mAnchorCorner.getPointForRect(layoutAttribute.mRect);
            mCurrentScrollOffset.set(point.x + savedState.mRelativeOffsetX, point.y + savedState.mRelativeOffsetY);
        }
        if (mPendingScrollPosition == NO_POSITION) {
            mPendingScrollPosition = savedState.mPendingScrollPosition;
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            Log.e(TAG, "Invalid state object is passed for onRestoreInstanceState: " + state.getClass().getName());
            return;
        }
        mPendingSavedState = (SavedState) state;
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
        return getScrollableWidth();
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return getScrollableHeight();
    }

    @Override
    public void scrollToPosition(int position) {
        scrollToPositionWithAlignment(position, SCROLL_ALIGNMENT_NONE);
    }

    public void scrollToPositionWithAlignment(int position, int scrollAlignment) {
        mPendingScrollPosition = position;
        mPendingScrollAlignment = scrollAlignment;
        requestLayout();
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        smoothScrollToPositionWithAlignment(recyclerView.getContext(), position, SCROLL_ALIGNMENT_NONE);
    }

    public void smoothScrollToPositionWithAlignment(Context context, int position, final int scrollAlignment) {
        prepareLayoutProvider();
        LinearSmoothScroller linearSmoothScroller = new CenterAwareLinearSmoothScroller(context, scrollAlignment) {
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                Point targetScrollOffset = calculateScrollOffsetToShowPositionIfPossible(targetPosition, scrollAlignment);
                if (targetScrollOffset == null) return null;
                normalizeScrollOffset(targetScrollOffset);
                return ScrollHelper.calculateUnitVectorFromPoints(mCurrentScrollOffset, targetScrollOffset);
            }
        };
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    private enum Direction {
        LEFT, TOP, RIGHT, BOTTOM
    }

    /**
     * Keeps position of anchoring view and relative offset from that position.
     */
    private static class SavedState implements Parcelable {
        private final int mAnchorPosition;
        private final AnchorHelper.Corner mAnchorCorner;
        private final int mRelativeOffsetX;
        private final int mRelativeOffsetY;
        private final int mPendingScrollPosition;
        private final int mPendingScrollAlignment;

        static SavedState empty() {
            return new SavedState(NO_POSITION, null, 0, 0, NO_POSITION, SCROLL_ALIGNMENT_NONE);
        }

        SavedState(int anchorPosition, AnchorHelper.Corner anchorCorner, int relativeOffsetX, int relativeOffsetY, int pendingScrollPosition, int pendingScrollAlignment) {
            mAnchorPosition = anchorPosition;
            mAnchorCorner = anchorCorner;
            mRelativeOffsetX = relativeOffsetX;
            mRelativeOffsetY = relativeOffsetY;
            mPendingScrollPosition = pendingScrollPosition;
            mPendingScrollAlignment = pendingScrollAlignment;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.mAnchorPosition);
            dest.writeSerializable(this.mAnchorCorner);
            dest.writeInt(this.mRelativeOffsetX);
            dest.writeInt(this.mRelativeOffsetY);
            dest.writeInt(this.mPendingScrollPosition);
            dest.writeInt(this.mPendingScrollAlignment);
        }

        protected SavedState(Parcel in) {
            this.mAnchorPosition = in.readInt();
            this.mAnchorCorner = (AnchorHelper.Corner) in.readSerializable();
            this.mRelativeOffsetX = in.readInt();
            this.mRelativeOffsetY = in.readInt();
            this.mPendingScrollPosition = in.readInt();
            this.mPendingScrollAlignment = in.readInt();
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public abstract static class LayoutProvider {
        private LayoutManagerState mLayoutManagerState = new LayoutManagerState();

        /**
         * @deprecated Use {@link #getState()}.
         */
        @Deprecated
        public int getLayoutSpaceWidth() {
            return mLayoutManagerState.getLayoutSpaceWidth();
        }

        /**
         * @deprecated Use {@link #getState()}.
         */
        @Deprecated
        public int getLayoutSpaceHeight() {
            return mLayoutManagerState.getLayoutSpaceHeight();
        }

        /**
         * @deprecated Use {@link #getState()}.
         */
        @Deprecated
        public int getItemCount() {
            return mLayoutManagerState.getItemCount();
        }

        /**
         * Returns current state of layout manager, including size of layout space and item count.
         */
        public final LayoutManagerState getState() {
            return mLayoutManagerState;
        }

        public abstract void prepareLayout();

        public abstract int getScrollContentWidth();

        public abstract int getScrollContentHeight();

        public abstract List<LayoutAttribute> getLayoutAttributesInRect(Rect rect);

        public abstract LayoutAttribute getLayoutAttributeForItemAtPosition(int position);

        public static class LayoutManagerState {
            private final int mLayoutSpaceWidth;
            private final int mLayoutSpaceHeight;
            private final int mItemCount;

            private LayoutManagerState(int layoutSpaceWidth, int layoutSpaceHeight, int itemCount) {
                mLayoutSpaceWidth = layoutSpaceWidth;
                mLayoutSpaceHeight = layoutSpaceHeight;
                mItemCount = itemCount;
            }

            private LayoutManagerState() {
                mLayoutSpaceWidth = 0;
                mLayoutSpaceHeight = 0;
                mItemCount = 0;
            }

            public int getLayoutSpaceWidth() {
                return mLayoutSpaceWidth;
            }

            public int getLayoutSpaceHeight() {
                return mLayoutSpaceHeight;
            }

            public int getItemCount() {
                return mItemCount;
            }
        }

        public static class LayoutAttribute {
            private final int mPosition;
            final Rect mRect;

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
             * Get position of item.
             *
             * @return Adapter position.
             */
            public int getPosition() {
                return mPosition;
            }

            /**
             * Copy absolute rect of item.
             * There is no getter because {@link Rect} is mutable type.
             *
             * @return Absolute rect of item.
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
