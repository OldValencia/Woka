package com.woka.core.data

// The small enums [WokaSettings] persists — moved out of that file when it reached
// the file-size mandate (VF-106).
enum class ViewLayout { SmallGrid, Grid, BigGrid }
enum class SortType { Custom, NameAsc, NameDesc, DateAsc, DateDesc }
enum class CarouselVisibility { AUTO, ALWAYS_ON, HIDDEN }
enum class ScalePreset { Fit, Fill, Original }
// Subtitle appearance (§3). Auto scales the size from the video-panel height; the
// other steps are fixed multipliers of that same relative base, so the user's
// choice stays proportional on window resize instead of being reset.
enum class SubtitleSize { Auto, Small, Medium, Large, ExtraLarge }
enum class SubtitleFont { System, Serif, Monospace }
// Where Now Playing's bubble sits while the sidebar is hidden (AP-006).
enum class WindowCorner { TopLeft, TopRight, BottomLeft, BottomRight }
