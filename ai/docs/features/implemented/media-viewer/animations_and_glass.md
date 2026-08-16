# Plan: App Liveliness (Animations and Apple Glass)

This document contains ideas and concepts for implementing a modern, "live" design in the Apple HIG style.

## 1. Apple Glass (Glassmorphism)
- [Canceled] **Dynamic Background:** The window renders an animated Mesh Gradient. (Canceled: no gradients).
- [x] **Translucent Surfaces:** Filling panels with a translucent color. (Done only for the Dock and DockAccessories).
- [x] **Borders and Highlights:** A 1.dp border to simulate a bevel. (Done: a solid 1.dp border without a gradient).
- [x] **Blur (Frosted Glass):** (Resolved by increasing the background opacity to 0.85f for better readability).

## 2. Animated Footer / Header
- [Canceled] **Footer:** Infinite breathing gradient animation. (Canceled: no gradients).
- [Canceled] **Header:** Translucency. (Canceled: glassmorphism is only applied to the Dock).

## 3. Microinteractions and "Live" UI
- [x] **Wiggle Effect:** Icons (e.g., Settings) slightly rotate back and forth on hover. (Done: a single-shot clockwork-like animation).
- [Canceled] **Scale Bounce:** When clicking/selecting a card, it springily scales down for tactile feedback. (Canceled).
- [x] **Smooth Dropdowns:** Using `AnimatedVisibility` with `expandVertically() + fadeIn()` for smooth appearance. Also added temporary icons for filter menu items (Zoom in, Zoom out, sorting).
- [x] **Search:** Seamless transition (morphing). The input smoothly expands from the search icon at its current position.

## 4. Complex Animations
- [Canceled] **Exponential Scroll/Swipe:** Writing custom `FlingBehavior` with `exponentialDecay`. (Canceled due to bad UX).
- [Canceled] **Staggered Grid:** A wave effect upon item appearance. (Canceled).
- [Canceled] **Shared Element Transitions:** Seamless expansion of an image from the grid to full screen on double click. (Canceled).
- [x] **Adaptive Glow (Shadows):** The shadow around a card is tinted with the dominant color. Implemented **only for selected items**.
