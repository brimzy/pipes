package com.nazcarbon.music

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.nazcarbon.music.data.*
import kotlinx.coroutines.*
import java.util.UUID

@UnstableApi
internal open class CarMediaLibrary(
    private val context: Context,
    private val scope: CoroutineScope,
    private val source: NewPipeMusicSource,
    private val playable: (MusicTrack, String) -> MediaItem,
    private val prepareQueue: (String, List<MusicTrack>) -> Unit,
) : MediaLibrarySession.Callback {
    private var selectionGeneration = 0
    private val preferences = context.getSharedPreferences("pipes", Context.MODE_PRIVATE)
    private var librarySession: MediaLibrarySession? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("liked", "playlists", "saved_tracks", "audio_downloads")) scope.launch {
            try {
                val library = withContext(Dispatchers.IO) { catalog() }
                val parents = listOf(CarCatalog.ROOT, CarCatalog.LIKED, CarCatalog.PLAYLISTS, CarCatalog.DOWNLOADS) +
                    library.folders(CarCatalog.PLAYLISTS).map { it.id }
                parents.forEach { id -> librarySession?.notifyChildrenChanged(id,
                    library.folders(id).size + library.children(id).size, null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Re-read on the host's next browse request if storage is unavailable. */ }
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        scope.launch {
            try { awaitCancellation() }
            finally { preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
        }
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (session is MediaLibrarySession && canBrowse(session, controller)) librarySession = session
    }

    fun canBrowse(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean {
        if (controller.uid == Process.myUid()) return true
        val manager = context.packageManager
        return CarControllerPolicy.canBrowse(controller.isTrusted, session.isAutomotiveController(controller),
            session.isAutoCompanionController(controller), controller.packageName,
            manager.getPackagesForUid(controller.uid)?.contains(controller.packageName) == true,
            controller.packageName == CarControllerPolicy.ANDROID_AUTO_PACKAGE &&
                manager.checkSignatures("com.google.android.gms", controller.packageName) == PackageManager.SIGNATURE_MATCH)
    }

    private fun catalog(): CarCatalog {
        val playlists = PlaylistStore.load(preferences)
        val known = (playlists + PlaylistStore.load(preferences, "saved_tracks")).flatMap { it.tracks }.associateBy { it.id }
        val liked = preferences.getStringSet("liked", emptySet()).orEmpty().mapNotNull { known[it] }
        val manager = context.getSystemService(DownloadManager::class.java)
        val downloads = AudioDownloads.load(preferences).mapNotNull { download ->
            val uri = manager.getUriForDownloadedFile(download.id) ?: return@mapNotNull null
            MusicTrack("download:${download.id}", download.title, download.artist, 0, "", uri.toString(), uri.toString())
        }
        return CarCatalog(liked, playlists, downloads)
    }

    private fun folder(folder: CarCatalog.Folder) = MediaItem.Builder().setMediaId(folder.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(folder.title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()
    private fun item(entry: CarCatalog.Entry) = MediaItem.Builder().setMediaId(entry.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.track.title).setArtist(entry.track.artist)
            .setArtworkUri(entry.track.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(if (canBrowse(session, browser)) LibraryResult.ofItem(folder(CarCatalog.Folder(CarCatalog.ROOT, "Pipes")), params)
            else LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = task {
        if (!canBrowse(session, browser)) return@task LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
        if (page < 0 || pageSize <= 0) return@task LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        withContext(Dispatchers.IO) {
            val library = catalog()
            if (!library.containsFolder(parentId)) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            else LibraryResult.ofItemList(CarCatalog.page(library.folders(parentId).map(::folder) + library.children(parentId).map(::item), page, pageSize), params)
        }
    }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = task {
        if (!canBrowse(session, browser)) return@task LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
        withContext(Dispatchers.IO) {
            val library = catalog()
            val media = library.folder(mediaId)?.let(::folder) ?: library.find(mediaId)?.let(::item)
            media?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = task {
        if (!canBrowse(session, browser)) return@task LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
        val count = withContext(Dispatchers.IO) {
            val library = catalog()
            if (!library.containsFolder(parentId)) -1 else library.folders(parentId).size + library.children(parentId).size
        }
        if (count < 0) return@task LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        session.notifyChildrenChanged(browser, parentId, count, params)
        LibraryResult.ofVoid(params)
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> = task {
        if (!canBrowse(session, browser)) return@task LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
        if (query.isBlank() || query.length > 200) return@task LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val count = withContext(Dispatchers.IO) { catalog().search(query).size }
        session.notifySearchResultChanged(browser, query, count, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
        page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = task {
        if (!canBrowse(session, browser)) return@task LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
        if (query.isBlank() || query.length > 200 || page < 0 || pageSize <= 0) return@task LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        withContext(Dispatchers.IO) { LibraryResult.ofItemList(CarCatalog.page(catalog().search(query).map(::item), page, pageSize), params) }
    }

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>, startIndex: Int,
        startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val request = ++selectionGeneration
        if (controller.uid == Process.myUid()) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        return task {
            check(canBrowse(session, controller)) { "This controller cannot select library audio." }
            require(mediaItems.size == 1 || (startIndex in mediaItems.indices)) { "Choose a library recording." }
            val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            val selected = mediaItems.getOrNull(index) ?: error("Choose a library recording.")
            val selection = withContext(Dispatchers.IO) {
                val library = catalog()
                val query = selected.requestMetadata.searchQuery
                val entry = if (!query.isNullOrBlank() && query.length <= 200) library.search(query).firstOrNull() else library.find(selected.mediaId)
                entry ?: error("This recording is not in your saved library.")
            }
            val resolved = withContext(Dispatchers.IO) { withTimeout(30_000) { source.resolveForPlayback(selection.track) } }
            check(request == selectionGeneration) { "Playback selection changed." }
            val token = UUID.randomUUID().toString()
            prepareQueue(token, selection.following)
            MediaSession.MediaItemsWithStartPosition(listOf(playable(resolved, token)), 0, startPositionMs.coerceAtLeast(0))
        }
    }

    override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        if (controller.uid == Process.myUid()) return Futures.immediateFuture(mediaItems)
        return Futures.immediateFailedFuture(UnsupportedOperationException("Select a saved recording to start playback."))
    }

    private fun <T> task(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        val job = scope.launch {
            try { future.set(block()) }
            catch (cancelled: CancellationException) { future.cancel(false) }
            catch (error: Exception) { future.setException(error) }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return future
    }
}
