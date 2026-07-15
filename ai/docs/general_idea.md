# General Idea: Iris

Iris is a minimalist, fast, and aesthetically pleasing Desktop media viewer and editor built with Compose Multiplatform (JVM). 

## Core Philosophy
Most desktop OS default photo viewers (like Windows Photos) are clunky, slow, and overly complex. Iris aims to replicate the smoothness and visual clarity of Apple's HIG (Human Interface Guidelines) - specifically macOS Preview and iOS Photos - on Windows and Linux.

It is NOT a full file system explorer. Instead, it operates on a **"Linked Folders"** paradigm. Users curate their gallery by linking specific directories (e.g., `D:\References`, `C:\Vacation`). 

## Primary Use Cases
1. **The Gallery Experience:** A user launches Iris, sees their beautiful grid of Linked Folders, clicks into one, navigates its subfolders, and browses media in a smooth, high-performance Grid.
2. **The Quick Preview:** A user double-clicks an image in standard Windows Explorer. Iris opens instantly as a borderless/minimalist Viewer. Pressing `Esc` closes it. Pressing `X` transitions gracefully into the Folder View, offering to "Link" this folder if it isn't already.
3. **Quick Edits:** While viewing an image, the user can quickly crop, draw an arrow, or trim a video without waiting for Photoshop or Premiere to load.

## Technical Pillars
- **Desktop First:** No mobile compromises. Full support for right-click context menus, mouse-wheel zoom, drag-and-drop, and hardware-accelerated Skia rendering.
- **Zero Cloud, Zero DB:** No cloud sync, no heavy SQL databases. Just reading the local file system and storing basic preferences in a simple JSON file in `Documents/OldValencia/Iris`.
