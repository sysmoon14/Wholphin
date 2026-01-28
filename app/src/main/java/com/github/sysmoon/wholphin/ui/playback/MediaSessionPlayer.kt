package com.github.sysmoon.wholphin.ui.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import com.github.sysmoon.wholphin.preferences.PlaybackPreferences
import com.github.sysmoon.wholphin.preferences.skipBackOnResume
import com.github.sysmoon.wholphin.ui.seekBack
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

class MediaSessionPlayer(
    player: Player,
    private val controllerViewState: ControllerViewState,
    private val playbackPreferences: PlaybackPreferences,
) : ForwardingSimpleBasePlayer(player) {
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        Timber.v("handleSetPlayWhenReady: playWhenReady=$playWhenReady")
        if (!playWhenReady && player.isPlaying) {
            controllerViewState.showControls()
        } else if (playWhenReady) {
            playbackPreferences.skipBackOnResume?.let {
                player.seekBack(it)
            }
        }
        return super.handleSetPlayWhenReady(playWhenReady)
    }
}
