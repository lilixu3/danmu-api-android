package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import androidx.compose.runtime.Composable
import com.example.danmuapiapp.domain.model.AppAnnouncement

@Composable
internal fun HomeUnreadAnnouncementsDialogHost(
    viewModel: HomeViewModel,
    unreadAnnouncements: List<AppAnnouncement>,
    unreadAnnouncementCount: Int,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
) {
    if (activeOverlay == HomeOverlay.UnreadAnnouncements && unreadAnnouncementCount > 1) {
        UnreadAnnouncementListDialog(
            announcements = unreadAnnouncements,
            onDismissRequest = { onDismissOverlay() },
            onOpenAnnouncement = { announcement ->
                onDismissOverlay()
                viewModel.openAnnouncementDetails(announcement)
            },
            onAcknowledgeAll = {
                onDismissOverlay()
                viewModel.acknowledgeAllUnreadAnnouncements()
            }
        )
    }
}

@Composable
internal fun HomeForegroundAnnouncementDialogHost(
    viewModel: HomeViewModel,
    activity: Activity?,
    onOpenAnnouncementRoute: (String) -> Unit,
) {
    val foregroundAnnouncement = viewModel.foregroundAnnouncementPrompt
    if (viewModel.showForegroundAnnouncementDialog && foregroundAnnouncement != null) {
        AnnouncementCenterDialog(
            announcement = foregroundAnnouncement,
            onDismissRequest = {
                if (!foregroundAnnouncement.forcePopup) {
                    viewModel.closeForegroundAnnouncementPrompt()
                }
            },
            onPrimaryAction = {
                val action = foregroundAnnouncement.primaryAction ?: return@AnnouncementCenterDialog
                val route = action.routeOrNull()
                if (route != null) {
                    onOpenAnnouncementRoute(route)
                    viewModel.acknowledgeForegroundAnnouncementPrompt()
                } else {
                    val alive = activity ?: return@AnnouncementCenterDialog
                    viewModel.openForegroundAnnouncementPrimaryAction(alive)
                }
            },
            onSecondaryAction = {
                val action = foregroundAnnouncement.secondaryAction ?: return@AnnouncementCenterDialog
                val route = action.routeOrNull()
                if (route != null) {
                    onOpenAnnouncementRoute(route)
                    viewModel.acknowledgeForegroundAnnouncementPrompt()
                } else {
                    val alive = activity ?: return@AnnouncementCenterDialog
                    viewModel.openForegroundAnnouncementSecondaryAction(alive)
                }
            },
            onMarkRead = viewModel::acknowledgeForegroundAnnouncementPrompt,
            onClose = viewModel::closeForegroundAnnouncementPrompt,
            onSnooze = viewModel::snoozeForegroundAnnouncementPrompt
        )
    }
}

