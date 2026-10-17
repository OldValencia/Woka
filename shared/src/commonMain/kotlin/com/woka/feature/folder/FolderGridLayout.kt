package com.woka.feature.folder

import com.woka.core.data.MediaItem

/**
 * Whether a folder's media draws as the photo mosaic rather than as cards (SIDE-004).
 *
 * True when the folder holds media and fewer than half of it is video. A count over
 * the items the grid already has, never a setting: a folder is an album or a shelf
 * of films because of what is in it, and the user moving files in changes the answer
 * without being asked. Sub-folders count for neither side — a library of season
 * folders with one poster beside them is still a shelf.
 *
 * Half and over is video, not "any video": a holiday folder with three clips among
 * three hundred photos is an album, and showing it as titled cards would turn three
 * hundred pictures into three hundred file names.
 */
fun List<MediaItem>.isMostlyPhotos(): Boolean {
    val media = filter { it !is MediaItem.Folder }
    if (media.isEmpty()) return false
    val videos = media.count { it is MediaItem.Video }
    return videos * 2 < media.size
}

/**
 * Whether the grid draws its **Folders** and **Media** headings: whenever the folder
 * has sub-folders, in either layout, whatever the filter shows (2026-10-17).
 *
 * They were the mosaic's alone and appeared only when both kinds were on screen, so
 * a folder of films never had them, and filtering a photo folder to Videos took both
 * away. One rule, read off one fact: sub-folders get a section, and the media under
 * them get theirs — "Media 0" included, which says the filter found nothing rather
 * than leaving the user to wonder where the heading went.
 */
fun List<MediaItem>.hasSections(): Boolean = any { it is MediaItem.Folder }

/**
 * Where the item at [itemIndex] of the folder's list sits in the lazy grid, which
 * holds the section headings as items of their own. Folders sort first
 * (`FolderStateMapper`), so a folder is one heading down and a medium two.
 *
 * Every index the screen hands the grid goes through this — the restored scroll's,
 * the viewer's last file's, the highlighted search result's. Without it, a grid
 * with sections lands one or two items short of the file it was asked for.
 */
fun List<MediaItem>.gridIndexOf(itemIndex: Int): Int {
    if (itemIndex < 0 || !hasSections()) return itemIndex
    return if (getOrNull(itemIndex) is MediaItem.Folder) itemIndex + 1 else itemIndex + 2
}
