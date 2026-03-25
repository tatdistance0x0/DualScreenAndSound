package com.example.dualscreenandsound;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.Locale;

public class MyPresentation extends Presentation {
    private static final long PROGRESS_INTERVAL_MS = 400L;
    private static final long OVERLAY_AUTO_HIDE_MS = 3000L;
    private static final long OVERLAY_FADE_MS = 180L;
    private static final long CENTER_ICON_HIDE_MS = 650L;
    private static final int OVERLAY_PANEL_EDGE_DP = 8;
    private static final int OVERLAY_PANEL_GAP_DP = 8;
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    public MediaPlayer mediaPlayer;
    public SurfaceView surfaceView;
    public Surface surface;
    public boolean isAudioDeviceSet = false;
    public boolean isVideoPlaying = false;
    public long currentPosition = 0;
    public ActivityResultLauncher<Intent> selectFileLauncher;
    public AudioDeviceInfo selectedDevice;

    private SurfaceHolder surfaceHolder;
    private boolean primingOutput = false;

    private View tapLayer;
    private View overlayControls;
    private View speedPanel;
    private View volumePanel;
    private View settingsPanel;

    private ImageButton btnOverlayPlayPause;
    private ImageButton btnOverlaySettings;
    private ImageButton btnOverlayVolumeEntry;
    private TextView btnOverlaySpeedEntry;
    private TextView tvSpeed050;
    private TextView tvSpeed075;
    private TextView tvSpeed100;
    private TextView tvSpeed125;
    private TextView tvSpeed150;
    private TextView tvSpeed200;
    private SeekBar seekProgress;
    private SeekBar seekVolume;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvVolumeValue;
    private Switch switchLoop;
    private Switch switchAutoPlay;
    private ImageView centerPlayState;

    private boolean progressUserSeeking = false;
    private float playerVolume = 1.0f;
    private int speedPresetIndex = 2;
    private boolean loopingEnabled = false;
    private boolean autoPlayEnabled = false;
    private boolean overlayVisible = true;

    private GestureDetector gestureDetector;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateOverlayUi();
            uiHandler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };
    private final Runnable hideOverlayRunnable = () -> hideOverlayControlsAnimated();
    private final Runnable hideCenterIconRunnable = () -> {
        if (centerPlayState != null) {
            centerPlayState.animate().cancel();
            centerPlayState.setVisibility(View.GONE);
            centerPlayState.setAlpha(0f);
        }
    };

    public MyPresentation(Context context, Display display) {
        super(context, display);
        setContentView(R.layout.presentation);
        surfaceView = findViewById(R.id.surface_view);
        surfaceHolder = surfaceView.getHolder();
        surface = surfaceHolder.getSurface();

        bindOverlayViews();
        setupOverlayControls();
        uiHandler.post(progressUpdater);

        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surface = holder.getSurface();
                Log.d("MediaPlayer", "MyPresentation surfaceCreated. Attempting to attach/init MediaPlayer.");
                if (mediaPlayer == null && MainActivity.presentionselectedUri != null) {
                    initMediaPlayer(MainActivity.presentionselectedUri, holder.getSurface());
                } else if (mediaPlayer != null) {
                    mediaPlayer.setSurface(holder.getSurface());
                    if (!MainActivity.isSwitchingToNewVideo) {
                        restorePlaybackState();
                    }
                    applyOverlayPlaybackSettings();
                }
                updateOverlayUi();
                showOverlayControls(true);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d("MediaPlayer", "MyPresentation surfaceChanged");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("MediaPlayer", "MyPresentation surfaceDestroyed. Saving playback state and detaching MediaPlayer.");
                savePlaybackState();
                if (mediaPlayer != null) {
                    mediaPlayer.setSurface(null);
                    Log.d("MediaPlayer", "MyPresentation MediaPlayer Surface 已分离，player 未释放。");
                }
            }
        });
    }

    private void bindOverlayViews() {
        tapLayer = findViewById(R.id.view_presentation_tap_layer);
        overlayControls = findViewById(R.id.layout_presentation_overlay_controls);
        speedPanel = findViewById(R.id.layout_presentation_overlay_speed_panel);
        volumePanel = findViewById(R.id.layout_presentation_overlay_volume_panel);
        settingsPanel = findViewById(R.id.layout_presentation_overlay_setting_panel);

        btnOverlayPlayPause = findViewById(R.id.btn_presentation_overlay_play_pause);
        btnOverlaySettings = findViewById(R.id.btn_presentation_overlay_settings);
        btnOverlayVolumeEntry = findViewById(R.id.btn_presentation_overlay_volume_entry);
        btnOverlaySpeedEntry = findViewById(R.id.btn_presentation_overlay_speed_entry);
        tvSpeed050 = findViewById(R.id.tv_presentation_speed_050);
        tvSpeed075 = findViewById(R.id.tv_presentation_speed_075);
        tvSpeed100 = findViewById(R.id.tv_presentation_speed_100);
        tvSpeed125 = findViewById(R.id.tv_presentation_speed_125);
        tvSpeed150 = findViewById(R.id.tv_presentation_speed_150);
        tvSpeed200 = findViewById(R.id.tv_presentation_speed_200);

        seekProgress = findViewById(R.id.seek_presentation_progress);
        seekVolume = findViewById(R.id.seek_presentation_volume);
        tvCurrentTime = findViewById(R.id.tv_presentation_current_time);
        tvTotalTime = findViewById(R.id.tv_presentation_total_time);
        tvVolumeValue = findViewById(R.id.tv_presentation_volume_value);
        switchLoop = findViewById(R.id.switch_presentation_overlay_loop);
        switchAutoPlay = findViewById(R.id.switch_presentation_overlay_autoplay);
        centerPlayState = findViewById(R.id.iv_presentation_center_play_state);
    }

    private void setupOverlayControls() {
        setupTapGesture();

        if (seekProgress != null) {
            seekProgress.setMax(1000);
            seekProgress.setProgress(0);
            seekProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser || mediaPlayer == null) return;
                    int durationMs = safeGetDuration(mediaPlayer);
                    if (durationMs <= 0) return;
                    int targetMs = (int) ((progress / 1000f) * durationMs);
                    if (tvCurrentTime != null) {
                        tvCurrentTime.setText(formatTimeMs(targetMs));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    progressUserSeeking = true;
                    showOverlayControls(false);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    progressUserSeeking = false;
                    if (mediaPlayer == null) return;
                    int durationMs = safeGetDuration(mediaPlayer);
                    if (durationMs <= 0) return;
                    int targetMs = (int) ((seekBar.getProgress() / 1000f) * durationMs);
                    safeSeekTo(targetMs);
                    updateOverlayUi();
                    showOverlayControls(true);
                }
            });
        }

        if (seekVolume != null) {
            seekVolume.setMax(100);
            seekVolume.setProgress((int) (playerVolume * 100));
            seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    playerVolume = clampVolume(progress / 100f);
                    applyOverlayPlaybackSettings();
                    showOverlayControls(false);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    showOverlayControls(false);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    showOverlayControls(true);
                }
            });
        }

        if (btnOverlayPlayPause != null) {
            btnOverlayPlayPause.setOnClickListener(v -> togglePlayback(false));
        }

        if (btnOverlaySpeedEntry != null) {
            btnOverlaySpeedEntry.setOnClickListener(v -> toggleExclusivePanel(speedPanel, btnOverlaySpeedEntry));
        }
        if (btnOverlayVolumeEntry != null) {
            btnOverlayVolumeEntry.setOnClickListener(v -> toggleExclusivePanel(volumePanel, btnOverlayVolumeEntry));
        }
        if (btnOverlaySettings != null) {
            btnOverlaySettings.setOnClickListener(v -> toggleExclusivePanel(settingsPanel, btnOverlaySettings));
        }

        bindSpeedItem(tvSpeed200, 5);
        bindSpeedItem(tvSpeed150, 4);
        bindSpeedItem(tvSpeed125, 3);
        bindSpeedItem(tvSpeed100, 2);
        bindSpeedItem(tvSpeed075, 1);
        bindSpeedItem(tvSpeed050, 0);

        if (switchLoop != null) {
            switchLoop.setChecked(loopingEnabled);
            switchLoop.setOnCheckedChangeListener((buttonView, isChecked) -> {
                loopingEnabled = isChecked;
                applyOverlayPlaybackSettings();
                showOverlayControls(true);
            });
        }

        if (switchAutoPlay != null) {
            switchAutoPlay.setChecked(autoPlayEnabled);
            switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
                autoPlayEnabled = isChecked;
                showOverlayControls(true);
            });
        }

        if (speedPanel != null) speedPanel.setVisibility(View.GONE);
        if (volumePanel != null) volumePanel.setVisibility(View.GONE);
        if (settingsPanel != null) settingsPanel.setVisibility(View.GONE);

        updateOverlayUi();
        showOverlayControls(true);
    }

    private void setupTapGesture() {
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isTapInCenterZone(tapLayer, e)) {
                    togglePlayback(true);
                    return true;
                }
                hideAllPanels();
                if (overlayVisible) {
                    hideOverlayControlsAnimated();
                } else {
                    showOverlayControls(true);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                togglePlayback(true);
                return true;
            }
        });

        if (tapLayer != null) {
            tapLayer.setClickable(true);
            tapLayer.setOnTouchListener((v, event) -> gestureDetector != null && gestureDetector.onTouchEvent(event));
        }
    }

    private boolean isTapInCenterZone(View target, MotionEvent event) {
        if (target == null || event == null) return false;
        float width = target.getWidth();
        float height = target.getHeight();
        if (width <= 0f || height <= 0f) return false;
        float left = width * 0.3f;
        float right = width * 0.7f;
        float top = height * 0.25f;
        float bottom = height * 0.75f;
        return event.getX() >= left && event.getX() <= right
                && event.getY() >= top && event.getY() <= bottom;
    }

    private void bindSpeedItem(TextView view, int index) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            speedPresetIndex = index;
            applyOverlayPlaybackSettings();
            showOverlayControls(true);
        });
    }

    private void togglePlayback(boolean showCenterFeedback) {
        if (mediaPlayer == null) return;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.start();
            }
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "副屏播放切换失败", e);
        }
        updateOverlayUi();
        if (showCenterFeedback) {
            showCenterPlayState(isPlayerPlayingSafely());
        }
        showOverlayControls(true);
    }

    private void showCenterPlayState(boolean playingAfterAction) {
        if (centerPlayState == null) return;
        uiHandler.removeCallbacks(hideCenterIconRunnable);
        centerPlayState.animate().cancel();
        centerPlayState.setImageResource(playingAfterAction ? R.drawable.ic_play_triangle : R.drawable.ic_pause_simple);
        centerPlayState.setAlpha(0f);
        centerPlayState.setVisibility(View.VISIBLE);
        centerPlayState.animate().alpha(0.96f).setDuration(110L).start();
        uiHandler.postDelayed(hideCenterIconRunnable, CENTER_ICON_HIDE_MS);
    }

    private void toggleExclusivePanel(View targetPanel, View anchor) {
        if (targetPanel == null || anchor == null) return;
        boolean shouldShow = targetPanel.getVisibility() != View.VISIBLE;
        hideAllPanels();
        if (shouldShow) {
            targetPanel.setAlpha(0f);
            targetPanel.setVisibility(View.VISIBLE);
            positionOverlayPanelAboveAnchor(targetPanel, anchor);
            targetPanel.animate().alpha(1f).setDuration(130L).start();
            showOverlayControls(false);
        } else {
            showOverlayControls(true);
        }
    }

    private void positionOverlayPanelAboveAnchor(View panel, View anchor) {
        if (panel == null || anchor == null || tapLayer == null || overlayControls == null) return;
        panel.post(() -> {
            int parentWidth = tapLayer.getWidth();
            int panelWidth = panel.getWidth();
            if (parentWidth <= 0 || panelWidth <= 0) return;
            float centerX = anchor.getX() + (anchor.getWidth() / 2f);
            float targetX = centerX - (panelWidth / 2f);
            float minX = dpToPx(OVERLAY_PANEL_EDGE_DP);
            float maxX = Math.max(minX, parentWidth - panelWidth - dpToPx(OVERLAY_PANEL_EDGE_DP));
            panel.setX(Math.max(minX, Math.min(maxX, targetX)));

            float targetY = overlayControls.getY() - panel.getHeight() - dpToPx(OVERLAY_PANEL_GAP_DP);
            panel.setY(Math.max(dpToPx(OVERLAY_PANEL_EDGE_DP), targetY));
        });
    }

    private void hideAllPanels() {
        hidePanel(speedPanel);
        hidePanel(volumePanel);
        hidePanel(settingsPanel);
    }

    private void hidePanel(View panel) {
        if (panel == null || panel.getVisibility() != View.VISIBLE) return;
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        panel.setAlpha(1f);
    }

    private boolean anyPanelVisible() {
        return isVisible(speedPanel) || isVisible(volumePanel) || isVisible(settingsPanel);
    }

    private boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    private void showOverlayControls(boolean autoHide) {
        if (overlayControls == null) return;
        overlayVisible = true;
        overlayControls.animate().cancel();
        if (overlayControls.getVisibility() != View.VISIBLE) {
            overlayControls.setAlpha(0f);
            overlayControls.setVisibility(View.VISIBLE);
        }
        overlayControls.animate().alpha(1f).setDuration(120L).start();
        if (autoHide) {
            scheduleOverlayAutoHideIfNeeded();
        } else {
            cancelOverlayAutoHide();
        }
    }

    private void hideOverlayControlsAnimated() {
        if (overlayControls == null) return;
        if (!overlayVisible) return;
        hideAllPanels();
        overlayVisible = false;
        cancelOverlayAutoHide();
        overlayControls.animate().cancel();
        overlayControls.animate().alpha(0f).setDuration(OVERLAY_FADE_MS).withEndAction(() -> {
            if (!overlayVisible && overlayControls != null) {
                overlayControls.setVisibility(View.GONE);
                overlayControls.setAlpha(1f);
            }
        }).start();
    }

    private void scheduleOverlayAutoHideIfNeeded() {
        cancelOverlayAutoHide();
        if (!overlayVisible) return;
        if (anyPanelVisible()) return;
        if (!isPlayerPlayingSafely()) return;
        uiHandler.postDelayed(hideOverlayRunnable, OVERLAY_AUTO_HIDE_MS);
    }

    private void cancelOverlayAutoHide() {
        uiHandler.removeCallbacks(hideOverlayRunnable);
    }

    public void initMediaPlayer(Uri fileUri, Surface targetSurface) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                Log.d("MediaPlayer", "Presention MediaPlayer 被初始化");
            } else {
                Log.d("MediaPlayer", "Presention MediaPlayer 重置");
                mediaPlayer.reset();
            }
            Log.d("MediaPlayer", "副屏 URI: " + fileUri);
            mediaPlayer.setDataSource(getContext(), fileUri);
            mediaPlayer.setSurface(targetSurface);
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("MediaPlayer", "副屏播放器准备完毕");
                restorePlaybackState();
                applyOverlayPlaybackSettings();
                if (autoPlayEnabled && isAudioDeviceSet && !isPlayerPlayingSafely()) {
                    try {
                        mp.start();
                    } catch (IllegalStateException e) {
                        Log.w("MediaPlayer", "副屏自动开播失败", e);
                    }
                }
                updateOverlayUi();
                showOverlayControls(true);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                updateOverlayUi();
                showOverlayControls(false);
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e("MediaPlayer", "副屏初始化失败", e);
        }
    }

    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            Log.d("MediaPlayer", "presentation.MediaPlayer 被释放");
        }
        updateOverlayUi();
    }

    public void savePlaybackState() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            isVideoPlaying = true;
            currentPosition = mediaPlayer.getCurrentPosition();
        } else {
            isVideoPlaying = false;
        }
    }

    private void restorePlaybackState() {
        if (mediaPlayer != null) {
            try {
                if (MainActivity.isSwitchingToNewVideo) {
                    mediaPlayer.seekTo(0);
                } else {
                    mediaPlayer.seekTo((int) currentPosition);
                }
                if (isVideoPlaying && !MainActivity.isSwitchingToNewVideo) {
                    mediaPlayer.start();
                }
            } catch (IllegalStateException e) {
                Log.w("MediaPlayer", "副屏恢复状态失败", e);
            }
        }
    }

    public void setAudioDevice(AudioDeviceInfo targetDevice) {
        if (targetDevice == null || mediaPlayer == null) {
            Log.d("AudioDevice", "selectedDevice为空或播放器未初始化");
            return;
        }
        boolean success = mediaPlayer.setPreferredDevice(targetDevice);
        if (success) {
            isAudioDeviceSet = true;
            selectedDevice = targetDevice;
            Log.d("AudioDevice", "副屏已设置音频输出为: " + targetDevice.getType());
        } else {
            isAudioDeviceSet = false;
            Log.d("AudioDevice", "副屏设置设备失败");
        }
    }

    public void setLoopingEnabled(boolean enabled) {
        loopingEnabled = enabled;
        if (switchLoop != null && switchLoop.isChecked() != enabled) {
            switchLoop.setChecked(enabled);
        }
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setLooping(enabled);
            } catch (IllegalStateException e) {
                Log.w("MediaPlayer", "副屏循环设置失败", e);
            }
        }
    }

    public void setAutoPlayEnabled(boolean enabled) {
        autoPlayEnabled = enabled;
        if (switchAutoPlay != null && switchAutoPlay.isChecked() != enabled) {
            switchAutoPlay.setChecked(enabled);
        }
    }

    public void setPlayerVolume(float volume) {
        playerVolume = clampVolume(volume);
        if (seekVolume != null) {
            int target = (int) (playerVolume * 100);
            if (seekVolume.getProgress() != target) {
                seekVolume.setProgress(target);
            }
        }
        applyOverlayPlaybackSettings();
    }

    public void setSpeedPresetIndex(int index) {
        if (index < 0 || index >= SPEED_PRESETS.length) {
            return;
        }
        speedPresetIndex = index;
        applyOverlayPlaybackSettings();
    }

    public void applyOverlayPlaybackSettings() {
        if (mediaPlayer == null) {
            updateOverlayUi();
            return;
        }
        try {
            mediaPlayer.setVolume(playerVolume, playerVolume);
            mediaPlayer.setLooping(loopingEnabled);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams params = mediaPlayer.getPlaybackParams();
                if (params == null) params = new PlaybackParams();
                params.setSpeed(SPEED_PRESETS[speedPresetIndex]);
                params.setPitch(1.0f);
                mediaPlayer.setPlaybackParams(params);
            }
        } catch (Exception e) {
            Log.w("MediaPlayer", "副屏应用播放参数失败", e);
        }
        updateOverlayUi();
    }

    private void updateOverlayUi() {
        if (btnOverlayPlayPause != null) {
            boolean playing = isPlayerPlayingSafely();
            btnOverlayPlayPause.setImageResource(playing ? R.drawable.ic_pause_simple : R.drawable.ic_play_triangle);
            btnOverlayPlayPause.setContentDescription(playing ? "暂停" : "播放");
        }
        if (btnOverlaySpeedEntry != null) {
            btnOverlaySpeedEntry.setText("倍速");
        }
        if (tvVolumeValue != null) {
            tvVolumeValue.setText(String.valueOf((int) (playerVolume * 100)));
        }
        if (switchLoop != null && switchLoop.isChecked() != loopingEnabled) {
            switchLoop.setChecked(loopingEnabled);
        }
        if (switchAutoPlay != null && switchAutoPlay.isChecked() != autoPlayEnabled) {
            switchAutoPlay.setChecked(autoPlayEnabled);
        }
        updateSpeedButtonVisualState();

        if (seekProgress == null || tvCurrentTime == null || tvTotalTime == null) {
            scheduleOverlayAutoHideIfNeeded();
            return;
        }
        if (mediaPlayer == null) {
            if (!progressUserSeeking) {
                seekProgress.setProgress(0);
            }
            tvCurrentTime.setText("00:00");
            tvTotalTime.setText("00:00");
            cancelOverlayAutoHide();
            return;
        }
        int durationMs = safeGetDuration(mediaPlayer);
        int currentMs = safeGetCurrentPosition(mediaPlayer);
        if (!progressUserSeeking) {
            int progress = durationMs > 0 ? (int) ((currentMs * 1000f) / durationMs) : 0;
            seekProgress.setProgress(Math.max(0, Math.min(1000, progress)));
            tvCurrentTime.setText(formatTimeMs(currentMs));
        }
        tvTotalTime.setText(formatTimeMs(Math.max(durationMs, 0)));
        scheduleOverlayAutoHideIfNeeded();
    }

    private void updateSpeedButtonVisualState() {
        updateSpeedItemVisual(tvSpeed200, speedPresetIndex == 5);
        updateSpeedItemVisual(tvSpeed150, speedPresetIndex == 4);
        updateSpeedItemVisual(tvSpeed125, speedPresetIndex == 3);
        updateSpeedItemVisual(tvSpeed100, speedPresetIndex == 2);
        updateSpeedItemVisual(tvSpeed075, speedPresetIndex == 1);
        updateSpeedItemVisual(tvSpeed050, speedPresetIndex == 0);
    }

    private void updateSpeedItemVisual(TextView view, boolean selected) {
        if (view == null) return;
        view.setTextColor(ContextCompat.getColor(getContext(), selected ? R.color.brand_accent : R.color.white));
        view.setAlpha(selected ? 1f : 0.85f);
    }

    private boolean isPlayerPlayingSafely() {
        if (mediaPlayer == null) return false;
        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private int safeGetCurrentPosition(MediaPlayer player) {
        if (player == null) return 0;
        try {
            return player.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private int safeGetDuration(MediaPlayer player) {
        if (player == null) return 0;
        try {
            return player.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    private void safeSeekTo(int targetMs) {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.seekTo(targetMs);
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "副屏 seek 失败", e);
        }
    }

    private float clampVolume(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    private String formatTimeMs(int totalMs) {
        int safeMs = Math.max(totalMs, 0);
        int totalSeconds = safeMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public boolean isPrimingOutput() {
        return primingOutput;
    }

    public boolean primeOutputForSmoothStart() {
        if (mediaPlayer == null || primingOutput || mediaPlayer.isPlaying()) {
            return false;
        }
        primingOutput = true;
        try {
            int resumePosition = Math.max(0, (int) currentPosition);
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            mediaPlayer.pause();
            mediaPlayer.seekTo(resumePosition);
            return true;
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "副屏预热失败", e);
            return false;
        } finally {
            primingOutput = false;
            updateOverlayUi();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        uiHandler.removeCallbacks(progressUpdater);
        uiHandler.removeCallbacks(hideOverlayRunnable);
        uiHandler.removeCallbacks(hideCenterIconRunnable);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
