package net.ypresto.recyclerview.absolutelayoutmanager;

import android.graphics.Point;
import android.graphics.Rect;

import java.util.Arrays;
import java.util.List;

class AnchorHelper {

    private AnchorHelper() {
        throw new AssertionError();
    }

    static AnchorInfo calculateAnchorItemInRect(AbsoluteLayoutManager.LayoutProvider layoutProvider, Point currentScrollOffset, Rect rect) {
        List<AbsoluteLayoutManager.LayoutProvider.LayoutAttribute> layoutAttributesInRect = layoutProvider.getLayoutAttributesInRect(rect);
        AbsoluteLayoutManager.LayoutProvider.LayoutAttribute nearestLayoutAttribute = null;
        Corner nearestCorner = null;
        double nearestDistance = Double.MAX_VALUE;
        int currentX = currentScrollOffset.x;
        int currentY = currentScrollOffset.y;

        Point point = new Point();
        for (AbsoluteLayoutManager.LayoutProvider.LayoutAttribute layoutAttribute : layoutAttributesInRect) {
            for (Corner corner : Corner.VALUES) {
                corner.writeToPointForRect(point, layoutAttribute.mRect);
                // skip not visible
                // NOTE: Not using rect.contains() because it does not include right and bottom edge.
                if (point.x < rect.left || point.y < rect.top || point.x > rect.right || point.y > rect.bottom)
                    continue;
                double distance = Math.sqrt(Math.pow(point.x - currentX, 2) * Math.pow(point.y - currentY, 2));
                if (distance < nearestDistance) {
                    nearestLayoutAttribute = layoutAttribute;
                    nearestCorner = corner;
                    nearestDistance = distance;
                }
            }
        }

        if (nearestLayoutAttribute == null) {
            return new AnchorInfo(layoutProvider.getLayoutAttributeForItemAtPosition(0), Corner.TOP_LEFT);
        }
        return new AnchorInfo(nearestLayoutAttribute, nearestCorner);
    }

    enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        static final List<Corner> VALUES = Arrays.asList(values());

        Point getPointForRect(Rect rect) {
            Point point = new Point();
            writeToPointForRect(point, rect);
            return point;
        }

        void writeToPointForRect(Point point, Rect rect) {
            switch (this) {
                case TOP_LEFT:
                    point.set(rect.left, rect.top);
                    break;
                case TOP_RIGHT:
                    point.set(rect.right, rect.top);
                    break;
                case BOTTOM_LEFT:
                    point.set(rect.left, rect.bottom);
                    break;
                case BOTTOM_RIGHT:
                    point.set(rect.right, rect.bottom);
                    break;
            }
        }
    }

    static class AnchorInfo {
        final AbsoluteLayoutManager.LayoutProvider.LayoutAttribute mLayoutAttribute;
        final Corner mCorner;

        private AnchorInfo(AbsoluteLayoutManager.LayoutProvider.LayoutAttribute layoutAttribute, Corner corner) {
            mLayoutAttribute = layoutAttribute;
            mCorner = corner;
        }

        Point getPoint() {
            return mCorner.getPointForRect(mLayoutAttribute.mRect);
        }
    }
}
