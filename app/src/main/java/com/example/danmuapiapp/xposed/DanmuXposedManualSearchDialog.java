package com.example.danmuapiapp.xposed;

import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.extractEpisodeNumber;
import static com.example.danmuapiapp.xposed.DanmuXposedTextPolicy.normalizeDisplayTitle;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
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

final class DanmuXposedManualSearchDialog {
    private static final int STAGE_SEARCH = 0;
    private static final int STAGE_DRAMA = 1;
    private static final int STAGE_EPISODE = 2;
    private static final int MODE_ANIME = 1;
    private static final int MODE_EPISODE = 2;

    interface Host {
        InjectionSettings readInjectionSettings(Context context, int fallbackPort);

        boolean readEpisodeShowTitles(Context context);

        boolean saveEpisodeShowTitles(Context context, boolean showTitles);

        BridgeResult queryBridgeAnimeSearch(Activity activity, String title);

        BridgeResult loadAnimeDetailDirect(String animeHandle, String episodeHint);

        void pushCandidate(Activity activity, CandidateHandle candidate, int shellPort, TextView statusText, TextView pushInfoText);

        void autoPushCurrent(Activity activity, int fallbackPort, TextView statusText, TextView pushInfoText);

        ShellMedia readShellMedia(int preferredPort);

        String formatPushTimeChip();

        String formatLastPushInfo(Context context);

        boolean hasUnviewedPush();

        void markPushHistoryViewed();

        void showPushHistoryDialog(Activity activity, DanmuTheme theme, View notifyButton, TextView notifyDot);

        EpisodeCandidate episodeCandidate(String handle);

        void logError(String message, Throwable throwable);
    }

    private final Host host;

    DanmuXposedManualSearchDialog(Host host) {
        this.host = host;
    }

    void show(Activity activity) {
        try {
            InjectionSettings bootSettings = host.readInjectionSettings(activity, 9978);
            final int[] shellPort = new int[]{bootSettings.shellPort};
            final DanmuTheme t = DanmuTheme.of(bootSettings.darkTheme);
            final boolean isCenter = bootSettings.dialogStyle == InjectionSettings.DIALOG_STYLE_CENTER;

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            int padH = dp(activity, DanmuTheme.SPACE_4);
            int padTop = isCenter ? dp(activity, DanmuTheme.SPACE_4) : dp(activity, DanmuTheme.SPACE_3);
            int padBottom = dp(activity, DanmuTheme.SPACE_4);
            root.setPadding(padH, padTop, padH, padBottom);

            if (!isCenter) {
                root.addView(DanmuUi.dragHandle(activity, t), DanmuUi.dragHandleLp(activity, t));
            }

            final int[] stage = new int[]{STAGE_SEARCH};
            final int[] reachable = new int[]{STAGE_SEARCH};
            final Runnable[] renderContent = new Runnable[1];
            final Runnable[] applyStageStatus = new Runnable[1];
            final String[] searchMessage = new String[]{""};
            final String[] episodeMessage = new String[]{""};
            final String[] currentDramaTitle = new String[]{""};

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(activity, DanmuTheme.SPACE_2), dp(activity, 4), dp(activity, 6), dp(activity, 4));
            header.setBackground(t.roundRect(t.surface, DanmuTheme.RADIUS_LG, t.stroke, 1, activity));

            TextView brandMark = DanmuUi.text(activity, t, "弹幕", DanmuTheme.TEXT_CAPTION, t.accentSoftText, true);
            brandMark.setGravity(Gravity.CENTER);
            brandMark.setPadding(dp(activity, DanmuTheme.SPACE_2), 0, dp(activity, DanmuTheme.SPACE_2), 0);
            brandMark.setBackground(t.roundRect(t.accentSoft, DanmuTheme.RADIUS_PILL, activity));
            LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 28));
            brandLp.setMargins(0, 0, dp(activity, DanmuTheme.SPACE_2), 0);
            header.addView(brandMark, brandLp);

            TextView headerTitleText = DanmuUi.text(activity, t, headerStageTitle(STAGE_SEARCH), DanmuTheme.TEXT_LABEL, t.textPrimary, true);
            headerTitleText.setSingleLine(true);
            headerTitleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(headerTitleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView pushSummaryText = DanmuUi.text(activity, t, host.formatPushTimeChip(), DanmuTheme.TEXT_CAPTION, t.textMuted, false);
            pushSummaryText.setSingleLine(true);
            pushSummaryText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            pushSummaryText.setGravity(Gravity.CENTER);
            pushSummaryText.setPadding(dp(activity, DanmuTheme.SPACE_2), 0, dp(activity, DanmuTheme.SPACE_2), 0);
            pushSummaryText.setBackground(t.roundRect(t.surfaceAlt, DanmuTheme.RADIUS_PILL, t.stroke, 1, activity));
            LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 28));
            summaryLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            header.addView(pushSummaryText, summaryLp);

            Button notifyButton = DanmuUi.ghostButton(activity, t, "↺");
            notifyButton.setTextSize(DanmuTheme.TEXT_LABEL);
            LinearLayout.LayoutParams notifyLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
            notifyLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            final TextView[] notifyDot = new TextView[1];
            notifyDot[0] = new TextView(activity);
            notifyDot[0].setText("");
            notifyDot[0].setGravity(Gravity.CENTER);
            notifyDot[0].setBackground(t.roundRect(t.danger, DanmuTheme.RADIUS_PILL, activity));

            final Runnable updateNotifyDot = () -> {
                if (host.hasUnviewedPush()) {
                    notifyDot[0].setVisibility(View.VISIBLE);
                } else {
                    notifyDot[0].setVisibility(View.GONE);
                }
            };
            updateNotifyDot.run();

            FrameLayout notifyWrapper = new FrameLayout(activity);
            notifyWrapper.addView(notifyButton, new FrameLayout.LayoutParams(
                dp(activity, 32), dp(activity, 32), Gravity.CENTER));
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(
                dp(activity, 7), dp(activity, 7), Gravity.TOP | Gravity.END);
            dotLp.topMargin = dp(activity, 5);
            dotLp.rightMargin = dp(activity, 5);
            notifyWrapper.addView(notifyDot[0], dotLp);
            notifyButton.setOnClickListener(v -> host.showPushHistoryDialog(activity, t, notifyButton, notifyDot[0]));

            Button closeButton = DanmuUi.ghostButton(activity, t, "×");
            closeButton.setTextSize(DanmuTheme.TEXT_TITLE);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
            closeLp.setMargins(dp(activity, DanmuTheme.SPACE_1), 0, 0, 0);
            header.addView(notifyWrapper, notifyLp);
            header.addView(closeButton, closeLp);
            root.addView(header, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            LinearLayout searchRow = new LinearLayout(activity);
            searchRow.setOrientation(LinearLayout.HORIZONTAL);
            searchRow.setGravity(Gravity.CENTER_VERTICAL);
            EditText keywordInput = DanmuUi.textField(activity, t, "输入剧名 / 自动读取当前播放", "");
            Button searchButton = DanmuUi.primaryButton(activity, t, "搜索");
            Button actionButton = DanmuUi.primaryButton(activity, t, "推送");
            searchRow.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(activity, 44), 1f));
            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44));
            searchLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            searchRow.addView(searchButton, searchLp);
            LinearLayout.LayoutParams pushLp = new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 44));
            pushLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            searchRow.addView(actionButton, pushLp);
            root.addView(searchRow, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            TextView pushInfoText = DanmuUi.chip(activity, t, "", true);
            pushInfoText.setVisibility(View.GONE);

            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int sheetTarget = (int) (screenH * (isCenter ? 0.88f : 0.90f));
            int searchChrome = dp(activity, 28 + 16 + 50 + 52 + 12 + 24);
            int contentHeightPx = clamp(sheetTarget - searchChrome, dp(activity, 260), dp(activity, 900));
            FrameLayout contentFrame = new FrameLayout(activity);
            LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, contentHeightPx);
            contentLp.setMargins(0, 0, 0, dp(activity, DanmuTheme.SPACE_3));
            root.addView(contentFrame, contentLp);

            FrameLayout promptHolder = new FrameLayout(activity);
            promptHolder.addView(DanmuUi.emptyState(activity, t, "输入剧名后开始搜索",
                "也可直接打开，会自动读取当前播放并预填"),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            contentFrame.addView(promptHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout resultsSection = new LinearLayout(activity);
            resultsSection.setOrientation(LinearLayout.VERTICAL);
            HorizontalScrollView platformFilterScroll = buildHorizontalChipScroll(activity);
            LinearLayout platformFilterRow = new LinearLayout(activity);
            platformFilterRow.setOrientation(LinearLayout.HORIZONTAL);
            platformFilterRow.setGravity(Gravity.CENTER_VERTICAL);
            platformFilterScroll.addView(platformFilterRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            resultsSection.addView(platformFilterScroll, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            ScrollView resultsScroll = buildSheetScroll(activity);
            LinearLayout resultsContainer = new LinearLayout(activity);
            resultsContainer.setOrientation(LinearLayout.VERTICAL);
            resultsScroll.addView(resultsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            resultsSection.addView(resultsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            contentFrame.addView(resultsSection, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout episodeSection = new LinearLayout(activity);
            episodeSection.setOrientation(LinearLayout.VERTICAL);

            final boolean[] showTitles = new boolean[]{host.readEpisodeShowTitles(activity)};
            final ArrayList<TextView> episodeItemViews = new ArrayList<>();
            final int[] gridMetrics = new int[]{9, 40};

            LinearLayout episodeToolbar = new LinearLayout(activity);
            episodeToolbar.setOrientation(LinearLayout.HORIZONTAL);
            episodeToolbar.setGravity(Gravity.CENTER_VERTICAL);
            TextView episodeCountText = DanmuUi.text(activity, t, "", DanmuTheme.TEXT_BODY, t.textSecondary, true);
            episodeCountText.setSingleLine(true);
            episodeCountText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Button numberModeButton = DanmuUi.toggleChip(activity, t, "数字", !showTitles[0]);
            Button titleModeButton = DanmuUi.toggleChip(activity, t, "标题", showTitles[0]);
            Button episodeBackButton = DanmuUi.ghostButton(activity, t, "返回");
            episodeToolbar.addView(episodeCountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 32));
            episodeToolbar.addView(numberModeButton, numLp);
            LinearLayout.LayoutParams titLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 32));
            titLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            episodeToolbar.addView(titleModeButton, titLp);
            LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 32));
            backLp.setMargins(dp(activity, DanmuTheme.SPACE_2), 0, 0, 0);
            episodeToolbar.addView(episodeBackButton, backLp);
            episodeSection.addView(episodeToolbar, matchWrapWithBottom(activity, DanmuTheme.SPACE_2));

            ScrollView episodeScroll = buildSheetScroll(activity);
            GridLayout episodeGrid = new GridLayout(activity);
            episodeGrid.setColumnCount(9);
            episodeGrid.setUseDefaultMargins(false);
            episodeGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            ArrayList<CandidateHandle> compactHandles = new ArrayList<>();
            episodeScroll.addView(episodeGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            episodeSection.addView(episodeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            contentFrame.addView(episodeSection, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView statusText = new TextView(activity);
            statusText.setVisibility(View.GONE);

            final ArrayList<String> animeLabels = new ArrayList<>();
            final ArrayList<CandidateHandle> animeHandles = new ArrayList<>();
            final ArrayList<SourceFilter> sourceFilters = new ArrayList<>();
            final String[] selectedSource = new String[]{""};
            final boolean[] searching = new boolean[]{false};
            final int[] mode = new int[]{MODE_ANIME};
            final String[] currentEpisode = new String[]{""};
            final int[] selectedEpisodeIndex = new int[]{0};

            final Runnable refreshEpisodeHeader = () -> {
                if (compactHandles.isEmpty()) {
                    episodeCountText.setText("");
                    return;
                }
                int sel = clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
                String dramaTitle = currentDramaTitle[0];
                String count = compactHandles.size() + " 集";
                String display = dramaTitle.isEmpty() ? "共 " + count : dramaTitle + "  共" + count;
                episodeCountText.setText(display);
            };

            final Runnable renderEpisodeGrid = () -> {
                renderEpisodeGrid(activity, t, episodeGrid, compactHandles, episodeItemViews, selectedEpisodeIndex,
                    showTitles[0], shellPort[0], statusText, actionButton, pushInfoText, gridMetrics, refreshEpisodeHeader);
                refreshEpisodeHeader.run();
            };

            renderContent[0] = () -> {
                reachable[0] = Math.max(reachable[0], stage[0]);
                if (!animeLabels.isEmpty()) reachable[0] = Math.max(reachable[0], STAGE_DRAMA);
                if (!compactHandles.isEmpty()) reachable[0] = Math.max(reachable[0], STAGE_EPISODE);
                headerTitleText.setText(headerStageTitle(stage[0]));
                pushSummaryText.setText(host.formatPushTimeChip());
                promptHolder.setVisibility(stage[0] == STAGE_SEARCH ? View.VISIBLE : View.GONE);
                resultsSection.setVisibility(stage[0] == STAGE_DRAMA ? View.VISIBLE : View.GONE);
                episodeSection.setVisibility(stage[0] == STAGE_EPISODE ? View.VISIBLE : View.GONE);
                actionButton.setText("推送");
            };

            applyStageStatus[0] = () -> {
                if (stage[0] == STAGE_EPISODE) {
                    statusText.setText(episodeMessage[0]);
                } else {
                    statusText.setText(searchMessage[0]);
                }
            };

            View.OnClickListener modeToggle = v -> {
                boolean wantTitles = v == titleModeButton;
                if (wantTitles == showTitles[0]) return;
                showTitles[0] = wantTitles;
                host.saveEpisodeShowTitles(activity, wantTitles);
                DanmuUi.styleToggleChip(activity, t, numberModeButton, !wantTitles);
                DanmuUi.styleToggleChip(activity, t, titleModeButton, wantTitles);
                renderEpisodeGrid.run();
                scrollEpisodeGridToIndex(activity, episodeScroll, selectedEpisodeIndex[0], gridMetrics[0], gridMetrics[1]);
            };
            numberModeButton.setOnClickListener(modeToggle);
            titleModeButton.setOnClickListener(modeToggle);
            episodeBackButton.setOnClickListener(v -> {
                stage[0] = STAGE_DRAMA;
                renderContent[0].run();
                applyStageStatus[0].run();
            });

            final Runnable[] renderDramaList = new Runnable[1];
            renderDramaList[0] = () -> {
                resultsContainer.removeAllViews();
                renderPlatformFilters(activity, t, platformFilterRow, sourceFilters, selectedSource[0], source -> {
                    selectedSource[0] = source == null ? "" : source;
                    renderDramaList[0].run();
                    platformFilterScroll.post(() -> {
                        if (selectedSource[0].isEmpty()) platformFilterScroll.smoothScrollTo(0, 0);
                    });
                });
                platformFilterScroll.setVisibility(sourceFilters.isEmpty() ? View.GONE : View.VISIBLE);
                if (searching[0]) {
                    resultsContainer.addView(DanmuUi.emptyState(activity, t, "搜索中…", "正在向弹幕核心查询，请稍候"),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                if (animeLabels.isEmpty()) {
                    resultsContainer.addView(DanmuUi.emptyState(activity, t, "无剧名结果", "换个关键词再搜一次"),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return;
                }
                int visibleIndex = 0;
                for (int i = 0; i < animeLabels.size(); i++) {
                    final CandidateHandle candidate = i < animeHandles.size() ? animeHandles.get(i) : null;
                    if (candidate != null && !selectedSource[0].isEmpty() && !selectedSource[0].equals(candidate.source)) {
                        continue;
                    }
                    visibleIndex++;
                    String[] parts = splitDramaLabel(animeLabels.get(i));
                    LinearLayout row = DanmuUi.listRow(activity, t, String.valueOf(visibleIndex), parts[0], parts[1]);
                    row.setOnClickListener(v -> {
                        if (candidate == null) return;
                        currentDramaTitle[0] = parts[0];
                        loadAnimeDetailIntoSheet(activity, candidate, currentEpisode[0], episodeScroll,
                            compactHandles, selectedEpisodeIndex, mode, searchButton, statusText, pushInfoText,
                            renderEpisodeGrid, gridMetrics, stage, renderContent[0], episodeMessage);
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(activity, DanmuTheme.SPACE_2);
                    resultsContainer.addView(row, lp);
                }
                if (visibleIndex == 0) {
                    selectedSource[0] = "";
                    renderDramaList[0].run();
                    return;
                }
                resultsScroll.post(() -> resultsScroll.scrollTo(0, 0));
            };

            if (isCenter) {
                root.setBackground(t.roundRect(t.sheetBg, DanmuTheme.RADIUS_SHEET, activity));
            } else {
                root.setBackground(t.topRoundedSheet(activity));
            }

            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .create();
            closeButton.setOnClickListener(v -> dialog.dismiss());

            final Runnable searchAction = () -> {
                String keyword = keywordInput.getText() == null ? "" : keywordInput.getText().toString().trim();
                if (keyword.isEmpty()) {
                    Toast.makeText(activity, "先输入剧名", Toast.LENGTH_SHORT).show();
                    return;
                }
                mode[0] = MODE_ANIME;
                compactHandles.clear();
                episodeItemViews.clear();
                selectedEpisodeIndex[0] = 0;
                episodeGrid.removeAllViews();
                animeLabels.clear();
                animeHandles.clear();
                sourceFilters.clear();
                selectedSource[0] = "";
                stage[0] = STAGE_DRAMA;
                searching[0] = true;
                searchMessage[0] = "搜索中…";
                statusText.setText("搜索中…");
                searchButton.setEnabled(false);
                renderDramaList[0].run();
                renderContent[0].run();
                new Thread(() -> {
                    BridgeResult result = host.queryBridgeAnimeSearch(activity, keyword);
                    activity.runOnUiThread(() -> {
                        searching[0] = false;
                        searchButton.setEnabled(true);
                        searchMessage[0] = result.message;
                        if (stage[0] != STAGE_EPISODE) statusText.setText(result.message);
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
                        renderDramaList[0].run();
                        renderContent[0].run();
                    });
                }, "DanmuSearchAnime").start();
            };

            searchButton.setOnClickListener(v -> searchAction.run());
            actionButton.setOnClickListener(v -> {
                if (mode[0] == MODE_EPISODE && !compactHandles.isEmpty()) {
                    int index = clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
                    host.pushCandidate(activity, compactHandles.get(index), shellPort[0], statusText, pushInfoText);
                } else {
                    host.autoPushCurrent(activity, shellPort[0], statusText, pushInfoText);
                }
            });

            dialog.setOnShowListener(d -> {
                Window window = dialog.getWindow();
                if (window != null) {
                    int width = activity.getResources().getDisplayMetrics().widthPixels;
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setGravity(isCenter ? Gravity.CENTER : Gravity.BOTTOM);
                    window.setLayout((int) (width * 0.82f), ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                host.markPushHistoryViewed();
                renderContent[0].run();
                pushSummaryText.setText(host.formatPushTimeChip());
                updateNotifyDot.run();
                pushInfoText.setText(host.formatLastPushInfo(activity));
                new Thread(() -> {
                    ShellMedia media = host.readShellMedia(shellPort[0]);
                    activity.runOnUiThread(() -> {
                        pushSummaryText.setText(host.formatPushTimeChip());
                        updateNotifyDot.run();
                        pushInfoText.setText(host.formatLastPushInfo(activity));
                        if (media != null) {
                            shellPort[0] = media.port;
                            currentEpisode[0] = media.displayEpisode();
                            if (keywordInput.getText() == null || keywordInput.getText().toString().trim().isEmpty()) {
                                String filled = normalizeDisplayTitle(media.title).isEmpty() ? media.title : normalizeDisplayTitle(media.title);
                                keywordInput.setText(filled);
                                keywordInput.setSelection(keywordInput.getText().length());
                            }
                            if (!media.title.isEmpty()) searchAction.run();
                        }
                    });
                }, "DanmuReadMedia").start();
            });
            dialog.show();
        } catch (Throwable throwable) {
            Toast.makeText(activity, "打开 APP弹幕 搜索失败：" + throwable.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            host.logError("show manual search dialog failed", throwable);
        }
    }

    private ScrollView buildSheetScroll(Activity activity) {
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

    private String[] splitDramaLabel(String label) {
        String value = label == null ? "" : label.trim();
        int sep = value.indexOf(" · ");
        if (sep < 0) return new String[]{value, ""};
        return new String[]{value.substring(0, sep), value.substring(sep + 3)};
    }

    private void loadAnimeDetailIntoSheet(
        Activity activity, CandidateHandle anime, String episodeHint, ScrollView episodeScroll,
        ArrayList<CandidateHandle> compactHandles, final int[] selectedEpisodeIndex, int[] mode,
        Button searchButton, TextView statusText, TextView pushInfoText, Runnable renderEpisodeGrid,
        int[] gridMetrics, final int[] stage, Runnable renderContent, String[] episodeMessage
    ) {
        statusText.setText("正在加载剧集：" + anime.label);
        searchButton.setEnabled(false);
        new Thread(() -> {
            BridgeResult result = host.loadAnimeDetailDirect(anime.handle, episodeHint);
            activity.runOnUiThread(() -> {
                searchButton.setEnabled(true);
                statusText.setText(result.message);
                if (result.ok && !result.candidates.isEmpty()) {
                    episodeMessage[0] = result.message;
                    mode[0] = MODE_EPISODE;
                    compactHandles.clear();
                    compactHandles.addAll(result.candidates);
                    int targetIndex = clamp(result.selectedIndex, 0, result.candidates.size() - 1);
                    selectedEpisodeIndex[0] = targetIndex;
                    pushInfoText.setText(host.formatLastPushInfo(activity));
                    renderEpisodeGrid.run();
                    stage[0] = STAGE_EPISODE;
                    renderContent.run();
                    scrollEpisodeGridToIndex(activity, episodeScroll, targetIndex, gridMetrics[0], gridMetrics[1]);
                } else {
                    mode[0] = MODE_ANIME;
                    selectedEpisodeIndex[0] = 0;
                    compactHandles.clear();
                    renderEpisodeGrid.run();
                    renderContent.run();
                }
            });
        }, "DanmuAnimeDetail").start();
    }

    private LinearLayout.LayoutParams matchWrapWithBottom(Activity activity, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(activity, bottomDp));
        return lp;
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

    private TextView platformChip(Activity activity, DanmuTheme t, String label, boolean selected) {
        TextView chip = new TextView(activity);
        chip.setText(label == null ? "" : label);
        chip.setTextSize(DanmuTheme.TEXT_CAPTION);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setIncludeFontPadding(false);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(selected ? t.accentText : t.textSecondary);
        int fill = selected ? t.accent : t.surfaceAlt;
        int stroke = selected ? t.accentStrong : t.stroke;
        chip.setBackground(t.roundRect(fill, DanmuTheme.RADIUS_PILL, stroke, selected ? 2 : 1, activity));
        chip.setPadding(dp(activity, DanmuTheme.SPACE_3), 0, dp(activity, DanmuTheme.SPACE_3), 0);
        return chip;
    }

    private void renderPlatformFilters(Activity activity, DanmuTheme t, LinearLayout chipsRow,
                                       List<SourceFilter> filters, String selectedSource,
                                       FilterSelectListener listener) {
        chipsRow.removeAllViews();
        TextView allChip = platformChip(activity, t, "全部 " + countAllFilters(filters), selectedSource == null || selectedSource.trim().isEmpty());
        allChip.setOnClickListener(v -> {
            if (listener != null) listener.onSelect("");
        });
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30));
        allLp.rightMargin = dp(activity, DanmuTheme.SPACE_2);
        chipsRow.addView(allChip, allLp);

        for (SourceFilter filter : filters) {
            if (filter == null || filter.source.isEmpty() || filter.count <= 0) continue;
            boolean selected = filter.source.equals(selectedSource == null ? "" : selectedSource.trim());
            TextView chip = platformChip(activity, t, filter.displayName() + " " + filter.count, selected);
            chip.setOnClickListener(v -> {
                if (listener != null) listener.onSelect(filter.source);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 30));
            lp.rightMargin = dp(activity, DanmuTheme.SPACE_2);
            chipsRow.addView(chip, lp);
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

    private void renderEpisodeGrid(Activity activity, DanmuTheme t, GridLayout episodeGrid, ArrayList<CandidateHandle> compactHandles,
                                   ArrayList<TextView> itemViews, final int[] selectedEpisodeIndex, boolean showTitles,
                                   int shellPort, TextView statusText, Button actionButton, TextView pushInfoText, int[] gridMetrics,
                                   Runnable onSelectionChanged) {
        episodeGrid.removeAllViews();
        itemViews.clear();
        if (compactHandles.isEmpty()) {
            gridMetrics[0] = 1;
            gridMetrics[1] = 40;
            return;
        }
        int columns = showTitles ? 1 : computeEpisodeColumns(activity);
        int rowHeightDp = showTitles ? 44 : 38;
        gridMetrics[0] = columns;
        gridMetrics[1] = rowHeightDp;
        episodeGrid.setColumnCount(columns);
        episodeGrid.setMinimumWidth(0);
        episodeGrid.setClipToPadding(false);
        int marginPx = dp(activity, DanmuTheme.SPACE_1);
        int selected = clamp(selectedEpisodeIndex[0], 0, compactHandles.size() - 1);
        selectedEpisodeIndex[0] = selected;
        for (int i = 0; i < compactHandles.size(); i++) {
            final CandidateHandle candidate = compactHandles.get(i);
            final int index = i;
            TextView cell = DanmuUi.episodeCell(activity, t);
            DanmuUi.styleEpisodeCell(activity, t, cell, episodeCellLabel(candidate, index, showTitles), index == selected, showTitles);
            cell.setOnClickListener(v -> {
                int prev = selectedEpisodeIndex[0];
                if (prev == index) {
                    host.pushCandidate(activity, candidate, shellPort, statusText, pushInfoText);
                    return;
                }
                selectedEpisodeIndex[0] = index;
                if (prev >= 0 && prev < itemViews.size()) {
                    DanmuUi.styleEpisodeCell(activity, t, itemViews.get(prev), episodeCellLabel(compactHandles.get(prev), prev, showTitles), false, showTitles);
                }
                DanmuUi.styleEpisodeCell(activity, t, cell, episodeCellLabel(candidate, index, showTitles), true, showTitles);
                if (actionButton != null) actionButton.setText("推送");
                statusText.setText("已选中第" + shortEpisodeLabel(candidate, index) + "集，再点一次或按推送执行");
                if (onSelectionChanged != null) onSelectionChanged.run();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(activity, showTitles ? 40 : 36);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            episodeGrid.addView(cell, lp);
            itemViews.add(cell);
        }
        ViewGroup.LayoutParams existing = episodeGrid.getLayoutParams();
        if (existing != null) {
            existing.width = ViewGroup.LayoutParams.MATCH_PARENT;
            existing.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            episodeGrid.setLayoutParams(existing);
        }
    }

    private String episodeCellLabel(CandidateHandle candidate, int index, boolean showTitles) {
        if (showTitles) {
            return buildEpisodeTitleLabel(candidate, index);
        }
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

    private int computeEpisodeColumns(Activity activity) {
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = (int) (screenWidth * 0.86f) - dp(activity, 36);
        int perItem = dp(activity, 52);
        int columns = Math.max(1, dialogWidth / perItem);
        return clamp(columns, 5, 9);
    }

    private void scrollEpisodeGridToIndex(Activity activity, ScrollView episodeScroll, int index, int columns, int rowHeightDp) {
        episodeScroll.post(() -> {
            int safeColumns = Math.max(1, columns);
            int row = Math.max(0, index) / safeColumns;
            int y = Math.max(0, row * dp(activity, rowHeightDp) - dp(activity, 24));
            episodeScroll.smoothScrollTo(0, y);
        });
    }

    private String shortEpisodeLabel(CandidateHandle candidate, int index) {
        int number = extractEpisodeNumber(candidate == null ? "" : candidate.label);
        return number > 0 ? String.valueOf(number) : String.valueOf(index + 1);
    }

    private String headerStageTitle(int stage) {
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

    private int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
