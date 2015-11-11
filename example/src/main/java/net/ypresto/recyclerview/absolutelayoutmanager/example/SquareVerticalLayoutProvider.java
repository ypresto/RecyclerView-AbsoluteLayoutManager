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
