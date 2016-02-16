RecyclerView-AbsoluteLayoutManager
==================================

Implement RecyclerView custom layout as easy as UICollectionViewLayout.


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


TODO
----

- Decoration margin is not supported yet.
- Predictive animation is not supported yet.


Compatibility
----

Tested with recyclerview-v7:22.1.1.


Installation
----

Available from jCenter.

Gradle:

```groovy
dependencies {
    compile 'com.android.support:recyclerview-v7:X.Y.Z'
    compile 'net.ypresto.recyclerview.absolutelayoutmanager:absolutelayoutmanager:0.1.1'
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
