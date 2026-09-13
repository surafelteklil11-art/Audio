# Widget Deep Review

This file records the widget architecture review before the next implementation pass.

## Findings

1. The widget catalog and the actual Android launcher widget must be treated as two separate surfaces.
2. Preview cards must not be mistaken for the runtime `RemoteViews` rendered by the launcher.
3. Every widget style needs its own layout resource, provider metadata, min dimensions, and preview mapping.
4. Widget dimensions must be declared in `appwidget-provider` metadata and match the catalog's grid sizes.
5. The add flow must handle launcher pinning support and unsupported launchers without crashing.
6. Runtime widget updates must use only `RemoteViews`-compatible views and resources.
7. All widget click targets need explicit PendingIntents with immutable/update-current flags as appropriate.
8. Existing Media3 playback must remain the source of truth; widgets must not create a second player instance.
9. Missing artwork must have a deterministic fallback.
10. Long song/artist text must be ellipsized to avoid clipping.
11. The widget page must not rely on fake preview images as the widget implementation.
12. The catalog must expose the same eight styles requested by the product design.
13. Existing side-menu behavior and Settings must remain intact.
14. Existing bottom navigation must remain intact.
15. Widget provider lifecycle methods must be idempotent.
16. Multiple widget instances must not share mutable per-instance state.
17. Widget update operations must tolerate a deleted widget id.
18. The app must not assume a launcher supports pinned widgets.
19. Widget layout backgrounds must be drawable resources rather than runtime custom drawing.
20. The implementation must compile with the repository's existing Android/Kotlin toolchain.

## Required result

The catalog and runtime widget should visually and behaviorally match the supplied references as closely as Android launcher constraints allow, while preserving the existing Audio app architecture.
