package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.Composable
import com.example.danmuapiapp.ui.screen.favorites.FavoriteListPane

@Composable
internal fun ApiTestFavoritePane(viewModel: ApiTestViewModel, base: String, onOpen: (ApiTestFavoriteItem) -> Unit) {
    FavoriteListPane(
        items = viewModel.favoriteItems,
        supportState = viewModel.favoriteSupportState,
        scheduledRefreshSupported = viewModel.scheduledFavoriteRefreshSupported,
        loadError = viewModel.favoriteLoadError,
        operation = viewModel.favoriteOperation,
        operationKeyword = viewModel.favoriteOperationKeyword,
        emptyHint = "在「手动匹配」里搜索后点收藏即可添加",
        openEnabled = !viewModel.isSearchingAnime,
        onLoad = { viewModel.loadFavorites(base) },
        onOpen = onOpen,
        onRefresh = { viewModel.refreshFavorite(base, it) },
        onRemove = { viewModel.removeFavorite(base, it) },
        onSchedule = { item, draft -> viewModel.updateFavoriteSchedule(base, item, draft) }
    )
}
