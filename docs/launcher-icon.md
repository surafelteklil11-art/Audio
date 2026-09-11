# Audio launcher icon

Audio uses a white headphone silhouette with three mint sound bars over a cobalt–indigo gradient. The simple mark stays legible at launcher size. There is no small lettering or decorative frame.

`tools/ic_launcher_art.svg` is the editable artwork. Run `python tools/generate_launcher_icons.py` to regenerate the checked-in Android vectors; no external Python packages are needed.

- Android 7 uses a rounded-square vector at the normal 48dp launcher size.
- Android 8+ uses separate full-bleed background and transparent foreground layers in a 108dp viewport. The central mark stays inside the 66dp safe circle, allowing circular, squircle and other launcher masks without clipping the headphones.
- Android 13+ includes a dedicated monochrome layer for launchers with themed icons enabled.
- Native vector resources replace the old raster density variants and the competing raster foreground, keeping the same artwork sharp at every density.

The application label and launcher resource name remain Audio and `@mipmap/ic_launcher`.

Layout follows [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive). Themed colors are selected by the user's launcher; the standard color icon always uses the fixed brand palette.
