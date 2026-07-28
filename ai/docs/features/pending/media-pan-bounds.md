# Media Pan Bounds (Photos & Videos)

## 1. Dragging Constraints
When a user zooms into a photo or video and pans it around (e.g., using `Ctrl + Drag`), the media should not be allowed to be dragged entirely off-screen or too far past its edges.

## 2. Expected UX (Rubber-banding / Clamping)
- **Bounds Checking:** Ensure that the edges of the media frame cannot cross past the center of the screen (or ideally, strictly clamp to the screen edges so the user doesn't see a black void if they try to drag past the media's natural border).
- **Spring Effect (Optional but recommended):** If the user forcefully drags past the boundary, add visual resistance (rubber-banding). Upon releasing the mouse, the media should smoothly spring back to align with the edge of the viewport.
