package com.bsoft.compose.bmusic.data.models

/** Where a top-level folder lives, so the UI can label it ("Internal storage", "SD card"). */
enum class VolumeKind { INTERNAL, EXTERNAL, OTHER }

/**
 * A folder of the music library, built from the songs' file paths.
 *
 * @property path absolute path without a trailing slash; "" for the virtual root.
 * @property volume set on storage-volume nodes only (shown when there is more than one).
 * @property folders sub-folders that contain music somewhere below them, sorted by name.
 * @property songs the songs directly in this folder, sorted by file name.
 * @property songCount all songs in this folder and below.
 */
data class MusicFolder(
    val path: String,
    val name: String,
    val folders: List<MusicFolder>,
    val songs: List<Song>,
    val songCount: Int,
    val volume: VolumeKind? = null,
) {
    /** Stable ID for media IDs and saved queues (paths can contain any character). */
    val key: String get() = folderKey(path)

    /** This folder's songs, then each sub-folder's, depth first: the order folders play in. */
    fun allSongs(): List<Song> = buildList { collectSongs(this@MusicFolder, this) }

    /** The folder at [path] in this tree, or null. */
    fun find(path: String): MusicFolder? = trail(path).lastOrNull()

    /** The folder whose [key] is [key], or null. */
    fun findByKey(key: String): MusicFolder? {
        if (this.key == key) return this
        return folders.firstNotNullOfOrNull { it.findByKey(key) }
    }

    /** The folders from the root down to [path] (inclusive), for breadcrumbs. */
    fun trail(path: String): List<MusicFolder> {
        if (this.path == path) return listOf(this)
        for (child in folders) {
            val below = child.trail(path)
            if (below.isNotEmpty()) return listOf(this) + below
        }
        return emptyList()
    }

    private fun collectSongs(folder: MusicFolder, into: MutableList<Song>) {
        into += folder.songs
        folder.folders.forEach { collectSongs(it, into) }
    }
}

/** FNV-1a 64-bit hash of the path, in hex: short, stable across launches, URL-safe. */
fun folderKey(path: String): String {
    var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    for (ch in path) {
        hash = hash xor ch.code.toLong()
        hash *= 0x100000001b3L
    }
    return java.lang.Long.toHexString(hash)
}

object FolderTree {

    private val emulated = Regex("^/storage/emulated/\\d+")
    private val removable = Regex("^/storage/[^/]+")

    /** The storage volume a path is on: its root directory and kind. */
    fun volumeOf(path: String): Pair<String, VolumeKind> {
        emulated.find(path)?.let { return it.value to VolumeKind.INTERNAL }
        removable.find(path)?.let { match ->
            if (match.value != "/storage/self") return match.value to VolumeKind.EXTERNAL
        }
        return "/" to VolumeKind.OTHER
    }

    /**
     * Builds the folder tree. The root's children are the storage volumes, or, when all music
     * is on one volume, that volume's folders directly. Songs without a path are left out.
     */
    fun build(songs: List<Song>): MusicFolder {
        class Node(val path: String, val name: String, val volume: VolumeKind? = null) {
            val folders = LinkedHashMap<String, Node>()
            val songs = mutableListOf<Song>()
        }

        val volumes = LinkedHashMap<String, Node>()
        for (song in songs) {
            val path = song.path
            val slash = path.lastIndexOf('/')
            if (slash <= 0) continue
            val directory = path.substring(0, slash)
            val (volumeRoot, kind) = volumeOf(directory)
            val volume = volumes.getOrPut(volumeRoot) { Node(volumeRoot, volumeRoot.substringAfterLast('/').ifEmpty { "/" }, kind) }
            val relative = directory.removePrefix(volumeRoot).trim('/')
            var node = volume
            if (relative.isNotEmpty()) {
                for (segment in relative.split('/')) {
                    if (segment.isEmpty()) continue
                    val childPath = if (node.path == "/") "/$segment" else node.path + "/" + segment
                    node = node.folders.getOrPut(segment) { Node(childPath, segment) }
                }
            }
            node.songs += song
        }

        val nameOrder = compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { it }
        fun freeze(node: Node): MusicFolder {
            val children = node.folders.values.map(::freeze).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            val direct = node.songs.sortedWith(compareBy(nameOrder) { it.displayName.ifEmpty { it.path.substringAfterLast('/') } })
            return MusicFolder(
                path = node.path,
                name = node.name,
                folders = children,
                songs = direct,
                songCount = direct.size + children.sumOf { it.songCount },
                volume = node.volume,
            )
        }

        val frozen = volumes.values.map(::freeze)
        // One volume: skip the "Internal storage" level and show its folders directly. The
        // root is virtual (path ""); every real folder keeps its absolute path.
        val single = frozen.singleOrNull()
        if (single != null) {
            return MusicFolder(path = ROOT_PATH, name = "", folders = single.folders, songs = single.songs, songCount = single.songCount)
        }
        val ordered = frozen.sortedWith(compareBy<MusicFolder> { it.volume?.ordinal ?: Int.MAX_VALUE }.thenBy { it.path })
        return MusicFolder(path = ROOT_PATH, name = "", folders = ordered, songs = emptyList(), songCount = ordered.sumOf { it.songCount })
    }

    /** Path of the virtual root folder. */
    const val ROOT_PATH = ""
}
