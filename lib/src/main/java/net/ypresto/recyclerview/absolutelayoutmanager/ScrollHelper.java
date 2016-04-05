package net.ypresto.recyclerview.absolutelayoutmanager;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;

import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_BOTTOM;
import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_CENTER_HORIZONTAL;
import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_CENTER_VERTICAL;
import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_LEFT;
import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_RIGHT;
import static net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager.SCROLL_ALIGNMENT_TOP;

class ScrollHelper {

    private ScrollHelper() {
        throw new AssertionError();
    }

    static PointF calculateUnitVectorFromPoints(Point currentScrollOffset, Point targetScrollOffset) {
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

    static Point calculateScrollOffsetToShowItem(AbsoluteLayoutManager.LayoutProvider.LayoutAttribute layoutAttribute, Rect layoutSpaceRect, int scrollAlignment) {
        Rect itemRect = layoutAttribute.copyRect();
        Point offset = new Point(layoutSpaceRect.left, layoutSpaceRect.top); // defaults to current position

        if ((scrollAlignment & SCROLL_ALIGNMENT_LEFT) != 0) {
            offset.x = itemRect.left;
        } else if ((scrollAlignment & SCROLL_ALIGNMENT_RIGHT) != 0) {
            offset.x = itemRect.right - layoutSpaceRect.width();
        } else if ((scrollAlignment & SCROLL_ALIGNMENT_CENTER_HORIZONTAL) != 0) {
            offset.x = Math.round(itemRect.exactCenterX() - (layoutSpaceRect.width() / 2.0f));
        } else if (itemRect.left < layoutSpaceRect.left) {
            offset.x = itemRect.left;
        } else if (itemRect.right > layoutSpaceRect.right) {
            offset.x = itemRect.right - layoutSpaceRect.width();
        }

        if ((scrollAlignment & SCROLL_ALIGNMENT_TOP) != 0) {
            offset.y = itemRect.top;
        } else if ((scrollAlignment & SCROLL_ALIGNMENT_BOTTOM) != 0) {
            offset.y = itemRect.bottom - layoutSpaceRect.height();
        } else if ((scrollAlignment & SCROLL_ALIGNMENT_CENTER_VERTICAL) != 0) {
            offset.y = Math.round(itemRect.exactCenterY() - (layoutSpaceRect.height() / 2.0f));
        } else if (itemRect.top < layoutSpaceRect.top) {
            offset.y = itemRect.top;
        } else if (itemRect.bottom > layoutSpaceRect.bottom) {
            offset.y = itemRect.bottom - layoutSpaceRect.height();
        }

        return offset;
    }
}
