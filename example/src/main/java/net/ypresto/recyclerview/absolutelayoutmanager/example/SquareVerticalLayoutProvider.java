package net.ypresto.recyclerview.absolutelayoutmanager.example;

import android.graphics.Rect;

import net.ypresto.recyclerview.absolutelayoutmanager.AbsoluteLayoutManager;

import java.util.ArrayList;
import java.util.List;

public class SquareVerticalLayoutProvider extends AbsoluteLayoutManager.LayoutProvider {
    List<LayoutAttribute> mLayoutAttributes;

    @Override
    public void prepareLayout() {
        int itemCount = getItemCount();
        mLayoutAttributes = new ArrayList<>(itemCount);
        int height = getLayoutSpaceWidth();
        for (int i = 0; i < itemCount; i++) {
            int offsetY = height * i;
            mLayoutAttributes.add(new LayoutAttribute(i, new Rect(0, offsetY, getLayoutSpaceWidth(), offsetY + height)));
        }
    }

    @Override
    public int getScrollContentWidth() {
        return getLayoutSpaceWidth();
    }

    @Override
    public int getScrollContentHeight() {
        return getLayoutSpaceWidth() * getItemCount();
    }

    @Override
    public List<LayoutAttribute> getLayoutAttributesInRect(Rect rect) {
        List<LayoutAttribute> filtered = new ArrayList<>();
        for (LayoutAttribute layoutAttribute : mLayoutAttributes) {
            if (layoutAttribute.isIntersectWithRect(rect)) {
                filtered.add(layoutAttribute);
            }
        }
        return filtered;
    }

    @Override
    public LayoutAttribute getLayoutAttributeForItemAtPosition(int position) {
        return mLayoutAttributes.get(position);
    }
}
