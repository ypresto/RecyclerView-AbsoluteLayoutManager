RecyclerView-AbsoluteLayoutManager
==================================

Implement RecyclerView custom layout as easy as UICollectionViewLayout.

![example.gif](https://cloud.githubusercontent.com/assets/400558/13219481/a4a7a436-d9b3-11e5-910c-e13e57979ec9.gif)


Why
----

Implementing custom RecyclerView.LayoutManager is much more difficult
than UICollectionViewLayout in iOS.
Key difference is that LayoutManager fills visible child views relative to
already placed child view, on other hand UICollectionViewLayout calculates
absolute position of each cell.

Although relative approach enables to handle `wrap_content` on child views,
but is too complicated if you don't want it.


Usage
----

Implement `AbsoluteLayoutManager.LayoutProvider`.
It is just same as UICollectionViewLayout. :)

See [example](example/src/main/java/net/ypresto/recyclerview/absolutelayoutmanager/example/SquareVerticalLayoutProvider.java).

If you want to divide screen into columns or to use fixed width space
between cells, you will end up to fight with 1px layout shift.
To keep away from rounding error, you can use [SpanCalculator](example/src/main/java/net/ypresto/recyclerview/absolutelayoutmanager/SpanCalculator.java
class to calculate absolute start / end pixel of each columns in length to be
divided.

TODO
----

- Decoration margin is not supported yet.
- Predictive animation is not supported yet.


Installation
----

Available from jCenter.

Gradle:

```groovy
dependencies {
    implementation 'com.android.support:recyclerview-v7:X.Y.Z'
    implementation 'net.ypresto.recyclerview.absolutelayoutmanager:absolutelayoutmanager:0.3.0'
}
```


LICENSE
----

```
Copyright (C) 2015 Yuya Tanaka

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
