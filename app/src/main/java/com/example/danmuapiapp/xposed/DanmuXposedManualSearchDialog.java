package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeDisplayTitle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialog behind the injected "APP弹幕" button: search a title, pick a source, pick an
 * episode, push it to the host shell.
 *
 * Landscape shows results and episodes side by side; portrait walks the same data through
 * stages. Stage only drives portrait visibility.
 */
final class DanmuXposedManualSearchDialog {
    private static final int STAGE_SEARCH = 0;
    private static final int STAGE_DRAMA = 1;
    private static final int STAGE_EPISODE = 2;
    private static final int MODE_ANIME = 1;
    private static final int MODE_EPISODE = 2;

    private static final int CELL_HEIGHT_DP = 44;
    private static final int MAX_WIDTH_LANDSCAPE_DP = 880;
    private static final int MAX_WIDTH_PORTRAIT_DP = 560;
    /** Header + search row + footer, excluding the body panels. */
    private static final int CHROME_LANDSCAPE_DP = 132;
    private static final int CHROME_PORTRAIT_DP = 132;

    private static final class SearchDialogState {
        int shellPort;
        int stage = STAGE_SEARCH;
        int mode = MODE_ANIME;
        int selectedEpisodeIndex = 0;
        /** Index into the unfiltered result list, or -1 when nothing is chosen yet. */
        int selectedDramaIndex = -1;
        int gridColumns = 8;
        int gridRowHeightDp = CELL_HEIGHT_DP + DanmuTheme.SPACE_1 * 2;
        long searchRequestId = 0L;
        long detailRequestId = 0L;
        boolean showTitles;
        boolean searching = false;
        boolean active = true;
        final boolean landscape;
        String searchMessage = "";
        String episodeMessage = "";
        String currentMediaTitle = "";
        String currentDramaTitle = "";
        String currentEpisode = "";
        String selectedSource = "";
        Runnable renderContent;
        Runnable applyStageStatus;
        Runnable renderDramaList;

        SearchDialogState(int shellPort, boolean showTitles, boolean landscape) {
            this.shellPort = shellPort;
            this.showTitles = showTitles;
            this.landscape = landscape;
        }
    }

    /** Last completed manual-search session. It deliberately contains no Activity or View references. */
    private static final class DialogSessionSnapshot {
        final String mediaTitle;
        final String keyword;
        final int stage;
        final int mode;
        final int selectedEpisodeIndex;
        final int selectedDramaIndex;
        final String currentDramaTitle;
        final String currentEpisode;
        final String selectedSource;
        final String searchMessage;
        final String episodeMessage;
        final ArrayList<CandidateHandle> animeHandles;
        final ArrayList<SourceFilter> sourceFilters;
        final ArrayList<CandidateHandle> episodeHandles;

        DialogSessionSnapshot(
            String keyword,
            SearchDialogState state,
            List<CandidateHandle> animeHandles,
            List<SourceFilter> sourceFilters,
            List<CandidateHandle> episodeHandles
        ) {
            this.mediaTitle = safe(state.currentMediaTitle);
            this.keyword = safe(keyword).trim();
            this.stage = state.stage;
            this.mode = state.mode;
            this.selectedEpisodeIndex = state.selectedEpisodeIndex;
            this.selectedDramaIndex = state.selectedDramaIndex;
            this.currentDramaTitle = safe(state.currentDramaTitle);
            this.currentEpisode = safe(state.currentEpisode);
            this.selectedSource = safe(state.selectedSource);
            this.searchMessage = safe(state.searchMessage);
            this.episodeMessage = safe(state.episodeMessage);
            this.animeHandles = new ArrayList<>(animeHandles);
            this.sourceFilters = new ArrayList<>(sourceFilters);
            this.episodeHandles = new ArrayList<>(episodeHandles);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    interface Host {
        InjectionSettings readInjectionSettings(Context context, int fallbackPort);

        boolean readEpisodeShowTitles(Context context);

        boolean saveEpisodeShowTitles(Context context, boolean showTitles);

        BridgeResult queryBridgeAnimeSearch(Activity activity, String title);

        BridgeResult loadAnimeDetailDirect(String animeHandle, String episodeHint);

        void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, PushFeedback feedback);

        void autoPushCurrent(Activity activity, int fallbackPort, PushFeedback feedback);

        ShellMedia readShellMedia(int preferredPort);

        String formatLastPushInfo(Context context);

        boolean hasUnviewedPush();

        void markPushHistoryViewed();

        void showPushHistoryDialog(Activity activity, DanmuTheme theme);

        void showSettingsDialog(Activity activity, DanmuTheme theme, int shellPort, Runnable onChanged);

        EpisodeCandidate episodeCandidate(String handle);

        void logError(String message, Throwable throwable);
    }

    private final Host host;
    private DialogSessionSnapshot cachedSession;

    DanmuXposedManualSearchDialog(Host host) {
        this.host = host;
    }

    void show(Activity activity) {
        try {
            InjectionSettings bootSettings = host.readInjectionSettings(activity, 9978);
            final DanmuTheme t = DanmuTheme.of(bootSettings.themeMode.resolveDark(activity));
            final boolean landscape = DanmuDialog.isLandscape(activity);
            SearchDialogState state = new SearchDialogState(
                bootSettings.shellPort, host.readEpisodeShowTitles(activity), landscape);

            LinearLayout root = DanmuDialog.root(activity, t);

            // ---- header: one title line, actions on the right ----
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView headerTitleText = DanmuUi.text(
                activity, t, headerStageTitle(STAGE_SEARCH, landscape),
                DanmuTheme.TEXT_HEADLINE, t.textPrimary, true);
            headerTitleText.setSingleLine(true);
            headerTitleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(headerTitleText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView settingsButton = DanmuUi.iconButton(activity, t, "⚙");
            TextView historyButton = DanmuUi.iconButton(activity, t, "↺");
            TextView closeButton = DanmuUi.iconButton(activity, t, "×");
            TextView notifyDot = DanmuUi.notifyDot(activity, t);

            FrameLayout historyWrapper = new FrameLayout(activity);
            historyWrapper.addView(historyButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                dp(activity, 7), dp(activity, 7), Gravity.TOP | Gravity.END);
            dotLp.topMargin = dp(activity, 5);
            dotLp.rightMargin = dp(activity, 5);
            historyWrapper.addView(notifyDot, dotLp);

            int iconSize = dp(activity, 36);
            header.addView(settingsButton, iconLp(activity, iconSize));
            header.addView(historyWrapper, iconLp(activity, iconSize));
            header.addView(closeButton, iconLp(activity, iconSize));
            root.addView(header, matchWrapWithBottom(activity, DanmuTheme.SPACE_3));

            final Runnable updateNotifyDot = () ->
                notifyDot.setVisibility(host.hasUnviewedPush() ? View.VISIBLE : View.GONE);
            updateNotifyDot.run();

            // ---- persistent search row ----
            LinearLayout searchRow = new LinearLayout(activity);
            searchRow.setOrientation(LinearLayout.HORIZONTAL);
            searchRow.setGravity(Gravity.CENTER_VERTICAL);
            EditText keywordInput = DanmuUi.textField(activity, t, "输入剧名 / 自动读取当前播放", "");
            Button searchButton = DanmuUi.primaryButton(activity, t, "搜索");
            Button actionButton = DanmuUi.secondaryButton(activity, t, "推送");
            searchRow.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
            searchRow.addView(searchButton, buttonLp(activity, 68));
            searchRow.addView(actionButton, buttonLp(activity, 68));
            root.addView(searchRow, matchWrapWithBottom(activity, DanmuTheme.SPACE_3));

            // ---- results column ----
            LinearLayout resultsSection = DanmuUi.panel(activity, t);
            TextView resultsLabel = DanmuUi.sectionLabel(activity, t, "匹配来源");
            resultsSection.addView(resultsLabel, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));
            HorizontalScrollView platformFilterScroll = buildHorizontalChipScroll(activity);
            LinearLayout platformFilterRow = new LinearLayout(activity);
            platformFilterRow.setOrientation(LinearLayout.HORIZONTAL);
            platformFilterRow.setGravity(Gravity.CENTER_VERTICAL);
            platformFilterScroll.addView(platformFilterRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            resultsSection.addView(platformFilterScroll, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            ScrollView resultsScroll = buildContentScroll(activity);
            LinearLayout resultsContainer = new LinearLayout(activity);
            resultsContainer.setOrientation(LinearLayout.VERTICAL);
            resultsScroll.addView(resultsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            resultsSection.addView(resultsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            // ---- episode column ----
            LinearLayout episodeSection = DanmuUi.panel(activity, t);
            final ArrayList<TextView> episodeItemViews = new ArrayList<>();

            LinearLayout episodeToolbar = new LinearLayout(activity);
            episodeToolbar.setOrientation(LinearLayout.HORIZONTAL);
            episodeToolbar.setGravity(Gravity.CENTER_VERTICAL);
            TextView episodeCountText = DanmuUi.sectionLabel(activity, t, "选择剧集");
            Button numberModeButton = DanmuUi.toggleChip(activity, t, "数字", !state.showTitles);
            Button titleModeButton = DanmuUi.toggleChip(activity, t, "标题", state.showTitles);
            Button episodeBackButton = DanmuUi.ghostButton(activity, t, "返回");
            episodeToolbar.addView(episodeCountText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            episodeToolbar.addView(numberModeButton, toggleLp(activity, 48));
            episodeToolbar.addView(titleModeButton, toggleLp(activity, 48));
            episodeToolbar.addView(episodeBackButton, toggleLp(activity, 52));
            episodeBackButton.setVisibility(landscape ? View.GONE : View.VISIBLE);
            episodeSection.addView(episodeToolbar, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            ScrollView episodeScroll = buildContentScroll(activity);
            LinearLayout episodeBody = new LinearLayout(activity);
            episodeBody.setOrientation(LinearLayout.VERTICAL);
            GridLayout episodeGrid = new GridLayout(activity);
            episodeGrid.setUseDefaultMargins(false);
            episodeGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            LinearLayout episodeEmpty = DanmuUi.emptyState(activity, t, "尚未选择来源", "先在左侧选择一个匹配来源");
            episodeBody.addView(episodeEmpty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            episodeBody.addView(episodeGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            episodeScroll.addView(episodeBody, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            episodeSection.addView(episodeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            ArrayList<CandidateHandle> compactHandles = new ArrayList<>();

            // ---- body: two columns in landscape, stage switch in portrait ----
            FrameLayout promptHolder = new FrameLayout(activity);
            promptHolder.addView(
                DanmuUi.emptyState(activity, t, "输入剧名后开始搜索", "也可直接打开，会自动读取当前播放并预填"),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            int bodyHeightPx = bodyHeight(activity, landscape);
            if (landscape) {
                LinearLayout columns = new LinearLayout(activity);
                columns.setOrientation(LinearLayout.HORIZONTAL);
                columns.addView(resultsSection, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f);
                rightLp.leftMargin = dp(activity, DanmuTheme.SPACE_2);
                columns.addView(episodeSection, rightLp);
                LinearLayout.LayoutParams columnsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, bodyHeightPx);
                columnsLp.bottomMargin = dp(activity, DanmuTheme.SPACE_3);
                root.addView(columns, columnsLp);
            } else {
                FrameLayout contentFrame = new FrameLayout(activity);
                contentFrame.addView(promptHolder, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                contentFrame.addView(resultsSection, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                contentFrame.addView(episodeSection, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, bodyHeightPx);
                frameLp.bottomMargin = dp(activity, DanmuTheme.SPACE_3);
                root.addView(contentFrame, frameLp);
            }

            // ---- footer ----
            LinearLayout footer = new LinearLayout(activity);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            TextView statusText = DanmuUi.statusLine(activity, t, "");
            TextView pushInfoText = DanmuUi.chip(activity, t, "", true);
            pushInfoText.setVisibility(View.GONE);
            footer.addView(statusText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            infoLp.leftMargin = dp(activity, DanmuTheme.SPACE_2);
            footer.addView(pushInfoText, infoLp);
            footer.setMinimumHeight(dp(activity, 24));
            root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final PushFeedback feedback = new PushFeedback() {
                @Override
                public void onStatus(String status) {
                    statusText.setText(status == null ? "" : status);
                }

                @Override
                public void onPushInfo(String info) {
                    pushInfoText.setText(info == null ? "" : info);
                    pushInfoText.setVisibility(View.VISIBLE);
                }
            };

            final ArrayList<String> animeLabels = new ArrayList<>();
            final ArrayList<CandidateHandle> animeHandles = new ArrayList<>();
            final ArrayList<SourceFilter> sourceFilters = new ArrayList<>();
            final ArrayList<DanmuUi.ResultRow> dramaRows = new ArrayList<>();
            final ArrayList<Integer> dramaRowIndexes = new ArrayList<>();

            final Runnable refreshEpisodeHeader = () -> {
                if (compactHandles.isEmpty()) {
                    episodeCountText.setText("选择剧集");
                    return;
                }
                String dramaTitle = state.currentDramaTitle;
                episodeCountText.setText(dramaTitle.isEmpty()
                    ? "选择剧集 · 共 " + compactHandles.size() + " 集"
                    : dramaTitle + " · " + compactHandles.size() + " 集");
            };

            final Runnable persistSession = () -> saveCachedSession(
                keywordInput.getText() == null ? "" : keywordInput.getText().toString(),
                state, animeHandles, sourceFilters, compactHandles);
            final Runnable onEpisodeSelectionChanged = () -> {
                refreshEpisodeHeader.run();
                persistSession.run();
            };

            final Runnable renderEpisodeGrid = () -> {
                renderEpisodeGrid(activity, t, episodeGrid, compactHandles, episodeItemViews,
                    state, feedback, onEpisodeSelectionChanged);
                episodeEmpty.setVisibility(compactHandles.isEmpty() ? View.VISIBLE : View.GONE);
                episodeGrid.setVisibility(compactHandles.isEmpty() ? View.GONE : View.VISIBLE);
                refreshEpisodeHeader.run();
            };

            state.renderContent = () -> {
                headerTitleText.setText(headerStageTitle(state.stage, landscape));
                if (!landscape) {
                    promptHolder.setVisibility(state.stage == STAGE_SEARCH ? View.VISIBLE : View.GONE);
                    resultsSection.setVisibility(state.stage == STAGE_DRAMA ? View.VISIBLE : View.GONE);
                    episodeSection.setVisibility(state.stage == STAGE_EPISODE ? View.VISIBLE : View.GONE);
                }
                actionButton.setText("推送");
            };

            state.applyStageStatus = () -> statusText.setText(
                state.stage == STAGE_EPISODE ? state.episodeMessage : state.searchMessage);

            View.OnClickListener modeToggle = v -> {
                boolean wantTitles = v == titleModeButton;
                if (wantTitles == state.showTitles) return;
                state.showTitles = wantTitles;
                host.saveEpisodeShowTitles(activity, wantTitles);
                numberModeButton.setSelected(!wantTitles);
                titleModeButton.setSelected(wantTitles);
                renderEpisodeGrid.run();
                scrollEpisodeGridToIndex(activity, episodeScroll, state.selectedEpisodeIndex,
                    state.gridColumns, state.gridRowHeightDp);
            };
            numberModeButton.setOnClickListener(modeToggle);
            titleModeButton.setOnClickListener(modeToggle);
            episodeBackButton.setOnClickListener(v -> {
                state.stage = STAGE_DRAMA;
                state.renderContent.run();
                state.applyStageStatus.run();
                persistSession.run();
            });

            // Repaints selection without rebuilding rows, so the focused row keeps focus.
            final Runnable applyDramaSelection = () -> {
                for (int i = 0; i < dramaRows.size(); i++) {
                    dramaRows.get(i).view.setSelected(
                        dramaRowIndexes.get(i) == state.selectedDramaIndex);
                }
            };

            state.renderDramaList = () -> {
                resultsContainer.removeAllViews();
                dramaRows.clear();
                dramaRowIndexes.clear();
                renderPlatformFilters(activity, t, platformFilterRow, sourceFilters, state.selectedSource, source -> {
                    state.selectedSource = source == null ? "" : source;
                    persistSession.run();
                    state.renderDramaList.run();
                    platformFilterScroll.post(() -> {
                        if (state.selectedSource.isEmpty()) platformFilterScroll.smoothScrollTo(0, 0);
                    });
                });
                platformFilterScroll.setVisibility(sourceFilters.isEmpty() ? View.GONE : View.VISIBLE);
                if (state.searching) {
                    resultsContainer.addView(
                        DanmuUi.emptyState(activity, t, "搜索中…", "正在向弹幕核心查询，请稍候"),
                        new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                if (animeLabels.isEmpty()) {
                    resultsContainer.addView(
                        DanmuUi.emptyState(activity, t, "无剧名结果", "换个关键词再搜一次"),
                        new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                int visibleIndex = 0;
                for (int i = 0; i < animeLabels.size(); i++) {
                    final CandidateHandle candidate = i < animeHandles.size() ? animeHandles.get(i) : null;
                    if (candidate != null && !state.selectedSource.isEmpty()
                        && !state.selectedSource.equals(candidate.source)) {
                        continue;
                    }
                    visibleIndex++;
                    final int candidateIndex = i;
                    String[] parts = splitDramaLabel(animeLabels.get(i));
                    DanmuUi.ResultRow row = DanmuUi.resultRow(activity, t);
                    // Identity, not title: several sources share one title, so comparing
                    // titles would light up every row at once.
                    row.bind(String.valueOf(visibleIndex), parts[0], parts[1],
                        candidateIndex == state.selectedDramaIndex);
                    row.view.setOnClickListener(v -> {
                        if (candidate == null) return;
                        state.selectedDramaIndex = candidateIndex;
                        state.currentDramaTitle = parts[0];
                        applyDramaSelection.run();
                        loadAnimeDetail(activity, candidate, state, episodeScroll, compactHandles,
                            searchButton, statusText, feedback, renderEpisodeGrid, episodeItemViews,
                            persistSession);
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(activity, DanmuTheme.SPACE_2);
                    resultsContainer.addView(row.view, lp);
                    dramaRows.add(row);
                    dramaRowIndexes.add(candidateIndex);
                }
                if (visibleIndex == 0) {
                    state.selectedSource = "";
                    state.renderDramaList.run();
                    return;
                }
                resultsScroll.post(() -> resultsScroll.scrollTo(0, 0));
            };

            AlertDialog dialog = DanmuDialog.create(activity, root);
            closeButton.setOnClickListener(v -> dialog.dismiss());
            dialog.setOnDismissListener(d -> state.active = false);
            historyButton.setOnClickListener(v -> {
                host.showPushHistoryDialog(activity, t);
                updateNotifyDot.run();
            });
            settingsButton.setOnClickListener(v -> host.showSettingsDialog(activity, t, state.shellPort, () -> {
                dialog.dismiss();
                show(activity);
            }));

            final Runnable searchAction = () -> {
                String keyword = keywordInput.getText() == null ? "" : keywordInput.getText().toString().trim();
                if (keyword.isEmpty()) {
                    Toast.makeText(activity, "先输入剧名", Toast.LENGTH_SHORT).show();
                    return;
                }
                cachedSession = null;
                state.mode = MODE_ANIME;
                compactHandles.clear();
                episodeItemViews.clear();
                state.selectedEpisodeIndex = 0;
                state.selectedDramaIndex = -1;
                state.currentDramaTitle = "";
                state.episodeMessage = "";
                episodeGrid.removeAllViews();
                episodeEmpty.setVisibility(View.VISIBLE);
                episodeGrid.setVisibility(View.GONE);
                refreshEpisodeHeader.run();
                animeLabels.clear();
                animeHandles.clear();
                sourceFilters.clear();
                state.selectedSource = "";
                state.stage = STAGE_DRAMA;
                state.searching = true;
                state.searchMessage = "搜索中…";
                state.detailRequestId++;
                statusText.setText("搜索中…");
                searchButton.setEnabled(false);
                state.renderDramaList.run();
                state.renderContent.run();
                long requestId = ++state.searchRequestId;
                new Thread(() -> {
                    BridgeResult result = host.queryBridgeAnimeSearch(activity, keyword);
                    activity.runOnUiThread(() -> {
                        if (!state.active || requestId != state.searchRequestId) return;
                        state.searching = false;
                        searchButton.setEnabled(true);
                        state.searchMessage = result.message;
                        if (state.stage != STAGE_EPISODE) statusText.setText(result.message);
                        animeLabels.clear();
                        animeHandles.clear();
                        sourceFilters.clear();
                        if (result.ok) {
                            animeHandles.addAll(result.candidates);
                            sourceFilters.addAll(result.filters);
                            for (CandidateHandle candidate : result.candidates) {
                                animeLabels.add(candidate.label);
                            }
                        }
                        state.renderDramaList.run();
                        state.renderContent.run();
                        persistSession.run();
                    });
                }, "DanmuSearchAnime").start();
            };

            searchButton.setOnClickListener(v -> searchAction.run());
            actionButton.setOnClickListener(v -> {
                if (state.mode == MODE_EPISODE && !compactHandles.isEmpty()) {
                    int index = clamp(state.selectedEpisodeIndex, 0, compactHandles.size() - 1);
                    host.pushCandidate(activity, compactHandles.get(index), state.shellPort, feedback);
                } else {
                    host.autoPushCurrent(activity, state.shellPort, feedback);
                }
            });

            DialogSessionSnapshot openingSnapshot = cachedSession;
            if (openingSnapshot != null && restoreCachedSession(
                null, state, animeLabels, animeHandles, sourceFilters, compactHandles)) {
                keywordInput.setText(openingSnapshot.keyword);
                keywordInput.setSelection(keywordInput.getText().length());
                renderEpisodeGrid.run();
                state.renderDramaList.run();
                state.renderContent.run();
                state.applyStageStatus.run();
            }

            dialog.setOnShowListener(d -> {
                host.markPushHistoryViewed();
                state.renderContent.run();
                updateNotifyDot.run();
                feedback.onPushInfo(host.formatLastPushInfo(activity));
                new Thread(() -> {
                    ShellMedia media = host.readShellMedia(state.shellPort);
                    activity.runOnUiThread(() -> {
                        if (!state.active) return;
                        updateNotifyDot.run();
                        feedback.onPushInfo(host.formatLastPushInfo(activity));
                        if (media != null) {
                            state.shellPort = media.port;
                            state.currentEpisode = media.displayEpisode();
                            String normalized = normalizeDisplayTitle(media.title);
                            String mediaTitle = normalized.isEmpty() ? media.title.trim() : normalized;
                            if (!mediaTitle.isEmpty() && restoreCachedSession(
                                mediaTitle, state, animeLabels, animeHandles, sourceFilters, compactHandles)) {
                                DialogSessionSnapshot restored = cachedSession;
                                keywordInput.setText(restored == null ? mediaTitle : restored.keyword);
                                keywordInput.setSelection(keywordInput.getText().length());
                                renderEpisodeGrid.run();
                                state.renderDramaList.run();
                                state.renderContent.run();
                                state.applyStageStatus.run();
                                scrollEpisodeGridToIndex(activity, episodeScroll, state.selectedEpisodeIndex,
                                    state.gridColumns, state.gridRowHeightDp);
                            } else if (!mediaTitle.isEmpty()) {
                                state.currentMediaTitle = mediaTitle;
                                keywordInput.setText(mediaTitle);
                                keywordInput.setSelection(keywordInput.getText().length());
                                searchAction.run();
                            }
                        }
                    });
                }, "DanmuReadMedia").start();
            });

            DanmuDialog.showCentered(dialog, activity,
                landscape ? MAX_WIDTH_LANDSCAPE_DP : MAX_WIDTH_PORTRAIT_DP);
            DanmuDialog.focusFirst(dialog, searchButton);
        } catch (Throwable throwable) {
            Toast.makeText(activity, "打开 APP弹幕 搜索失败：" + throwable.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            host.logError("show manual search dialog failed", throwable);
        }
    }

    private void saveCachedSession(
        String keyword,
        SearchDialogState state,
        List<CandidateHandle> animeHandles,
        List<SourceFilter> sourceFilters,
        List<CandidateHandle> episodeHandles
    ) {
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        if (state.searching || cleanKeyword.isEmpty() || animeHandles.isEmpty()) return;
        cachedSession = new DialogSessionSnapshot(
            cleanKeyword, state, animeHandles, sourceFilters, episodeHandles);
    }

    private boolean restoreCachedSession(
        String currentMediaTitle,
        SearchDialogState state,
        ArrayList<String> animeLabels,
        ArrayList<CandidateHandle> animeHandles,
        ArrayList<SourceFilter> sourceFilters,
        ArrayList<CandidateHandle> episodeHandles
    ) {
        DialogSessionSnapshot snapshot = cachedSession;
        if (snapshot == null || snapshot.keyword.isEmpty() || snapshot.animeHandles.isEmpty()) return false;
        if (currentMediaTitle != null && !canReuseSession(
            snapshot.mediaTitle, snapshot.keyword, currentMediaTitle)) {
            return false;
        }

        String activeEpisode = state.currentEpisode.isEmpty()
            ? snapshot.currentEpisode : state.currentEpisode;
        state.currentMediaTitle = currentMediaTitle == null || currentMediaTitle.trim().isEmpty()
            ? snapshot.mediaTitle : currentMediaTitle.trim();
        state.currentEpisode = activeEpisode;
        state.currentDramaTitle = snapshot.currentDramaTitle;
        state.selectedSource = snapshot.selectedSource;
        state.searchMessage = snapshot.searchMessage;
        state.episodeMessage = snapshot.episodeMessage;
        state.searching = false;

        animeLabels.clear();
        animeHandles.clear();
        sourceFilters.clear();
        episodeHandles.clear();
        animeHandles.addAll(snapshot.animeHandles);
        sourceFilters.addAll(snapshot.sourceFilters);
        episodeHandles.addAll(snapshot.episodeHandles);
        for (CandidateHandle candidate : animeHandles) {
            animeLabels.add(candidate.label);
        }

        state.selectedDramaIndex = snapshot.selectedDramaIndex >= 0
            ? clamp(snapshot.selectedDramaIndex, 0, animeHandles.size() - 1) : -1;
        if (episodeHandles.isEmpty()) {
            state.mode = MODE_ANIME;
            state.stage = STAGE_DRAMA;
            state.selectedEpisodeIndex = 0;
        } else {
            state.mode = snapshot.mode == MODE_EPISODE ? MODE_EPISODE : MODE_ANIME;
            state.stage = snapshot.stage == STAGE_EPISODE ? STAGE_EPISODE : STAGE_DRAMA;
            state.selectedEpisodeIndex = findEpisodeIndex(
                episodeHandles, activeEpisode, snapshot.selectedEpisodeIndex);
        }
        return true;
    }

    static boolean canReuseSession(String cachedMediaTitle, String cachedKeyword, String currentMediaTitle) {
        String current = currentMediaTitle == null ? "" : currentMediaTitle.trim();
        if (current.isEmpty()) return false;
        String media = cachedMediaTitle == null ? "" : cachedMediaTitle.trim();
        if (!media.isEmpty()) return media.equalsIgnoreCase(current);
        String keyword = cachedKeyword == null ? "" : cachedKeyword.trim();
        return !keyword.isEmpty() && keyword.equalsIgnoreCase(current);
    }

    private int findEpisodeIndex(List<CandidateHandle> handles, String episodeHint, int fallbackIndex) {
        if (handles == null || handles.isEmpty()) return 0;
        int episodeNumber = extractEpisodeNumber(episodeHint == null ? "" : episodeHint);
        if (episodeNumber > 0) {
            for (int i = 0; i < handles.size(); i++) {
                CandidateHandle candidate = handles.get(i);
                if (candidate != null && extractEpisodeNumber(candidate.label) == episodeNumber) return i;
            }
        }
        return clamp(fallbackIndex, 0, handles.size() - 1);
    }

    private void loadAnimeDetail(
        Activity activity,
        CandidateHandle anime,
        SearchDialogState state,
        ScrollView episodeScroll,
        ArrayList<CandidateHandle> compactHandles,
        Button searchButton,
        TextView statusText,
        PushFeedback feedback,
        Runnable renderEpisodeGrid,
        ArrayList<TextView> episodeItemViews,
        Runnable persistSession
    ) {
        state.mode = MODE_ANIME;
        state.selectedEpisodeIndex = 0;
        state.episodeMessage = "";
        compactHandles.clear();
        episodeItemViews.clear();
        renderEpisodeGrid.run();
        persistSession.run();
        statusText.setText("正在加载剧集：" + anime.label);
        searchButton.setEnabled(false);
        long requestId = ++state.detailRequestId;
        new Thread(() -> {
            BridgeResult result = host.loadAnimeDetailDirect(anime.handle, state.currentEpisode);
            activity.runOnUiThread(() -> {
                if (!state.active || requestId != state.detailRequestId) return;
                searchButton.setEnabled(true);
                statusText.setText(result.message);
                if (result.ok && !result.candidates.isEmpty()) {
                    state.episodeMessage = result.message;
                    state.mode = MODE_EPISODE;
                    compactHandles.clear();
                    compactHandles.addAll(result.candidates);
                    int targetIndex = findEpisodeIndex(
                        result.candidates, state.currentEpisode, result.selectedIndex);
                    state.selectedEpisodeIndex = targetIndex;
                    feedback.onPushInfo(host.formatLastPushInfo(activity));
                    renderEpisodeGrid.run();
                    state.stage = STAGE_EPISODE;
                    state.renderContent.run();
                    scrollEpisodeGridToIndex(activity, episodeScroll, targetIndex,
                        state.gridColumns, state.gridRowHeightDp);
                    focusEpisodeCell(episodeItemViews, targetIndex);
                    persistSession.run();
                } else {
                    state.mode = MODE_ANIME;
                    state.selectedEpisodeIndex = 0;
                    state.episodeMessage = "";
                    compactHandles.clear();
                    renderEpisodeGrid.run();
                    state.renderContent.run();
                    persistSession.run();
                }
            });
        }, "DanmuAnimeDetail").start();
    }

    private void renderEpisodeGrid(
        Activity activity,
        DanmuTheme t,
        GridLayout episodeGrid,
        ArrayList<CandidateHandle> compactHandles,
        ArrayList<TextView> itemViews,
        SearchDialogState state,
        PushFeedback feedback,
        Runnable onSelectionChanged
    ) {
        episodeGrid.removeAllViews();
        itemViews.clear();
        if (compactHandles.isEmpty()) {
            state.gridColumns = 1;
            state.gridRowHeightDp = 40;
            return;
        }
        int columns = state.showTitles ? 1 : computeEpisodeColumns(activity, episodeGrid, state.landscape);
        state.gridColumns = columns;
        state.gridRowHeightDp = CELL_HEIGHT_DP + DanmuTheme.SPACE_1 * 2;
        episodeGrid.setColumnCount(columns);
        episodeGrid.setMinimumWidth(0);
        episodeGrid.setClipToPadding(false);
        int marginPx = dp(activity, DanmuTheme.SPACE_1);
        int selected = clamp(state.selectedEpisodeIndex, 0, compactHandles.size() - 1);
        state.selectedEpisodeIndex = selected;
        for (int i = 0; i < compactHandles.size(); i++) {
            final CandidateHandle candidate = compactHandles.get(i);
            final int index = i;
            TextView cell = DanmuUi.episodeCell(activity, t);
            DanmuUi.styleEpisodeCell(activity, t, cell,
                episodeCellLabel(candidate, index, state.showTitles), index == selected, state.showTitles);
            cell.setOnClickListener(v -> {
                int prev = state.selectedEpisodeIndex;
                if (prev == index) {
                    host.pushCandidate(activity, candidate, state.shellPort, feedback);
                    return;
                }
                state.selectedEpisodeIndex = index;
                if (prev >= 0 && prev < itemViews.size()) {
                    DanmuUi.styleEpisodeCell(activity, t, itemViews.get(prev),
                        episodeCellLabel(compactHandles.get(prev), prev, state.showTitles), false, state.showTitles);
                }
                DanmuUi.styleEpisodeCell(activity, t, cell,
                    episodeCellLabel(candidate, index, state.showTitles), true, state.showTitles);
                feedback.onStatus("已选中第" + shortEpisodeLabel(candidate, index) + "集，再点一次或按推送执行");
                if (onSelectionChanged != null) onSelectionChanged.run();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(activity, CELL_HEIGHT_DP);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            episodeGrid.addView(cell, lp);
            itemViews.add(cell);
        }
        DanmuUi.wireGridFocus(episodeGrid, columns);
        ViewGroup.LayoutParams existing = episodeGrid.getLayoutParams();
        if (existing != null) {
            existing.width = ViewGroup.LayoutParams.MATCH_PARENT;
            existing.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            episodeGrid.setLayoutParams(existing);
        }
    }

    private void renderPlatformFilters(Activity activity, DanmuTheme t, LinearLayout chipsRow,
                                       List<SourceFilter> filters, String selectedSource,
                                       FilterSelectListener listener) {
        chipsRow.removeAllViews();
        boolean allSelected = selectedSource == null || selectedSource.trim().isEmpty();
        TextView allChip = DanmuUi.filterChip(activity, t, "全部 " + countAllFilters(filters), allSelected);
        allChip.setOnClickListener(v -> {
            if (listener != null) listener.onSelect("");
        });
        chipsRow.addView(allChip, chipLp(activity));

        for (SourceFilter filter : filters) {
            if (filter == null || filter.source.isEmpty() || filter.count <= 0) continue;
            boolean selected = filter.source.equals(selectedSource == null ? "" : selectedSource.trim());
            TextView chip = DanmuUi.filterChip(activity, t, filter.displayName() + " " + filter.count, selected);
            chip.setOnClickListener(v -> {
                if (listener != null) listener.onSelect(filter.source);
            });
            chipsRow.addView(chip, chipLp(activity));
        }
    }

    private int countAllFilters(List<SourceFilter> filters) {
        int count = 0;
        if (filters == null) return 0;
        for (SourceFilter filter : filters) {
            if (filter != null) count += Math.max(0, filter.count);
        }
        return count;
    }

    private void focusEpisodeCell(ArrayList<TextView> itemViews, int index) {
        if (itemViews == null || index < 0 || index >= itemViews.size()) return;
        TextView cell = itemViews.get(index);
        cell.post(() -> {
            if (!cell.isInTouchMode()) cell.requestFocus();
        });
    }

    private ScrollView buildContentScroll(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        scroll.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            return false;
        });
        return scroll;
    }

    private HorizontalScrollView buildHorizontalChipScroll(Activity activity) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setScrollbarFadingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setFillViewport(false);
        scroll.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            return false;
        });
        return scroll;
    }

    private String[] splitDramaLabel(String label) {
        String value = label == null ? "" : label.trim();
        int sep = value.indexOf(" · ");
        if (sep < 0) return new String[]{value, ""};
        return new String[]{value.substring(0, sep), value.substring(sep + 3)};
    }

    private String episodeCellLabel(CandidateHandle candidate, int index, boolean showTitles) {
        if (showTitles) return buildEpisodeTitleLabel(candidate, index);
        return shortEpisodeLabel(candidate, index);
    }

    private String buildEpisodeTitleLabel(CandidateHandle candidate, int index) {
        int number = candidate == null ? 0 : extractEpisodeNumber(candidate.label);
        String head = number > 0 ? "第" + number + "集" : "第" + (index + 1) + "集";
        EpisodeCandidate episode = candidate == null ? null : host.episodeCandidate(candidate.handle);
        String title = episode == null ? "" : episode.name.trim();
        if (!title.isEmpty() && !title.equals(String.valueOf(number)) && !title.equals(head)) {
            return head + " · " + title;
        }
        return head;
    }

    private String shortEpisodeLabel(CandidateHandle candidate, int index) {
        int number = extractEpisodeNumber(candidate == null ? "" : candidate.label);
        return number > 0 ? String.valueOf(number) : String.valueOf(index + 1);
    }

    /** Uses the grid's measured width once available; the first render falls back to an estimate. */
    private int computeEpisodeColumns(Activity activity, GridLayout grid, boolean landscape) {
        int measured = grid == null ? 0 : grid.getWidth();
        int available = measured > 0 ? measured : estimateGridWidth(activity, landscape);
        int perItem = dp(activity, 56);
        int columns = Math.max(1, available / perItem);
        return clamp(columns, 4, landscape ? 12 : 8);
    }

    private int estimateGridWidth(Activity activity, boolean landscape) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int cap = dp(activity, landscape ? MAX_WIDTH_LANDSCAPE_DP : MAX_WIDTH_PORTRAIT_DP);
        int dialogWidth = Math.min(screenWidth - dp(activity, DanmuTheme.SPACE_4 * 2), cap);
        int inner = dialogWidth - dp(activity, DanmuTheme.SPACE_5 * 2 + DanmuTheme.SPACE_2 * 2);
        // Landscape splits the body into two columns weighted 1 : 1.15.
        return landscape ? (int) (inner * 0.53f) : inner;
    }

    private void scrollEpisodeGridToIndex(Activity activity, ScrollView episodeScroll, int index, int columns, int rowHeightDp) {
        episodeScroll.post(() -> {
            int safeColumns = Math.max(1, columns);
            int row = Math.max(0, index) / safeColumns;
            int y = Math.max(0, row * dp(activity, rowHeightDp) - dp(activity, DanmuTheme.SPACE_6));
            episodeScroll.smoothScrollTo(0, y);
        });
    }

    /**
     * The body gets everything the window allows minus the fixed chrome. Landscape screens are
     * short, so the floor stays low enough that the panels never push the footer off screen.
     */
    private int bodyHeight(Activity activity, boolean landscape) {
        int budget = DanmuDialog.contentHeight(
            activity, landscape ? CHROME_LANDSCAPE_DP : CHROME_PORTRAIT_DP);
        return clamp(budget, dp(activity, landscape ? 180 : 260), dp(activity, 900));
    }

    private String headerStageTitle(int stage, boolean landscape) {
        if (landscape) return "APP弹幕";
        switch (stage) {
            case STAGE_DRAMA:
                return "选择匹配来源";
            case STAGE_EPISODE:
                return "选择剧集推送";
            case STAGE_SEARCH:
            default:
                return "搜索当前播放";
        }
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, bottomDp);
        return lp;
    }

    private LinearLayout.LayoutParams buttonLp(Activity activity, int widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            dp(activity, widthDp), dp(activity, 46));
        lp.leftMargin = dp(activity, DanmuTheme.SPACE_2);
        return lp;
    }

    private LinearLayout.LayoutParams toggleLp(Activity activity, int widthDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            dp(activity, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(activity, DanmuTheme.SPACE_1);
        return lp;
    }

    private LinearLayout.LayoutParams iconLp(Activity activity, int size) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = dp(activity, DanmuTheme.SPACE_1);
        return lp;
    }

    private LinearLayout.LayoutParams chipLp(Activity activity) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(activity, DanmuTheme.SPACE_2);
        return lp;
    }

    private int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
