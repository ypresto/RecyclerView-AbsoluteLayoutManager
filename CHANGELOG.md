Change Log
==========

Version 0.2.1
----------------------------

- Fix occasional crash at onSaveInstanceState() (#7)

Version 0.2.0
----------------------------

- Add scrollToPositionWithAlignment() and smoothScrollToPositionWithAlignment() methods which behaves like UICollectionViewScrollPosition.
- Add invalidateLayout() method to call prepareLayout() explicitly.
- Save scroll position relative to visible item position.
- Fix scrollToPosition() does not handle padding correctly.
- Fix prepareLayout() is not called when non-structure change or setLayoutManager().
- Fix crash with onSaveInstanceState() after scrollToPosition().

Version 0.1.4
----------------------------

- Fix mutable `Point` object is exposed in instance state and causes unexpected change of scroll offset.
- Fix treating `Point` as `Parcelable` which is only on API >= 13.
- Introduce LayoutManagerState object instead of methods on abstract LayoutProvider class like getLayoutSpaceWidth().
  It should be replaced by getState().getLayoutSpaceWidth().


Version 0.1.3
----------------------------

- Fix view is not updated by onItemChanged if target view is in visible area.
  Thanks to @hiroyukimizukami -san!

Version 0.1.2
----------------------------

- Simplify internal logic.
- Fix incorrectly placed views on incremental fill.

Version 0.1.1
----------------------------

- Fix AndroidManifest has problematic attributes.

Version 0.1.0
----------------------------

- Initial release.
