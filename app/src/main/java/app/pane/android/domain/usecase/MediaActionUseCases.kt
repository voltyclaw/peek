package app.pane.android.domain.usecase

import app.pane.android.domain.model.PreparedMedia
import app.pane.android.domain.model.RemoteMedia
import app.pane.android.domain.repository.MediaRepository

class PrepareMediaForSharingUseCase(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(media: List<RemoteMedia>): Result<List<PreparedMedia>> =
        mediaRepository.prepareForSharing(media)
}

class DownloadMediaUseCase(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(media: List<RemoteMedia>): Int =
        mediaRepository.download(media)
}
