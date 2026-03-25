package com.example.dualscreenandsound;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.SyncParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private static final int SEEK_STEP_MS = 10_000;
    private static final long MAIN_PROGRESS_UPDATE_INTERVAL_MS = 400L;
    private static final long OVERLAY_AUTO_HIDE_MS = 3000L;
    private static final long OVERLAY_FADE_DURATION_MS = 180L;
    private static final long CENTER_PLAY_ICON_DURATION_MS = 650L;
    private static final int OVERLAY_REVEAL_OFFSET_DP = 24;
    private static final int OVERLAY_PANEL_EDGE_DP = 8;
    private static final int OVERLAY_PANEL_GAP_DP = 8;
    private static final float[] SPEED_PRESETS = new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private AudioManager mAudioManager;
    private MyPresentation presentation;  // 用来保存对 Presentation 的引用
    private boolean isVideoPlaying= false;  // 用来追踪视频是否正在播放
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private FrameLayout surfaceContainer;
    private ScrollView scrollView;
    private ImageButton btnFullScreen, btnOverlayPlayPause;
    private ImageButton btnOverlaySettings, btnOverlayVolumeEntry;
    private Button btnMainPlay, btnPresentationPlay, btnPresentationFile;
    private TextView btnOverlaySpeedEntry;
    private TextView tvOverlaySpeed050, tvOverlaySpeed075, tvOverlaySpeed100, tvOverlaySpeed125, tvOverlaySpeed150, tvOverlaySpeed200;
    private Button btnSubSeekBack10, btnSubSeekForward10, btnSubSpeed;
    private TextView tvStatusMain, tvStatusSub, tvStatusAudioCount;
    private TextView tvSummaryMainAudio, tvSummarySubAudio, tvSummaryDisplay;
    private TextView tvMainVolumeValue, tvSubVolumeValue;
    private TextView tvSubCurrentTime, tvSubTotalTime, tvOverlayCurrentTime, tvOverlayTotalTime;
    private TextView tvOverlayVolumeValue;
    private Switch switchOverlayLoop, switchOverlayAutoPlay;
    private ImageView ivCenterPlayState;
    private SeekBar seekMainVolume, seekSubVolume, seekSubProgress, seekOverlayProgress;
    private View mainTapLayer;
    private View layoutOverlayControls;
    private View layoutOverlaySpeedPanel;
    private View layoutOverlayVolumePanel;
    private View layoutOverlaySettingPanel;
    private View layoutSubControlHeader;
    private View layoutSubControlContent;
    private TextView tvSubControlToggle;
    private List<AudioDeviceInfo> OutputDevices;
    private AutoCompleteTextView mAudioDevicesSpinner1,mAudioDevicesSpinner2, displaySpinner;
    private String selectedFilePath,selectedPresentionFilePath,previousMainScreenFilePath, previousPresentationFilePath= "";  // 用于保存选择的文件路径
    private boolean isAudioDeviceSet = false;
    private ActivityResultLauncher<Intent> selectFileLauncher, selectFileLauncherCache;  // 声明 ActivityResultLauncher
    public static Uri presentionselectedUri;
    private Uri selectedUri,toggleSelectedUri;
    private AudioDeviceInfo selectedDevice ;
    public static AudioDeviceInfo selectedDeviceCache;
    private long currentPosition = 0;  // 用来保存当前播放的位置
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    public static boolean isSwitchingToNewVideo = false;
    private String filePath= null;
    private List<String> deviceNames = new ArrayList<>();
    private List<DisplayItem> displayItems = new ArrayList<>();
    // 使用一个 HashMap 来存储副屏 ID 和对应的 MyPresentation 实例
    private Map<Integer, MyPresentation> presentationMap = new HashMap<>();
    private DisplayManager displayManager ;
    private Display[] allDisplays;

    private int selectedDisplayId;
    private Object device = 0;

    private boolean isFullScreen = false;
    private Dialog fullScreenDialog;
    private SurfaceView fullScreenSurfaceView;
    private Surface fullScreenSurface;
    private boolean keepPlayingAcrossSurfaceSwitch = false;
    private SurfaceHolder mainSurfaceHolder;
    private SurfaceHolder fullScreenSurfaceHolder;
    private boolean pendingAttachToMainSurface = false;
    private ViewGroup controlsLayout;
    private final List<View> nonPlayerViews = new ArrayList<>();
    private int inlineOriginalSurfaceHeight = -1;
    private int inlineOriginalSurfaceTopMargin = 0;
    private int inlineOriginalSurfaceBottomMargin = 0;
    private boolean inlineOriginalPaddingCaptured = false;
    private int inlineOriginalPaddingLeft = 0;
    private int inlineOriginalPaddingTop = 0;
    private int inlineOriginalPaddingRight = 0;
    private int inlineOriginalPaddingBottom = 0;
    private int inlineOriginalControlsLayoutHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
    private boolean wasPlayingBeforeFullScreen = false;
    private static final long ROUTE_SWITCH_DEBOUNCE_MS = 220L;
    private static final long ROUTE_SWITCH_GLOBAL_INTERVAL_MS = 850L;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean systemStreamMutedByApp = false;
    private final Runnable restoreSystemStreamRunnable = this::restoreSystemUiSoundEffects;
    private AudioDeviceInfo pendingMainRouteDevice = null;
    private AudioDeviceInfo pendingPresentationRouteDevice = null;
    private long lastGlobalRouteApplyTs = 0L;
    private boolean routeSwitchInProgress = false;
    private long pendingMainRouteTs = 0L;
    private long pendingPresentationRouteTs = 0L;
    private final Runnable applyRouteSwitchQueueRunnable = this::drainRouteSwitchQueue;
    private boolean mainVideoRenderingStarted = false;
    private boolean mainSyncParamsUnsupported = false;
    private boolean subProgressUserSeeking = false;
    private boolean overlayProgressUserSeeking = false;
    private boolean overlayControlsVisible = true;
    private boolean subControlExpanded = true;
    private int currentSpeedPresetIndex = 2;
    private int currentSubSpeedPresetIndex = 2;
    private boolean loopModeEnabled = false;
    private boolean autoPlayEnabled = false;
    private float mainPlayerVolume = 1.0f;
    private float presentationPlayerVolume = 1.0f;
    private GestureDetector mainTapGestureDetector;
    private final Runnable hideMainOverlayRunnable = this::hideMainOverlayControlsAnimated;
    private final Runnable hideCenterPlayStateRunnable = () -> {
        if (ivCenterPlayState != null) {
            ivCenterPlayState.animate().cancel();
            ivCenterPlayState.setVisibility(View.GONE);
            ivCenterPlayState.setAlpha(0f);
        }
    };
    private final Runnable mainProgressUpdater = new Runnable() {
        @Override
        public void run() {
            updateMainProgressUi();
            updateSubProgressUi();
            updateOverlayMainProgressUi();
            uiHandler.postDelayed(this, MAIN_PROGRESS_UPDATE_INTERVAL_MS);
        }
    };

    private class DisplayItem {
        String displayName;
        int displayId;

        DisplayItem(String displayName, int displayId) {
            this.displayName = displayName;
            this.displayId = displayId;
        }

        @Override
        public String toString() {
            return displayName; // Spinner 显示的是 displayName
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

        setupDisplayListener();

        surfaceView = findViewById(R.id.surfaceView);  // SurfaceView 用于显示视频
        surfaceContainer = findViewById(R.id.surfaceContainer);
        scrollView = findViewById(R.id.scrollView);
        btnFullScreen = findViewById(R.id.btn_fullscreen);
        btnMainPlay = findViewById(R.id.btn_displaymanager);
        controlsLayout = findViewById(R.id.controlsLayout);
        cacheNonPlayerViews();

        surfaceHolder = surfaceView.getHolder();
        // SurfaceHolder.Callback 用来处理 Surface 创建 and 销毁
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d("MediaPlayer", "准备初始化MediaPlayer或者presentation.MediaPlayer");
                surface = holder.getSurface();
                mainSurfaceHolder = holder;
                // Surface 创建后，可以初始化播放器
                if (selectedUri != null) {
                    boolean createdMainPlayer = false;
                    if (mediaPlayer == null) {
                        initMediaPlayer(selectedUri, holder.getSurface());
                        createdMainPlayer = true;
                    } else {
                        switchMainPlayerSurface(holder, isFullScreen ? "全屏主 Surface" : "主界面 Surface");
                        pendingAttachToMainSurface = false;
                    }
                    // Avoid re-routing audio on every surface recreation (fullscreen/layout),
                    // which may cause short audio interruption.
                    if (createdMainPlayer && selectedDevice != null) {
                        setAudioDevice(selectedDevice);
                    }
                } else if (mediaPlayer != null && pendingAttachToMainSurface) {
                    switchMainPlayerSurface(holder, "主界面 Surface(延迟恢复)");
                    pendingAttachToMainSurface = false;
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 处理Surface变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("MediaPlayer", "准备获取当前播放位置");
                surface = null;
                mainSurfaceHolder = null;
                keepPlayingAcrossSurfaceSwitch = mediaPlayer != null && mediaPlayer.isPlaying();
                // 在Surface销毁时保存当前播放状态
                savePlaybackState(); // 保存MediaPlayer播放状态
            }
        });


        /********************
         添加音频路由设备的下拉列表
        *********************/
        mAudioDevicesSpinner1 = findViewById(R.id.spinner_audio_devices);
        mAudioDevicesSpinner2 = findViewById(R.id.spinner_audio_presentation_devices);
        displaySpinner = findViewById(R.id.displaySpinner);
        tvStatusMain = findViewById(R.id.tv_status_main);
        tvStatusSub = findViewById(R.id.tv_status_sub);
        tvStatusAudioCount = findViewById(R.id.tv_status_audio_count);
        tvSummaryMainAudio = findViewById(R.id.tv_summary_main_audio);
        tvSummarySubAudio = findViewById(R.id.tv_summary_sub_audio);
        tvSummaryDisplay = findViewById(R.id.tv_summary_display);
        bindEnhancedPlayerViews();
        setupEnhancedPlayerControls();
        mAudioDevicesSpinner1.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                suppressSystemUiSoundEffectsTemporarily();
            }
            return false;
        });
        mAudioDevicesSpinner2.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                suppressSystemUiSoundEffectsTemporarily();
            }
            return false;
        });
        displaySpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                suppressSystemUiSoundEffectsTemporarily();
            }
            return false;
        });
        mAudioDevicesSpinner1.setOnClickListener(v -> mAudioDevicesSpinner1.showDropDown());
        mAudioDevicesSpinner2.setOnClickListener(v -> mAudioDevicesSpinner2.showDropDown());
        displaySpinner.setOnClickListener(v -> displaySpinner.showDropDown());
        refreshAudioDeviceList();
        updateDashboardStatus();

        /********************
         添加主副屏视频播放的按钮
         *********************/
        Button btn1 = btnMainPlay;
        btnPresentationPlay = findViewById(R.id.btn_presentation_displaymanager);
        Button btn2 = btnPresentationPlay;
        // btn1 的点击事件
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在 btn1 上调用通用的播放控制方法
                toggleMediaPlayer(mediaPlayer, isAudioDeviceSet, btn1);
            }
        });
        // btn2 的点击事件
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (presentation != null) {
                    Log.d("Display", "Initializing presentation for display ID: " + selectedDisplayId);
                    if (presentionselectedUri == null) {
                        Toast.makeText(getApplicationContext(), "请选择副屏媒体文件", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (presentation.mediaPlayer == null) {
                        Surface presentationSurface = getPresentationSurface();
                        if (presentationSurface != null && presentationSurface.isValid()) {
                            presentation.initMediaPlayer(presentionselectedUri, presentationSurface);
                            showPresentationInitFeedback(btn2);
                            applyPresentationPlayerSettings();
                            if (selectedDeviceCache != null) {
                                presentation.setAudioDevice(selectedDeviceCache);
                                maybePrimePresentationOutput("button-init");
                            }
                            Toast.makeText(getApplicationContext(), "副屏播放器初始化中，请再点击一次播放", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getApplicationContext(), "副屏画面未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    presentation.isAudioDeviceSet = (presentation.selectedDevice != null || selectedDeviceCache != null);
                    if (!presentation.isAudioDeviceSet) {
                        Toast.makeText(getApplicationContext(), "请选择副屏音频通道", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    togglePresentationPlayback(btn2);
                } else {
                    Toast.makeText(getApplicationContext(), "请连接副屏", Toast.LENGTH_SHORT).show();
                }
            }
        });

        /********************
         添加主副屏视频文件选择按钮
         *********************/
        // 创建一个 ActivityResultLauncher 来替代 startActivityForResult
        selectFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleFileResult(result, false));

        if (selectFileLauncherCache == null) {
            selectFileLauncherCache = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> handleFileResult(result, true));
            Log.d("AudioDevice", "注册presentation.selectFileLauncher");
        }

        // 按钮点击事件，打开文件选择器
        Button selectFileButton = findViewById(R.id.btn_selectfile);
        btnPresentationFile = findViewById(R.id.btn_presentation_selectfile);
        Button selectPrensentionFileButton = btnPresentationFile;
        selectFileButton.setOnClickListener(v -> handleFileSelection(selectFileLauncher, false));
        selectPrensentionFileButton.setOnClickListener(v ->{
                if(presentation != null){
                    handleFileSelection(selectFileLauncherCache, true);
                }else{
                    Toast.makeText(getApplicationContext(), "请连接副屏", Toast.LENGTH_SHORT).show();
                    Log.d("AudioDevice", "presentation为null");
                }
        });

        // 全屏逻辑：同一 Surface 原地全屏，避免切换 Surface 引发黑屏/ANR
        btnFullScreen.setOnClickListener(v -> {
            if (isFullScreen) {
                exitFullScreen();
            } else {
                enterFullScreen();
            }
        });

        // Disable UI click/haptic effects to avoid system touch-sound tracks
        // reconfiguring HDMI route during fullscreen toggle on some HALs.
        disableInteractionSoundEffects(
                btnMainPlay,
                btn2,
                selectFileButton,
                selectPrensentionFileButton,
                btnFullScreen,
                btnOverlaySpeedEntry,
                btnOverlayVolumeEntry,
                tvOverlaySpeed200,
                tvOverlaySpeed150,
                tvOverlaySpeed125,
                tvOverlaySpeed100,
                tvOverlaySpeed075,
                tvOverlaySpeed050,
                btnSubSeekBack10,
                btnSubSeekForward10,
                btnSubSpeed,
                layoutSubControlHeader,
                tvSubControlToggle,
                btnOverlayPlayPause,
                btnOverlaySettings,
                switchOverlayLoop,
                switchOverlayAutoPlay,
                mAudioDevicesSpinner1,
                mAudioDevicesSpinner2,
                displaySpinner
        );
        updateDashboardStatus();
    }

    private void showPresentationInitFeedback(Button targetButton) {
        if (targetButton == null) return;
        targetButton.setText("初始化中...");
        targetButton.setEnabled(false);
        uiHandler.postDelayed(() -> {
            updateDashboardStatus();
            if (targetButton.isEnabled()) {
                targetButton.setText("副屏播放");
            }
        }, 900L);
    }

    private void bindEnhancedPlayerViews() {
        seekMainVolume = findViewById(R.id.seek_overlay_volume);
        seekSubVolume = findViewById(R.id.seek_sub_volume);
        seekSubProgress = findViewById(R.id.seek_sub_progress);
        seekOverlayProgress = findViewById(R.id.seek_overlay_progress);
        tvMainVolumeValue = findViewById(R.id.tv_overlay_volume_value);
        tvSubVolumeValue = findViewById(R.id.tv_sub_volume_value);
        tvSubCurrentTime = findViewById(R.id.tv_sub_current_time);
        tvSubTotalTime = findViewById(R.id.tv_sub_total_time);
        tvOverlayCurrentTime = findViewById(R.id.tv_overlay_current_time);
        tvOverlayTotalTime = findViewById(R.id.tv_overlay_total_time);
        mainTapLayer = findViewById(R.id.view_main_tap_layer);
        layoutOverlayControls = findViewById(R.id.layout_overlay_controls);
        layoutOverlaySpeedPanel = findViewById(R.id.layout_overlay_speed_panel);
        layoutOverlayVolumePanel = findViewById(R.id.layout_overlay_volume_panel);
        layoutOverlaySettingPanel = findViewById(R.id.layout_overlay_setting_panel);
        layoutSubControlHeader = findViewById(R.id.layout_sub_control_header);
        layoutSubControlContent = findViewById(R.id.layout_sub_control_content);
        tvSubControlToggle = findViewById(R.id.tv_sub_control_toggle);
        switchOverlayLoop = findViewById(R.id.switch_overlay_loop);
        switchOverlayAutoPlay = findViewById(R.id.switch_overlay_autoplay);
        ivCenterPlayState = findViewById(R.id.iv_center_play_state);
        btnOverlaySpeedEntry = findViewById(R.id.btn_overlay_speed_entry);
        tvOverlaySpeed050 = findViewById(R.id.tv_overlay_speed_050);
        tvOverlaySpeed075 = findViewById(R.id.tv_overlay_speed_075);
        tvOverlaySpeed100 = findViewById(R.id.tv_overlay_speed_100);
        tvOverlaySpeed125 = findViewById(R.id.tv_overlay_speed_125);
        tvOverlaySpeed150 = findViewById(R.id.tv_overlay_speed_150);
        tvOverlaySpeed200 = findViewById(R.id.tv_overlay_speed_200);
        btnSubSeekBack10 = findViewById(R.id.btn_sub_seek_back_10);
        btnSubSeekForward10 = findViewById(R.id.btn_sub_seek_forward_10);
        btnSubSpeed = findViewById(R.id.btn_sub_speed);
        btnOverlayPlayPause = findViewById(R.id.btn_overlay_play_pause);
        btnOverlaySettings = findViewById(R.id.btn_overlay_settings);
        btnOverlayVolumeEntry = findViewById(R.id.btn_overlay_volume_entry);
    }

    private void setupEnhancedPlayerControls() {
        setupMainOverlayTapGesture();
        setupSubControlFold();
        bringMainOverlayViewsToFront();
        if (seekOverlayProgress != null) setupMainProgressSeekBar(seekOverlayProgress, true);
        if (seekSubProgress != null) setupSubProgressSeekBar(seekSubProgress);

        if (seekMainVolume != null) {
            seekMainVolume.setMax(100);
            seekMainVolume.setProgress((int) (mainPlayerVolume * 100));
            seekMainVolume.setOnTouchListener((v, event) -> {
                if (scrollView == null) return false;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    scrollView.requestDisallowInterceptTouchEvent(true);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    scrollView.requestDisallowInterceptTouchEvent(false);
                }
                return false;
            });
            seekMainVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    mainPlayerVolume = progress / 100f;
                    applyMainPlayerVolume();
                    updateVolumeLabels();
                    showMainOverlayControls(false);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    showMainOverlayControls(false);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    showMainOverlayControls(true);
                }
            });
        }

        if (seekSubVolume != null) {
            seekSubVolume.setMax(100);
            seekSubVolume.setProgress((int) (presentationPlayerVolume * 100));
            seekSubVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    presentationPlayerVolume = progress / 100f;
                    applyPresentationPlayerSettings();
                    updateVolumeLabels();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        if (btnSubSeekBack10 != null) {
            btnSubSeekBack10.setOnClickListener(v -> seekPresentationBy(-SEEK_STEP_MS));
        }
        if (btnSubSeekForward10 != null) {
            btnSubSeekForward10.setOnClickListener(v -> seekPresentationBy(SEEK_STEP_MS));
        }
        if (btnOverlaySpeedEntry != null) {
            btnOverlaySpeedEntry.setOnClickListener(v -> toggleMainOverlayPanel(layoutOverlaySpeedPanel, btnOverlaySpeedEntry));
        }
        if (btnOverlayVolumeEntry != null) {
            btnOverlayVolumeEntry.setOnClickListener(v -> toggleMainOverlayPanel(layoutOverlayVolumePanel, btnOverlayVolumeEntry));
        }
        bindMainSpeedItem(tvOverlaySpeed200, 5);
        bindMainSpeedItem(tvOverlaySpeed150, 4);
        bindMainSpeedItem(tvOverlaySpeed125, 3);
        bindMainSpeedItem(tvOverlaySpeed100, 2);
        bindMainSpeedItem(tvOverlaySpeed075, 1);
        bindMainSpeedItem(tvOverlaySpeed050, 0);
        if (btnSubSpeed != null) {
            btnSubSpeed.setOnClickListener(v -> {
                currentSubSpeedPresetIndex = (currentSubSpeedPresetIndex + 1) % SPEED_PRESETS.length;
                applyPresentationPlaybackSpeed();
                updateSubSpeedButtonLabel();
            });
        }
        if (btnOverlayPlayPause != null) {
            btnOverlayPlayPause.setOnClickListener(v -> {
                toggleMediaPlayer(mediaPlayer, isAudioDeviceSet, btnMainPlay);
                showMainOverlayControls(true);
            });
        }
        if (btnOverlaySettings != null) {
            btnOverlaySettings.setOnClickListener(v -> toggleMainOverlayPanel(layoutOverlaySettingPanel, btnOverlaySettings));
        }

        if (switchOverlayLoop != null) {
            switchOverlayLoop.setChecked(loopModeEnabled);
            switchOverlayLoop.setOnCheckedChangeListener((buttonView, isChecked) -> {
                loopModeEnabled = isChecked;
                applyLoopModeToPlayers();
                updateLoopButtonLabel();
                showMainOverlayControls(true);
            });
        }
        if (switchOverlayAutoPlay != null) {
            switchOverlayAutoPlay.setChecked(autoPlayEnabled);
            switchOverlayAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
                autoPlayEnabled = isChecked;
                if (presentation != null) {
                    presentation.setAutoPlayEnabled(isChecked);
                }
                showMainOverlayControls(true);
            });
        }

        updateMainProgressUi();
        updateSubProgressUi();
        updateOverlayMainProgressUi();
        updateVolumeLabels();
        updateSpeedButtonLabel();
        updateSubSpeedButtonLabel();
        updateLoopButtonLabel();
        updateMainPlayVisuals();
        updateFullScreenIcon();
        applyMainOverlayUiScale(false);
        showMainOverlayControls(true);
        startMainProgressUpdater();
    }

    private void setupSubControlFold() {
        if (layoutSubControlContent == null) return;
        updateSubControlFoldUi(true);
        View.OnClickListener toggleListener = v -> toggleSubControlFold();
        if (layoutSubControlHeader != null) {
            layoutSubControlHeader.setOnClickListener(toggleListener);
        }
        if (tvSubControlToggle != null) {
            tvSubControlToggle.setOnClickListener(toggleListener);
        }
    }

    private void toggleSubControlFold() {
        subControlExpanded = !subControlExpanded;
        updateSubControlFoldUi(false);
    }

    private void updateSubControlFoldUi(boolean immediate) {
        if (layoutSubControlContent == null) return;
        if (tvSubControlToggle != null) {
            tvSubControlToggle.setText(subControlExpanded ? "收起" : "展开");
        }

        layoutSubControlContent.animate().cancel();
        if (subControlExpanded) {
            layoutSubControlContent.setVisibility(View.VISIBLE);
            if (immediate) {
                layoutSubControlContent.setAlpha(1f);
            } else {
                layoutSubControlContent.setAlpha(0f);
                layoutSubControlContent.animate().alpha(1f).setDuration(140L).start();
            }
            return;
        }

        if (immediate) {
            layoutSubControlContent.setAlpha(1f);
            layoutSubControlContent.setVisibility(View.GONE);
        } else {
            layoutSubControlContent.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .withEndAction(() -> {
                        layoutSubControlContent.setVisibility(View.GONE);
                        layoutSubControlContent.setAlpha(1f);
                    })
                    .start();
        }
    }

    private void setupMainOverlayTapGesture() {
        mainTapGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isTapInCenterZone(mainTapLayer, e)) {
                    toggleMainPlaybackFromGesture();
                    return true;
                }
                hideAllMainOverlayPanels();
                if (overlayControlsVisible) {
                    hideMainOverlayControlsAnimated();
                } else {
                    showMainOverlayControls(true);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleMainPlaybackFromGesture();
                return true;
            }
        });
        if (mainTapLayer != null) {
            mainTapLayer.setClickable(true);
            mainTapLayer.setOnTouchListener((v, event) -> mainTapGestureDetector != null && mainTapGestureDetector.onTouchEvent(event));
            mainTapLayer.setOnGenericMotionListener((v, event) -> {
                if (!isMouseRevealEvent(event)) return false;
                showMainOverlayControls(true);
                return true;
            });
        }
    }

    private boolean isMouseRevealEvent(MotionEvent event) {
        if (event == null) return false;
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_HOVER_MOVE
                && action != MotionEvent.ACTION_HOVER_ENTER
                && action != MotionEvent.ACTION_MOVE) {
            return false;
        }
        return (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
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

    private void bindMainSpeedItem(TextView view, int presetIndex) {
        if (view == null) return;
        view.setOnClickListener(v -> {
            currentSpeedPresetIndex = presetIndex;
            applyMainPlaybackSpeed();
            updateSpeedButtonLabel();
            showMainOverlayControls(true);
        });
    }

    private void updateMainSpeedButtonVisualState() {
        updateMainSpeedItemVisual(tvOverlaySpeed200, currentSpeedPresetIndex == 5);
        updateMainSpeedItemVisual(tvOverlaySpeed150, currentSpeedPresetIndex == 4);
        updateMainSpeedItemVisual(tvOverlaySpeed125, currentSpeedPresetIndex == 3);
        updateMainSpeedItemVisual(tvOverlaySpeed100, currentSpeedPresetIndex == 2);
        updateMainSpeedItemVisual(tvOverlaySpeed075, currentSpeedPresetIndex == 1);
        updateMainSpeedItemVisual(tvOverlaySpeed050, currentSpeedPresetIndex == 0);
    }

    private void updateMainSpeedItemVisual(TextView view, boolean selected) {
        if (view == null) return;
        view.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.overlay_text_primary : R.color.overlay_text_secondary));
        view.setBackgroundResource(selected ? R.drawable.bg_overlay_choice_selected : 0);
        view.setAlpha(selected ? 1f : 0.86f);
    }

    private void toggleMainOverlayPanel(View panel, View anchor) {
        if (panel == null || anchor == null) return;
        boolean shouldShow = panel.getVisibility() != View.VISIBLE;
        hideAllMainOverlayPanels();
        if (shouldShow) {
            bringMainOverlayViewsToFront();
            panel.setAlpha(0f);
            panel.setVisibility(View.VISIBLE);
            positionMainOverlayPanelAboveAnchor(panel, anchor);
            panel.animate().alpha(1f).setDuration(130L).start();
            showMainOverlayControls(false);
        } else {
            showMainOverlayControls(true);
        }
    }

    private void positionMainOverlayPanelAboveAnchor(View panel, View anchor) {
        if (panel == null || anchor == null || surfaceContainer == null) return;
        panel.post(() -> {
            int parentWidth = surfaceContainer.getWidth();
            int panelWidth = panel.getWidth();
            if (parentWidth <= 0 || panelWidth <= 0) return;
            float centerX = anchor.getX() + (anchor.getWidth() / 2f);
            float targetX = centerX - (panelWidth / 2f);
            float minX = dpToPx(OVERLAY_PANEL_EDGE_DP);
            float maxX = Math.max(minX, parentWidth - panelWidth - dpToPx(OVERLAY_PANEL_EDGE_DP));
            panel.setX(Math.max(minX, Math.min(maxX, targetX)));

            if (layoutOverlayControls != null) {
                float targetY = layoutOverlayControls.getY() - panel.getHeight() - dpToPx(OVERLAY_PANEL_GAP_DP);
                panel.setY(Math.max(dpToPx(OVERLAY_PANEL_EDGE_DP), targetY));
            }
        });
    }

    private void applyMainOverlayUiScale(boolean fullScreenMode) {
        if (layoutOverlayControls != null) {
            int horizontal = dpToPx(fullScreenMode ? 14 : 12);
            int vertical = dpToPx(fullScreenMode ? 12 : 10);
            layoutOverlayControls.setPadding(horizontal, vertical, horizontal, vertical);
        }

        setViewSizeDp(btnOverlayPlayPause, fullScreenMode ? 48 : 36, fullScreenMode ? 48 : 36);
        setViewSizeDp(btnOverlayVolumeEntry, fullScreenMode ? 40 : 34, fullScreenMode ? 40 : 34);
        setViewSizeDp(btnOverlaySettings, fullScreenMode ? 40 : 34, fullScreenMode ? 40 : 34);
        setViewSizeDp(btnFullScreen, fullScreenMode ? 40 : 34, fullScreenMode ? 40 : 34);

        if (btnOverlaySpeedEntry != null) {
            ViewGroup.LayoutParams lp = btnOverlaySpeedEntry.getLayoutParams();
            if (lp != null) {
                lp.height = dpToPx(fullScreenMode ? 46 : 40);
                btnOverlaySpeedEntry.setLayoutParams(lp);
            }
            btnOverlaySpeedEntry.setMinWidth(dpToPx(fullScreenMode ? 104 : 88));
            btnOverlaySpeedEntry.setMinHeight(dpToPx(fullScreenMode ? 46 : 40));
            btnOverlaySpeedEntry.setTextSize(fullScreenMode ? 15f : 13f);
        }

        setTextWidthAndSizeDp(tvOverlayCurrentTime, fullScreenMode ? 54 : 46, fullScreenMode ? 14f : 12f);
        setTextWidthAndSizeDp(tvOverlayTotalTime, fullScreenMode ? 54 : 46, fullScreenMode ? 14f : 12f);

        setPanelWidthAndPaddingDp(layoutOverlaySpeedPanel, fullScreenMode ? 116 : 104, fullScreenMode ? 14 : 12);
        setSpeedItemSize(tvOverlaySpeed200, fullScreenMode);
        setSpeedItemSize(tvOverlaySpeed150, fullScreenMode);
        setSpeedItemSize(tvOverlaySpeed125, fullScreenMode);
        setSpeedItemSize(tvOverlaySpeed100, fullScreenMode);
        setSpeedItemSize(tvOverlaySpeed075, fullScreenMode);
        setSpeedItemSize(tvOverlaySpeed050, fullScreenMode);

        setPanelSizeAndPaddingDp(layoutOverlayVolumePanel,
                fullScreenMode ? 116 : 104,
                fullScreenMode ? 250 : 228,
                fullScreenMode ? 14 : 12);
        if (tvMainVolumeValue != null) {
            tvMainVolumeValue.setTextSize(fullScreenMode ? 16f : 14f);
        }
        setViewSizeDp(seekMainVolume, fullScreenMode ? 184 : 160, fullScreenMode ? 44 : 40);

        setPanelWidthAndPaddingDp(layoutOverlaySettingPanel, fullScreenMode ? 316 : 278, fullScreenMode ? 14 : 12);
        updateSettingPanelRows(layoutOverlaySettingPanel, fullScreenMode);
    }

    private void setSpeedItemSize(TextView view, boolean fullScreenMode) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.height = dpToPx(fullScreenMode ? 44 : 40);
            view.setLayoutParams(lp);
        }
        view.setTextSize(fullScreenMode ? 15f : 14f);
    }

    private void setPanelWidthAndPaddingDp(View panel, int widthDp, int paddingDp) {
        if (panel == null) return;
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp != null) {
            lp.width = dpToPx(widthDp);
            panel.setLayoutParams(lp);
        }
        int p = dpToPx(paddingDp);
        panel.setPadding(p, p, p, p);
    }

    private void setPanelSizeAndPaddingDp(View panel, int widthDp, int heightDp, int paddingDp) {
        if (panel == null) return;
        ViewGroup.LayoutParams lp = panel.getLayoutParams();
        if (lp != null) {
            lp.width = dpToPx(widthDp);
            lp.height = dpToPx(heightDp);
            panel.setLayoutParams(lp);
        }
        int p = dpToPx(paddingDp);
        panel.setPadding(p, p, p, p);
    }

    private void setViewSizeDp(View view, int widthDp, int heightDp) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        lp.width = dpToPx(widthDp);
        lp.height = dpToPx(heightDp);
        view.setLayoutParams(lp);
    }

    private void setTextWidthAndSizeDp(TextView view, int widthDp, float sizeSp) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.width = dpToPx(widthDp);
            view.setLayoutParams(lp);
        }
        view.setTextSize(sizeSp);
    }

    private void updateSettingPanelRows(View panel, boolean fullScreenMode) {
        if (!(panel instanceof LinearLayout)) return;
        LinearLayout container = (LinearLayout) panel;
        int rowHeight = dpToPx(fullScreenMode ? 56 : 50);
        int secondTopMargin = dpToPx(fullScreenMode ? 10 : 8);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            ViewGroup.LayoutParams baseParams = child.getLayoutParams();
            if (baseParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) baseParams;
                mlp.height = rowHeight;
                mlp.topMargin = (i == 0) ? 0 : secondTopMargin;
                child.setLayoutParams(mlp);
            } else {
                baseParams.height = rowHeight;
                child.setLayoutParams(baseParams);
            }
            LinearLayout row = (LinearLayout) child;
            View title = row.getChildAt(0);
            if (title instanceof TextView) {
                ((TextView) title).setTextSize(fullScreenMode ? 16f : 15f);
            }
        }
    }

    private void hideAllMainOverlayPanels() {
        hideMainOverlayPanel(layoutOverlaySpeedPanel);
        hideMainOverlayPanel(layoutOverlayVolumePanel);
        hideMainOverlayPanel(layoutOverlaySettingPanel);
    }

    private void hideMainOverlayPanel(View panel) {
        if (panel == null || panel.getVisibility() != View.VISIBLE) return;
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        panel.setAlpha(1f);
    }

    private boolean isAnyMainOverlayPanelVisible() {
        return (layoutOverlaySpeedPanel != null && layoutOverlaySpeedPanel.getVisibility() == View.VISIBLE)
                || (layoutOverlayVolumePanel != null && layoutOverlayVolumePanel.getVisibility() == View.VISIBLE)
                || (layoutOverlaySettingPanel != null && layoutOverlaySettingPanel.getVisibility() == View.VISIBLE);
    }

    private void showMainOverlayControls(boolean allowAutoHide) {
        if (layoutOverlayControls == null) return;
        bringMainOverlayViewsToFront();
        overlayControlsVisible = true;
        layoutOverlayControls.animate().cancel();
        if (layoutOverlayControls.getVisibility() != View.VISIBLE) {
            layoutOverlayControls.setAlpha(0f);
            layoutOverlayControls.setTranslationY(dpToPx(OVERLAY_REVEAL_OFFSET_DP));
            layoutOverlayControls.setVisibility(View.VISIBLE);
        }
        layoutOverlayControls.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160L)
                .start();
        if (allowAutoHide) {
            scheduleMainOverlayAutoHideIfNeeded();
        } else {
            cancelMainOverlayAutoHide();
        }
    }

    private void hideMainOverlayControlsAnimated() {
        if (layoutOverlayControls == null) return;
        if (!overlayControlsVisible) return;
        hideAllMainOverlayPanels();
        overlayControlsVisible = false;
        cancelMainOverlayAutoHide();
        layoutOverlayControls.animate().cancel();
        layoutOverlayControls.animate()
                .alpha(0f)
                .translationY(dpToPx(OVERLAY_REVEAL_OFFSET_DP))
                .setDuration(OVERLAY_FADE_DURATION_MS)
                .withEndAction(() -> {
                    if (!overlayControlsVisible && layoutOverlayControls != null) {
                        layoutOverlayControls.setVisibility(View.GONE);
                        layoutOverlayControls.setAlpha(1f);
                        layoutOverlayControls.setTranslationY(0f);
                    }
                }).start();
    }

    private void bringMainOverlayViewsToFront() {
        if (layoutOverlayControls != null) layoutOverlayControls.bringToFront();
        if (layoutOverlaySpeedPanel != null) layoutOverlaySpeedPanel.bringToFront();
        if (layoutOverlayVolumePanel != null) layoutOverlayVolumePanel.bringToFront();
        if (layoutOverlaySettingPanel != null) layoutOverlaySettingPanel.bringToFront();
        if (ivCenterPlayState != null) ivCenterPlayState.bringToFront();
        if (surfaceContainer != null) {
            surfaceContainer.invalidate();
        }
    }

    private void scheduleMainOverlayAutoHideIfNeeded() {
        cancelMainOverlayAutoHide();
        if (!overlayControlsVisible) return;
        if (isAnyMainOverlayPanelVisible()) return;
        if (!isPlayerPlayingSafely(mediaPlayer)) return;
        uiHandler.postDelayed(hideMainOverlayRunnable, OVERLAY_AUTO_HIDE_MS);
    }

    private void cancelMainOverlayAutoHide() {
        uiHandler.removeCallbacks(hideMainOverlayRunnable);
    }

    private void toggleMainPlaybackFromGesture() {
        if (mediaPlayer == null) {
            Toast.makeText(getApplicationContext(), "请先选择主屏媒体文件", Toast.LENGTH_SHORT).show();
            showMainOverlayControls(false);
            return;
        }
        if (!isAudioDeviceSet) {
            Toast.makeText(getApplicationContext(), "请选择主屏音频通道", Toast.LENGTH_SHORT).show();
            showMainOverlayControls(false);
            return;
        }
        toggleMediaPlayer(mediaPlayer, isAudioDeviceSet, btnMainPlay);
        showMainCenterPlayState(isPlayerPlayingSafely(mediaPlayer));
        showMainOverlayControls(true);
    }

    private void showMainCenterPlayState(boolean playingAfterToggle) {
        if (ivCenterPlayState == null) return;
        uiHandler.removeCallbacks(hideCenterPlayStateRunnable);
        ivCenterPlayState.animate().cancel();
        ivCenterPlayState.setImageResource(playingAfterToggle ? R.drawable.ic_play_triangle : R.drawable.ic_pause_simple);
        ivCenterPlayState.setAlpha(0f);
        ivCenterPlayState.setVisibility(View.VISIBLE);
        ivCenterPlayState.animate().alpha(0.96f).setDuration(110L).start();
        uiHandler.postDelayed(hideCenterPlayStateRunnable, CENTER_PLAY_ICON_DURATION_MS);
    }

    private void setupMainProgressSeekBar(SeekBar seekBar, boolean overlay) {
        seekBar.setMax(1000);
        seekBar.setProgress(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || mediaPlayer == null) return;
                int durationMs = safeGetDuration(mediaPlayer);
                if (durationMs <= 0) return;
                int targetMs = (int) ((progress / 1000f) * durationMs);
                if (tvOverlayCurrentTime != null && overlay) {
                    tvOverlayCurrentTime.setText(formatTimeMs(targetMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                showMainOverlayControls(false);
                if (overlay) {
                    overlayProgressUserSeeking = true;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (overlay) {
                    overlayProgressUserSeeking = false;
                }
                if (mediaPlayer == null) return;
                int durationMs = safeGetDuration(mediaPlayer);
                if (durationMs <= 0) return;
                int targetMs = (int) ((bar.getProgress() / 1000f) * durationMs);
                safeSeekMainTo(targetMs);
                updateMainProgressUi();
                updateOverlayMainProgressUi();
                showMainOverlayControls(true);
            }
        });
    }

    private void setupSubProgressSeekBar(SeekBar seekBar) {
        seekBar.setMax(1000);
        seekBar.setProgress(0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                MediaPlayer subPlayer = getPresentationPlayer();
                if (!fromUser || subPlayer == null) return;
                int durationMs = safeGetDuration(subPlayer);
                if (durationMs <= 0) return;
                int targetMs = (int) ((progress / 1000f) * durationMs);
                if (tvSubCurrentTime != null) {
                    tvSubCurrentTime.setText(formatTimeMs(targetMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                subProgressUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                subProgressUserSeeking = false;
                MediaPlayer subPlayer = getPresentationPlayer();
                if (subPlayer == null) return;
                int durationMs = safeGetDuration(subPlayer);
                if (durationMs <= 0) return;
                int targetMs = (int) ((bar.getProgress() / 1000f) * durationMs);
                safeSeekPresentationTo(targetMs);
                updateSubProgressUi();
            }
        });
    }

    private void startMainProgressUpdater() {
        uiHandler.removeCallbacks(mainProgressUpdater);
        uiHandler.post(mainProgressUpdater);
    }

    private void updateMainProgressUi() {
        if (mediaPlayer == null) {
            updateMainPlayVisuals();
            cancelMainOverlayAutoHide();
            return;
        }
        updateMainPlayVisuals();
    }

    private void updateOverlayMainProgressUi() {
        if (seekOverlayProgress == null || tvOverlayCurrentTime == null || tvOverlayTotalTime == null) return;
        if (mediaPlayer == null) {
            if (!overlayProgressUserSeeking) {
                seekOverlayProgress.setProgress(0);
            }
            tvOverlayCurrentTime.setText("00:00");
            tvOverlayTotalTime.setText("00:00");
            updateMainPlayVisuals();
            cancelMainOverlayAutoHide();
            return;
        }
        int durationMs = safeGetDuration(mediaPlayer);
        int currentMs = safeGetCurrentPosition(mediaPlayer);
        if (!overlayProgressUserSeeking) {
            int progress = durationMs > 0 ? (int) ((currentMs * 1000f) / durationMs) : 0;
            seekOverlayProgress.setProgress(Math.max(0, Math.min(1000, progress)));
            tvOverlayCurrentTime.setText(formatTimeMs(currentMs));
        }
        tvOverlayTotalTime.setText(formatTimeMs(Math.max(durationMs, 0)));
        updateMainPlayVisuals();
    }

    private void updateSubProgressUi() {
        if (seekSubProgress == null || tvSubCurrentTime == null || tvSubTotalTime == null) return;
        MediaPlayer subPlayer = getPresentationPlayer();
        if (subPlayer == null) {
            if (!subProgressUserSeeking) {
                seekSubProgress.setProgress(0);
            }
            tvSubCurrentTime.setText("00:00");
            tvSubTotalTime.setText("00:00");
            return;
        }
        int durationMs = safeGetDuration(subPlayer);
        int currentMs = safeGetCurrentPosition(subPlayer);
        if (!subProgressUserSeeking) {
            int progress = durationMs > 0 ? (int) ((currentMs * 1000f) / durationMs) : 0;
            seekSubProgress.setProgress(Math.max(0, Math.min(1000, progress)));
            tvSubCurrentTime.setText(formatTimeMs(currentMs));
        }
        tvSubTotalTime.setText(formatTimeMs(Math.max(durationMs, 0)));
    }

    private void seekPresentationBy(int deltaMs) {
        MediaPlayer subPlayer = getPresentationPlayer();
        if (subPlayer == null) {
            Toast.makeText(getApplicationContext(), "请先选择副屏媒体文件", Toast.LENGTH_SHORT).show();
            return;
        }
        int currentMs = safeGetCurrentPosition(subPlayer);
        int durationMs = safeGetDuration(subPlayer);
        if (durationMs <= 0) return;
        int targetMs = Math.max(0, Math.min(durationMs, currentMs + deltaMs));
        safeSeekPresentationTo(targetMs);
        updateSubProgressUi();
    }

    private void safeSeekMainTo(int targetMs) {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.seekTo(targetMs);
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "主屏 seekTo 失败", e);
        }
    }

    private void safeSeekPresentationTo(int targetMs) {
        MediaPlayer subPlayer = getPresentationPlayer();
        if (subPlayer == null) return;
        try {
            subPlayer.seekTo(targetMs);
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "副屏 seekTo 失败", e);
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

    private MediaPlayer getPresentationPlayer() {
        return (presentation != null) ? presentation.mediaPlayer : null;
    }

    private boolean isPlayerPlayingSafely(MediaPlayer player) {
        if (player == null) return false;
        try {
            return player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void updateMainPlayVisuals() {
        boolean hasPlayer = mediaPlayer != null;
        boolean isPlaying = isPlayerPlayingSafely(mediaPlayer);
        if (btnMainPlay != null) {
            btnMainPlay.setText(isPlaying ? "暂停" : "主屏播放");
        }
        if (btnOverlayPlayPause != null) {
            btnOverlayPlayPause.setEnabled(hasPlayer);
            btnOverlayPlayPause.setAlpha(hasPlayer ? 0.96f : 0.45f);
            btnOverlayPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause_simple : R.drawable.ic_play_triangle);
            btnOverlayPlayPause.setContentDescription(isPlaying ? "暂停" : "播放");
        }
        if (!isPlaying) {
            cancelMainOverlayAutoHide();
        }
    }

    private void updateFullScreenIcon() {
        if (btnFullScreen == null) return;
        btnFullScreen.setImageResource(isFullScreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen_enter);
        btnFullScreen.setContentDescription(isFullScreen ? "退出全屏" : "进入全屏");
    }

    private void applyMainPlayerVolume() {
        if (mediaPlayer == null) return;
        try {
            mediaPlayer.setVolume(mainPlayerVolume, mainPlayerVolume);
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "主屏音量设置失败", e);
        }
    }

    private void applyPresentationPlayerSettings() {
        if (presentation == null) return;
        try {
            presentation.setLoopingEnabled(loopModeEnabled);
            presentation.setAutoPlayEnabled(autoPlayEnabled);
            presentation.setPlayerVolume(presentationPlayerVolume);
            presentation.setSpeedPresetIndex(currentSubSpeedPresetIndex);
            if (presentation.mediaPlayer != null) {
                presentation.applyOverlayPlaybackSettings();
                updateSubProgressUi();
            }
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "副屏参数设置失败", e);
        }
    }

    private void applyLoopModeToPlayers() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setLooping(loopModeEnabled);
            } catch (IllegalStateException e) {
                Log.w("MediaPlayer", "主屏循环模式设置失败", e);
            }
        }
        applyPresentationPlayerSettings();
    }

    private void applyMainPlaybackSpeed() {
        if (mediaPlayer == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        float speed = SPEED_PRESETS[currentSpeedPresetIndex];
        try {
            PlaybackParams params = mediaPlayer.getPlaybackParams();
            if (params == null) params = new PlaybackParams();
            params.setSpeed(speed);
            params.setPitch(1.0f);
            mediaPlayer.setPlaybackParams(params);
        } catch (Exception e) {
            Log.w("MediaPlayer", "主屏倍速设置失败", e);
        }
    }

    private void applyPresentationPlaybackSpeed() {
        MediaPlayer subPlayer = getPresentationPlayer();
        if (subPlayer == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        float speed = SPEED_PRESETS[currentSubSpeedPresetIndex];
        try {
            PlaybackParams params = subPlayer.getPlaybackParams();
            if (params == null) params = new PlaybackParams();
            params.setSpeed(speed);
            params.setPitch(1.0f);
            subPlayer.setPlaybackParams(params);
        } catch (Exception e) {
            Log.w("MediaPlayer", "副屏倍速设置失败", e);
        }
    }

    private void updateSpeedButtonLabel() {
        if (btnOverlaySpeedEntry != null) {
            btnOverlaySpeedEntry.setText("倍速");
        }
        updateMainSpeedButtonVisualState();
    }

    private void updateSubSpeedButtonLabel() {
        if (btnSubSpeed == null) return;
        btnSubSpeed.setText(String.format(Locale.US, "副屏 %.2fx", SPEED_PRESETS[currentSubSpeedPresetIndex]));
    }

    private void updateLoopButtonLabel() {
        if (switchOverlayLoop != null && switchOverlayLoop.isChecked() != loopModeEnabled) {
            switchOverlayLoop.setChecked(loopModeEnabled);
        }
    }

    private void updateVolumeLabels() {
        if (tvMainVolumeValue != null) {
            tvMainVolumeValue.setText(String.valueOf((int) (mainPlayerVolume * 100)));
        }
        if (tvSubVolumeValue != null) {
            tvSubVolumeValue.setText((int) (presentationPlayerVolume * 100) + "%");
        }
        if (seekMainVolume != null) {
            int target = (int) (mainPlayerVolume * 100);
            if (seekMainVolume.getProgress() != target) {
                seekMainVolume.setProgress(target);
            }
        }
    }

    private String formatTimeMs(int totalMs) {
        int safeMs = Math.max(totalMs, 0);
        int totalSeconds = safeMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void disableInteractionSoundEffects(View... views) {
        if (views == null) return;
        for (View view : views) {
            if (view == null) continue;
            view.setSoundEffectsEnabled(false);
            view.setHapticFeedbackEnabled(false);
        }
    }

    private void suppressSystemUiSoundEffectsTemporarily() {
        if (mAudioManager == null) return;
        uiHandler.removeCallbacks(restoreSystemStreamRunnable);
        if (!systemStreamMutedByApp) {
            try {
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
                systemStreamMutedByApp = true;
            } catch (Exception e) {
                Log.w("AudioDevice", "临时静音系统音效失败", e);
                return;
            }
        }
        uiHandler.postDelayed(restoreSystemStreamRunnable, 1800);
    }

    private void restoreSystemUiSoundEffects() {
        if (!systemStreamMutedByApp || mAudioManager == null) return;
        try {
            mAudioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception e) {
            Log.w("AudioDevice", "恢复系统音效失败", e);
        } finally {
            systemStreamMutedByApp = false;
        }
    }

    private void updateDashboardStatus() {
        if (tvStatusMain != null) {
            tvStatusMain.setText("● 主屏在线");
            tvStatusMain.setTextColor(ContextCompat.getColor(this, R.color.status_success));
        }
        boolean hasSubDisplay = false;
        if (allDisplays != null) {
            for (Display display : allDisplays) {
                if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    hasSubDisplay = true;
                    break;
                }
            }
        }
        if (tvStatusSub != null) {
            tvStatusSub.setText(hasSubDisplay ? "● 副屏已连接" : "● 副屏未连接");
            tvStatusSub.setTextColor(ContextCompat.getColor(
                    this, hasSubDisplay ? R.color.status_success : R.color.status_warning));
        }
        if (tvStatusAudioCount != null) {
            int count = OutputDevices != null ? OutputDevices.size() : 0;
            tvStatusAudioCount.setText("● 音频设备 " + count);
            tvStatusAudioCount.setTextColor(ContextCompat.getColor(
                    this, count > 0 ? R.color.status_success : R.color.status_warning));
        }
        updateSecondaryControlsEnabled(hasSubDisplay);
    }

    private void updateSecondaryControlsEnabled(boolean hasSubDisplay) {
        float enabledAlpha = 1.0f;
        float disabledAlpha = 0.45f;
        if (btnPresentationPlay != null) {
            btnPresentationPlay.setEnabled(hasSubDisplay);
            btnPresentationPlay.setAlpha(hasSubDisplay ? enabledAlpha : disabledAlpha);
        }
        if (btnPresentationFile != null) {
            btnPresentationFile.setEnabled(hasSubDisplay);
            btnPresentationFile.setAlpha(hasSubDisplay ? enabledAlpha : disabledAlpha);
        }
        if (mAudioDevicesSpinner2 != null) {
            mAudioDevicesSpinner2.setEnabled(hasSubDisplay);
            mAudioDevicesSpinner2.setAlpha(hasSubDisplay ? enabledAlpha : 0.55f);
        }
        if (displaySpinner != null) {
            displaySpinner.setEnabled(hasSubDisplay);
            displaySpinner.setAlpha(hasSubDisplay ? enabledAlpha : 0.55f);
        }
        if (seekSubProgress != null) {
            seekSubProgress.setEnabled(hasSubDisplay);
            seekSubProgress.setAlpha(hasSubDisplay ? enabledAlpha : 0.55f);
        }
        if (seekSubVolume != null) {
            seekSubVolume.setEnabled(hasSubDisplay);
            seekSubVolume.setAlpha(hasSubDisplay ? enabledAlpha : 0.55f);
        }
        if (btnSubSeekBack10 != null) {
            btnSubSeekBack10.setEnabled(hasSubDisplay);
            btnSubSeekBack10.setAlpha(hasSubDisplay ? enabledAlpha : disabledAlpha);
        }
        if (btnSubSeekForward10 != null) {
            btnSubSeekForward10.setEnabled(hasSubDisplay);
            btnSubSeekForward10.setAlpha(hasSubDisplay ? enabledAlpha : disabledAlpha);
        }
        if (btnSubSpeed != null) {
            btnSubSpeed.setEnabled(hasSubDisplay);
            btnSubSpeed.setAlpha(hasSubDisplay ? enabledAlpha : disabledAlpha);
        }
    }

    private void updateRouteSummaryTexts() {
        if (tvSummaryMainAudio != null) {
            String item = mAudioDevicesSpinner1 != null ? mAudioDevicesSpinner1.getText().toString() : "";
            tvSummaryMainAudio.setText("当前：" + (!item.isEmpty() ? item : "未选择"));
        }
        if (tvSummarySubAudio != null) {
            String item = mAudioDevicesSpinner2 != null ? mAudioDevicesSpinner2.getText().toString() : "";
            tvSummarySubAudio.setText("当前：" + (!item.isEmpty() ? item : "未选择"));
        }
        if (tvSummaryDisplay != null) {
            String item = displaySpinner != null ? displaySpinner.getText().toString() : "";
            tvSummaryDisplay.setText("当前：" + (!item.isEmpty() ? item : "未选择"));
        }
    }

    private Surface getPresentationSurface() {
        if (presentation == null) return null;
        if (presentation.surfaceView != null && presentation.surfaceView.getHolder() != null) {
            Surface surface = presentation.surfaceView.getHolder().getSurface();
            if (surface != null) {
                return surface;
            }
        }
        return presentation.surface;
    }

    private void enterFullScreen() {
        if (isFullScreen) return;
        if (mediaPlayer == null) {
            Toast.makeText(getApplicationContext(), "请先播放主屏视频", Toast.LENGTH_SHORT).show();
            return;
        }
        wasPlayingBeforeFullScreen = mediaPlayer.isPlaying();
        isFullScreen = true;
        applyInlineFullScreen(true);
    }

    private void exitFullScreen() {
        if (!isFullScreen) return;
        isFullScreen = false;
        applyInlineFullScreen(false);
        if (mediaPlayer != null && wasPlayingBeforeFullScreen && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void cacheNonPlayerViews() {
        if (!(controlsLayout instanceof ViewGroup) || !nonPlayerViews.isEmpty()) return;
        ViewGroup group = controlsLayout;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getId() != R.id.surfaceContainer) {
                nonPlayerViews.add(child);
            }
        }
    }

    private void applyInlineFullScreen(boolean enable) {
        cacheNonPlayerViews();
        hideAllMainOverlayPanels();
        ViewGroup.LayoutParams params = surfaceContainer.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;

        if (enable) {
            if (inlineOriginalSurfaceHeight < 0) {
                inlineOriginalSurfaceHeight = lp.height;
                inlineOriginalSurfaceTopMargin = lp.topMargin;
                inlineOriginalSurfaceBottomMargin = lp.bottomMargin;
            }
            if (controlsLayout != null && !inlineOriginalPaddingCaptured) {
                inlineOriginalPaddingLeft = controlsLayout.getPaddingLeft();
                inlineOriginalPaddingTop = controlsLayout.getPaddingTop();
                inlineOriginalPaddingRight = controlsLayout.getPaddingRight();
                inlineOriginalPaddingBottom = controlsLayout.getPaddingBottom();
                inlineOriginalPaddingCaptured = true;
                ViewGroup.LayoutParams controlsParams = controlsLayout.getLayoutParams();
                if (controlsParams != null) {
                    inlineOriginalControlsLayoutHeight = controlsParams.height;
                }
            }
            for (View view : nonPlayerViews) {
                view.setVisibility(View.GONE);
            }
            if (controlsLayout != null) {
                controlsLayout.setPadding(0, 0, 0, 0);
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
            scrollView.setFillViewport(true);

            View root = findViewById(R.id.rootLayout);
            int targetHeight = root != null ? root.getHeight() : 0;
            if (scrollView != null) {
                targetHeight = Math.max(targetHeight, scrollView.getHeight());
            }
            View decor = getWindow().getDecorView();
            if (decor != null) {
                targetHeight = Math.max(targetHeight, decor.getHeight());
            }
            if (targetHeight <= 0) {
                targetHeight = getResources().getDisplayMetrics().heightPixels;
            }

            if (controlsLayout != null) {
                ViewGroup.LayoutParams controlsParams = controlsLayout.getLayoutParams();
                if (controlsParams != null) {
                    controlsParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    controlsLayout.setLayoutParams(controlsParams);
                }
            }
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.topMargin = 0;
            lp.bottomMargin = 0;
            surfaceContainer.setLayoutParams(lp);
            updateFullScreenIcon();
            applyMainOverlayUiScale(true);
            View rootLayout = findViewById(R.id.rootLayout);
            if (rootLayout != null) {
                rootLayout.setBackgroundColor(Color.BLACK);
            }
            applyImmersiveMode(getWindow());
            showMainOverlayControls(true);
            uiHandler.postDelayed(this::scheduleMainOverlayAutoHideIfNeeded, 120L);
        } else {
            for (View view : nonPlayerViews) {
                view.setVisibility(View.VISIBLE);
            }
            if (controlsLayout != null && inlineOriginalPaddingCaptured) {
                controlsLayout.setPadding(
                        inlineOriginalPaddingLeft,
                        inlineOriginalPaddingTop,
                        inlineOriginalPaddingRight,
                        inlineOriginalPaddingBottom);
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().show();
            }
            scrollView.setFillViewport(true);
            if (inlineOriginalSurfaceHeight > 0) {
                lp.height = inlineOriginalSurfaceHeight;
            }
            lp.topMargin = inlineOriginalSurfaceTopMargin;
            lp.bottomMargin = inlineOriginalSurfaceBottomMargin;
            surfaceContainer.setLayoutParams(lp);
            if (controlsLayout != null) {
                ViewGroup.LayoutParams controlsParams = controlsLayout.getLayoutParams();
                if (controlsParams != null) {
                    controlsParams.height = inlineOriginalControlsLayoutHeight;
                    controlsLayout.setLayoutParams(controlsParams);
                }
            }
            scrollView.setVisibility(View.VISIBLE);
            if (btnMainPlay != null) {
                btnMainPlay.setVisibility(View.VISIBLE);
            }
            updateFullScreenIcon();
            applyMainOverlayUiScale(false);
            View rootLayout = findViewById(R.id.rootLayout);
            if (rootLayout != null) {
                rootLayout.setBackgroundResource(R.drawable.bg_main_gradient);
            }
            clearImmersiveMode();
            scrollToInlinePreview();
            showMainOverlayControls(true);
        }
    }

    private void ensureFullScreenDialog() {
        if (fullScreenDialog != null) return;

        fullScreenDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        fullScreenSurfaceView = new SurfaceView(this);
        FrameLayout.LayoutParams surfaceLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(fullScreenSurfaceView, surfaceLp);

        Button closeButton = new Button(this);
        closeButton.setText("退出全屏");
        closeButton.setAlpha(0.85f);
        closeButton.setOnClickListener(v -> exitFullScreen());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.gravity = Gravity.TOP | Gravity.END;
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        closeLp.topMargin = margin;
        closeLp.rightMargin = margin;
        root.addView(closeButton, closeLp);

        fullScreenDialog.setContentView(root);
        fullScreenDialog.setCanceledOnTouchOutside(false);
        fullScreenDialog.setOnShowListener(dialog -> applyImmersiveMode(fullScreenDialog.getWindow()));
        fullScreenDialog.setOnDismissListener(dialog -> {
            isFullScreen = false;
            restoreInlinePlayerSurfaceAndUi();
        });

        Window window = fullScreenDialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        fullScreenSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                fullScreenSurfaceHolder = holder;
                fullScreenSurface = holder.getSurface();
                if (isFullScreen) {
                    switchMainPlayerSurface(holder, "全屏 Dialog Surface");
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // no-op
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                fullScreenSurfaceHolder = null;
                fullScreenSurface = null;
            }
        });
    }

    private void restoreInlinePlayerSurfaceAndUi() {
        scrollView.setVisibility(View.VISIBLE);
        scrollToInlinePreview();
        if (btnMainPlay != null) {
            btnMainPlay.setVisibility(View.VISIBLE);
        }
        View controlsLayout = findViewById(R.id.controlsLayout);
        if (controlsLayout != null) {
            controlsLayout.setVisibility(View.VISIBLE);
        }
        btnFullScreen.setVisibility(View.VISIBLE);
        clearImmersiveMode();
        if (mainSurfaceHolder != null && surface != null && surface.isValid()) {
            switchMainPlayerSurface(mainSurfaceHolder, "主界面 Surface");
            pendingAttachToMainSurface = false;
        } else {
            pendingAttachToMainSurface = true;
        }
    }

    private void switchMainPlayerSurface(SurfaceHolder targetHolder, String targetName) {
        if (mediaPlayer == null || targetHolder == null) return;
        try {
            mediaPlayer.setDisplay(targetHolder);
            Surface targetSurface = targetHolder.getSurface();
            if (targetSurface != null && targetSurface.isValid()
                    && keepPlayingAcrossSurfaceSwitch && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
            Log.d("MediaPlayer", "切换渲染目标到: " + targetName);
        } catch (IllegalStateException e) {
            Log.e("MediaPlayer", "切换 Surface 失败: " + targetName, e);
        } finally {
            Surface targetSurface = targetHolder.getSurface();
            if (targetSurface != null && targetSurface.isValid()) {
                keepPlayingAcrossSurfaceSwitch = false;
            }
        }
    }

    private void applyImmersiveMode(Window window) {
        if (window == null) return;
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void clearImmersiveMode() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (isFullScreen || (fullScreenDialog != null && fullScreenDialog.isShowing())) {
            exitFullScreen();
        } else {
            super.onBackPressed();
        }
    }

    // 用于处理播放和暂停的通用方法
    private void toggleMediaPlayer(MediaPlayer mediaPlayer, boolean isAudioDeviceSet, TextView buttonText) {
        if (mediaPlayer != null) {
            if (isAudioDeviceSet) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    buttonText.setText("主屏播放");
                    updateMainProgressUi();
                } else {
                    mediaPlayer.start();
                    scheduleMainSyncNudge("main-manual-start");
                    buttonText.setText("暂停");
                    updateMainProgressUi();
                }
                updateOverlayMainProgressUi();
                updateMainPlayVisuals();
                showMainOverlayControls(mediaPlayer.isPlaying());
            } else {
                Toast.makeText(getApplicationContext(), "请选择音频通道", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "请选择媒体文件", Toast.LENGTH_SHORT).show();
        }
        updateMainPlayVisuals();
    }

    // 通用方法：设置音频设备并恢复播放状态
    private void handleAudioDeviceSelection(int position, boolean isPresentation) {
        if (OutputDevices == null || position >= OutputDevices.size()) return;
        device = OutputDevices.get(position);

        // 处理音频设备选择和播放状态恢复
        if (isPresentation) {
            handleDeviceSelectionForPresentation(device);
        } else {
            handleDeviceSelectionForAudio(device);
        }
    }

    // 处理副屏的音频设备选择的方法
    private void handleDeviceSelectionForPresentation(Object device) {
        if (presentation == null) {
            Log.d("AudioDevice", "请连接副屏");
            return;
        }
        AudioDeviceInfo newDevice = (AudioDeviceInfo) device;
        if (presentation.selectedDevice != null && newDevice != null
                && presentation.selectedDevice.getId() == newDevice.getId()) {
            Log.d("AudioDevice", "副屏音频设备未变化，跳过重路由");
            return;
        }
        presentation.selectedDevice = newDevice;
        Log.d("AudioDevice", "选中的设备信息：");
        if (selectedDevice != null) {
            Log.d("AudioDevice", "设备ID: " + selectedDevice.getId());
            Log.d("AudioDevice", "设备名称: " + selectedDevice.getProductName());
            Log.d("AudioDevice", "设备类型: " + selectedDevice.getType());
        }
        if (presentation.selectedDevice != null) {
            selectedDeviceCache = presentation.selectedDevice;
        }

        if (selectedPresentionFilePath == null || selectedPresentionFilePath.isEmpty()) {
            Log.d("AudioDevice", "请选择文件");
            return;
        }
        if (presentation.mediaPlayer == null) {
            Log.d("AudioDevice", "副屏播放器未初始化，跳过设置");
            return;
        }
        schedulePresentationRouteSwitch(newDevice);
    }

    // 处理普通音频设备选择的方法
    private void handleDeviceSelectionForAudio(Object device) {
        AudioDeviceInfo newDevice = (AudioDeviceInfo) device;
        if (selectedDevice != null && newDevice != null
                && selectedDevice.getId() == newDevice.getId()) {
            Log.d("AudioDevice", "主屏音频设备未变化，跳过重路由");
            return;
        }
        selectedDevice = newDevice;
        Log.d("AudioDevice", "handleDeviceSelectionForAudio 拿到的Selected Device: " + selectedDevice);
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            Log.d("AudioDevice", "请选择文件");
            return;
        }
        if (mediaPlayer == null) {
            Log.d("AudioDevice", "主屏播放器未初始化，跳过设置");
            return;
        }
        scheduleMainRouteSwitch(newDevice);
    }

    private void scheduleMainRouteSwitch(AudioDeviceInfo targetDevice) {
        if (targetDevice == null) return;
        pendingMainRouteDevice = targetDevice;
        pendingMainRouteTs = SystemClock.uptimeMillis();
        scheduleRouteQueueDrain(ROUTE_SWITCH_DEBOUNCE_MS);
    }

    private void schedulePresentationRouteSwitch(AudioDeviceInfo targetDevice) {
        if (targetDevice == null) return;
        pendingPresentationRouteDevice = targetDevice;
        pendingPresentationRouteTs = SystemClock.uptimeMillis();
        scheduleRouteQueueDrain(ROUTE_SWITCH_DEBOUNCE_MS);
    }

    private void scheduleRouteQueueDrain(long baseDelayMs) {
        uiHandler.removeCallbacks(applyRouteSwitchQueueRunnable);
        long now = SystemClock.uptimeMillis();
        long sinceLast = now - lastGlobalRouteApplyTs;
        long delay = Math.max(0L, baseDelayMs);
        if (routeSwitchInProgress) {
            delay = Math.max(delay, 120L);
        }
        if (sinceLast < ROUTE_SWITCH_GLOBAL_INTERVAL_MS) {
            delay = Math.max(delay, ROUTE_SWITCH_GLOBAL_INTERVAL_MS - sinceLast);
        }
        uiHandler.postDelayed(applyRouteSwitchQueueRunnable, delay);
    }

    private void drainRouteSwitchQueue() {
        if (routeSwitchInProgress) {
            scheduleRouteQueueDrain(120L);
            return;
        }
        AudioDeviceInfo nextMain = pendingMainRouteDevice;
        AudioDeviceInfo nextPresentation = pendingPresentationRouteDevice;
        if (nextMain == null && nextPresentation == null) return;

        routeSwitchInProgress = true;
        try {
            // Apply only one route change per cycle to avoid HAL reconfiguration storms
            // when both players are active.
            boolean chooseMain = nextPresentation == null
                    || (nextMain != null && pendingMainRouteTs <= pendingPresentationRouteTs);
            if (chooseMain && nextMain != null) {
                pendingMainRouteDevice = null;
                applyMainRouteSwitch(nextMain);
            } else if (nextPresentation != null) {
                pendingPresentationRouteDevice = null;
                applyPresentationRouteSwitch(nextPresentation);
            }
            lastGlobalRouteApplyTs = SystemClock.uptimeMillis();
        } finally {
            routeSwitchInProgress = false;
        }

        if (pendingMainRouteDevice != null || pendingPresentationRouteDevice != null) {
            scheduleRouteQueueDrain(ROUTE_SWITCH_GLOBAL_INTERVAL_MS);
        }
    }

    private void togglePresentationPlayback(TextView buttonText) {
        if (presentation == null || presentation.mediaPlayer == null) {
            Toast.makeText(getApplicationContext(), "请选择副屏媒体文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (presentation.isPrimingOutput()) {
            Toast.makeText(getApplicationContext(), "副屏通道预热中，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        MediaPlayer player = presentation.mediaPlayer;
        applyPresentationPlayerSettings();
        if (player.isPlaying()) {
            player.pause();
            buttonText.setText("副屏播放");
            updateSubProgressUi();
            return;
        }

        boolean mainWasPlaying = mediaPlayer != null && mediaPlayer.isPlaying();
        try {
            player.start();
            buttonText.setText("暂停");
            updateSubProgressUi();
            scheduleMainSyncNudge("presentation-start");
            uiHandler.postDelayed(() -> {
                try {
                    if (mainWasPlaying && mediaPlayer != null && !mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                        scheduleMainSyncNudge("main-recovered-after-presentation-start");
                    }
                } catch (IllegalStateException e) {
                    Log.e("AudioDevice", "副屏起播稳定化失败", e);
                }
            }, 220L);
        } catch (IllegalStateException e) {
            Log.e("AudioDevice", "副屏播放启动失败", e);
            Toast.makeText(getApplicationContext(), "副屏播放失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyMainRouteSwitch(AudioDeviceInfo targetDevice) {
        if (targetDevice == null || mediaPlayer == null) return;
        boolean wasPlaying = mediaPlayer.isPlaying();
        setAudioDevice(targetDevice);
        if (wasPlaying && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e("AudioDevice", "主屏恢复播放失败", e);
            }
        }
    }

    private void applyPresentationRouteSwitch(AudioDeviceInfo targetDevice) {
        if (targetDevice == null || presentation == null || presentation.mediaPlayer == null) return;
        boolean wasPlaying = presentation.mediaPlayer.isPlaying();
        Log.d("AudioDevice", "presentation.setAudioDevice(selectedDeviceCache)");
        presentation.setAudioDevice(targetDevice);
        applyPresentationPlayerSettings();
        maybePrimePresentationOutput("route-switch");
        if (wasPlaying && !presentation.mediaPlayer.isPlaying()) {
            try {
                presentation.mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e("AudioDevice", "副屏恢复播放失败", e);
            }
        }
    }

    //打开文件管理器的通用方法
    private void handleFileSelection(ActivityResultLauncher<Intent> launcher, boolean isPresentation) {
        if (launcher != null) {
            launcher.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"));
        } else {
            Log.e("MainActivity", "ActivityResultLauncher is null");
        }
    }

    // 处理文件选择结果的通用方法
    public void handleFileResult(ActivityResult result, boolean isPresentation) {
        Log.d("ActivityResult", "选择文件回调触发");

        if (result.getResultCode() == RESULT_OK) {
            Intent data = result.getData();
            if (data != null && data.getData() != null) {
                toggleSelectedUri = data.getData();
                int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(toggleSelectedUri, takeFlags);
                } catch (Exception e) {
                    Log.w("ActivityResult", "持久化 URI 权限失败", e);
                }
                filePath = getRealPathFromURI(toggleSelectedUri);
                if (filePath != null) {
                    if (isPresentation) {
                        presentionselectedUri = toggleSelectedUri; // 保存副屏文件的 URI
                        selectedPresentionFilePath = filePath;

                        // 判断是否为新的视频文件
                        if (!filePath.equals(previousPresentationFilePath)) {
                            Log.d("PresentationFilePath", "选择了新的视频文件: " + selectedPresentionFilePath);
                            isSwitchingToNewVideo = true;
                        } else {
                            Log.d("PresentationFilePath", "选择的是同一个视频文件");
                            isSwitchingToNewVideo = false;
                        }
                        Log.d("selectedPresentionFilePath", "文件路径为: " + selectedPresentionFilePath);
                        Toast.makeText(MainActivity.this, "文件已选择: " + selectedPresentionFilePath, Toast.LENGTH_SHORT).show();

                        // 更新副屏的 previousFilePath
                        previousPresentationFilePath = filePath;
                        if (presentation != null) {
                            Surface presentationSurface = getPresentationSurface();
                            if (presentationSurface != null && presentationSurface.isValid()) {
                                presentation.initMediaPlayer(presentionselectedUri, presentationSurface);
                                applyPresentationPlayerSettings();
                                if (selectedDeviceCache != null) {
                                    presentation.setAudioDevice(selectedDeviceCache);
                                    maybePrimePresentationOutput("file-selected");
                                }
                            } else {
                                Log.d("MediaPlayer", "副屏 Surface 未就绪，等待用户点击播放时兜底初始化");
                            }
                        }
                    } else {
                        selectedUri = toggleSelectedUri; // 保存主屏文件的 URI
                        selectedFilePath = filePath;

                        // 判断是否为新的视频文件
                        if (!filePath.equals(previousMainScreenFilePath)) {
                            Log.d("SelectedFilePath", "选择了新的视频文件: " + selectedFilePath);
                            isSwitchingToNewVideo = true;
                        } else {
                            Log.d("SelectedFilePath", "选择的是同一个视频文件");
                            isSwitchingToNewVideo = false;
                        }

                        Log.d("SelectedFilePath", "文件路径为: " + selectedFilePath);
                        Toast.makeText(MainActivity.this, "文件已选择: " + selectedFilePath, Toast.LENGTH_SHORT).show();

                        // 更新主屏的 previousFilePath
                        previousMainScreenFilePath = filePath;
                        if (autoPlayEnabled && surface != null && surface.isValid()) {
                            initMediaPlayer(selectedUri, surface);
                            if (selectedDevice != null) {
                                setAudioDevice(selectedDevice);
                            }
                        }
                    }

                } else {
                    Log.d("SelectedFilePath", "无法获取文件路径");
                }
            }
        } else {
            Log.d("ActivityResult", "文件选择未成功");
        }
    }

    // 初始化 MediaPlayer的通用方法
    private void initMediaPlayer(Uri fileUri, Surface surface) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                Log.d("MediaPlayer", "MediaPlayer 被初始化");
            } else {
                Log.d("MediaPlayer", "MediaPlayer 重置");
                mediaPlayer.reset(); // 重置MediaPlayer
            }
            Log.d("MediaPlayer", "主屏URI: " + fileUri.toString());
            // 设置数据源为本地视频文件的路径
            mediaPlayer.setDataSource(this, fileUri);
            Log.d("MediaPlayer", "设置数据源");
            if (mainSurfaceHolder != null) {
                mediaPlayer.setDisplay(mainSurfaceHolder);
            } else {
                mediaPlayer.setSurface(surface);  // 兜底绑定 Surface
            }
            Log.d("MediaPlayer", "绑定Surface");
            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING) {
                    Log.w("MediaPlayer", "主屏检测到 AUDIO_NOT_PLAYING，尝试重新同步");
                    scheduleMainSyncNudge("main-audio-not-playing");
                    return true;
                }
                return false;
            });
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("MediaPlayer", "播放器准备完毕");
                applyMainSyncParams(mp, "main-prepared");
                applyMainPlayerVolume();
                applyMainPlaybackSpeed();
                applyLoopModeToPlayers();
                // 恢复播放状态
                Log.d("MediaPlayer", "准备恢复restorePlaybackState");
                restorePlaybackState();
                if (autoPlayEnabled && isAudioDeviceSet && !isPlayerPlayingSafely(mp)) {
                    try {
                        mp.start();
                        scheduleMainSyncNudge("main-autoplay");
                    } catch (IllegalStateException e) {
                        Log.w("MediaPlayer", "主屏自动开播失败", e);
                    }
                }
                updateMainProgressUi();
                updateOverlayMainProgressUi();
                updateMainPlayVisuals();
                showMainOverlayControls(true);

            });
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!loopModeEnabled) {
                    updateMainPlayVisuals();
                    showMainOverlayControls(false);
                }
                updateMainProgressUi();
                updateOverlayMainProgressUi();
            });
            mediaPlayer.prepareAsync(); // 异步准备播放
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // 释放 MediaPlayer的通用方法
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            Log.d("MediaPlayer", "MediaPlayer 被释放");
        }
    }

    // 保存MediaPlayer播放状态的通用方法
    private void savePlaybackState() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            isVideoPlaying = true;
            Log.d("MediaPlayer", "尝试获取当前播放位置");
            currentPosition = mediaPlayer.getCurrentPosition(); // 获取当前播放位置
        } else {
            Log.d("MediaPlayer", "未去获取当前播放位置");
            isVideoPlaying = false;
        }

    }

    // 恢复MediaPlayer播放状态
    private void restorePlaybackState() {
        if (mediaPlayer != null) {
            try {
                Log.d("MediaPlayer", "尝试恢复restorePlaybackState");

                if (isSwitchingToNewVideo) {
                    // 如果切换到新的视频，确保从0秒开始播放
                    Log.d("MediaPlayer", "从0秒开始播放");
                    mediaPlayer.seekTo(0);
                } else {
                    // 恢复播放进度
                    Log.d("MediaPlayer", "恢复播放进度");
                    mediaPlayer.seekTo((int) currentPosition);
                }

                if (isVideoPlaying && !isSwitchingToNewVideo) {
                    // 如果之前在播放，且不是切换到新视频，则继续播放
                    Log.d("MediaPlayer", "之前在播放，且不是切换到新视频，则继续播放");
                    mediaPlayer.start();
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }


    // 获取文件的绝对路径
    private String getRealPathFromURI(Uri uri) {
        String path = null;
        if (uri.getScheme().equals("content")) {
            // 如果URI是content类型，尝试使用ContentResolver获取文件路径
            String[] projection = {MediaStore.Video.Media.DATA};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                path = cursor.getString(columnIndex);
                cursor.close();
            }
        } else if (uri.getScheme().equals("file")) {
            // 如果URI是file类型，直接获取文件路径
            path = uri.getPath();
        }
        // 如果路径为空，尝试使用ContentResolver直接获取文件流
        if (path == null) {
            path = uri.toString();
        }
        return path;
    }

    // 设置音频设备
    private void setAudioDevice(AudioDeviceInfo selectedDevice){
        if (mediaPlayer == null || selectedDevice == null) return;
        boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
        if (success) {
            isAudioDeviceSet = true;  // 设置成功，标志位为 true
            Log.d("AudioDevice", "已设置音频输出为: " + getDeviceTypeName(selectedDevice.getType()));
            applyMainSyncParams(mediaPlayer, "main-route-updated");
        } else {
            isAudioDeviceSet = false;  // 设置失败，标志位为 false
            Log.d("AudioDevice", "设置设备失败");
        }
    }

    private void applyMainSyncParams(MediaPlayer player, String reason) {
        if (player == null || mainSyncParamsUnsupported) return;
        try {
            SyncParams syncParams = new SyncParams()
                    .allowDefaults()
                    .setSyncSource(SyncParams.SYNC_SOURCE_SYSTEM_CLOCK)
                    .setAudioAdjustMode(SyncParams.AUDIO_ADJUST_MODE_RESAMPLE);
            player.setSyncParams(syncParams);
            Log.d("MediaPlayer", "主屏 SyncParams 应用成功: " + reason);
        } catch (IllegalArgumentException e) {
            mainSyncParamsUnsupported = true;
            Log.w("MediaPlayer", "主屏设备不支持 SyncParams，后续自动跳过", e);
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "主屏 SyncParams 应用失败: " + reason, e);
        }
    }

    private void scrollToInlinePreview() {
        if (scrollView == null || surfaceContainer == null) return;
        scrollView.post(() -> {
            int targetY = Math.max(0, surfaceContainer.getTop() - dpToPx(12));
            scrollView.scrollTo(0, targetY);
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void scheduleMainSyncNudge(String reason) {
        uiHandler.postDelayed(() -> applyMainSyncParams(mediaPlayer, reason), 120L);
    }

    private void maybePrimePresentationOutput(String reason) {
        if (presentation == null || presentation.mediaPlayer == null) return;
        if (presentation.mediaPlayer.isPlaying()) return;
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d("AudioDevice", "主屏正在播放，跳过副屏预热: " + reason);
            return;
        }
        boolean primed = presentation.primeOutputForSmoothStart();
        Log.d("AudioDevice", "副屏预热请求(" + reason + "): " + primed);
    }


    //监听副屏连接变化
    private void setupDisplayListener() {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        // 创建 DisplayListener 监听副屏的插拔
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.d("AudioDevice", "Display added: " + displayId);
                Display  display = displayManager.getDisplay(displayId);
                if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    refreshAudioDeviceList();
                }
            }
            @Override
            public void onDisplayChanged(int displayId) {
                Log.d("AudioDevice", "Display changed: " + displayId);
                // Avoid refreshing adapters here; frequent display config callbacks
                // can trigger unnecessary UI churn and accidental audio re-routing.
            }
            @Override
            public void onDisplayRemoved(int displayId) {
                if (presentation != null && presentation.getDisplay().getDisplayId() == displayId) {
                    Log.d("AudioDevice", "Display removed: " + displayId);
                    if (presentation.surface != null ) {
                        if (presentation.mediaPlayer != null && presentation.mediaPlayer.isPlaying()) {
                            presentation.mediaPlayer.pause();
                        }
                        presentation.releaseMediaPlayer();
                    }
                    presentation.dismiss();
                    presentation = null;
                }
                refreshAudioDeviceList();
            }
        }, null);
    }
    //初始化presentation
    private void initializePresentation(int displayId) {
        Log.d("Display", "Initializing presentation for display ID: " + displayId);

        if (allDisplays != null && allDisplays.length > 0) {
            for (Display display : allDisplays) {
                if (display.getDisplayId() == 0) continue;

                if (display.getDisplayId() == displayId) {
                    if (!presentationMap.containsKey(displayId)) {
                        MyPresentation newPresentation = new MyPresentation(this, display);
                        presentationMap.put(displayId, newPresentation);
                        newPresentation.show();
                    }
                    presentation = presentationMap.get(displayId);
                    applyPresentationPlayerSettings();
                    return;
                }
            }
        }
    }

    // 根据设备类型返回可读的设备名称
    public String getDeviceTypeName(int deviceType) {
        switch (deviceType) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return AudioSystem.DEVICE_OUT_SPEAKER_NAME;
            case AudioDeviceInfo.TYPE_HDMI:
                return AudioSystem.DEVICE_OUT_HDMI_NAME;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "蓝牙音频设备";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "有线耳机";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "有线头戴耳机";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "USB 音频设备";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "HDMI ARC";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "麦克风";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "蓝牙语音设备";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return AudioSystem.DEVICE_OUT_SPDIF_NAME;
            default:
                return "未知设备类型";
        }
    }

    private AudioDeviceInfo resolveDefaultDevice(List<AudioDeviceInfo> devices, AudioDeviceInfo preferred) {
        if (devices == null || devices.isEmpty()) return null;
        if (preferred != null) {
            for (AudioDeviceInfo info : devices) {
                if (info.getId() == preferred.getId()) {
                    return info;
                }
            }
        }
        return devices.get(0);
    }

    private int findDeviceIndex(List<AudioDeviceInfo> devices, AudioDeviceInfo target) {
        if (devices == null || target == null) return -1;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId() == target.getId()) {
                return i;
            }
        }
        return -1;
    }

    private int findDisplayIndex(List<DisplayItem> displays, int targetDisplayId) {
        if (displays == null || displays.isEmpty()) return -1;
        for (int i = 0; i < displays.size(); i++) {
            if (displays.get(i).displayId == targetDisplayId) {
                return i;
            }
        }
        return -1;
    }


    private void refreshAudioDeviceList() {
        deviceNames.clear();
        displayItems.clear();
        allDisplays = displayManager.getDisplays();
        for (Display display : allDisplays) {
            String displayName = "显示器 " + display.getDisplayId();
            int displayId = display.getDisplayId();
            boolean exists = false;
            for (DisplayItem item : displayItems) {
                if (item.displayId == displayId) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                displayItems.add(new DisplayItem(displayName, displayId));
            }
        }

        OutputDevices = Arrays.asList(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        updateDashboardStatus();
        AudioDeviceInfo defaultMainDevice = resolveDefaultDevice(OutputDevices, selectedDevice);
        AudioDeviceInfo preferredPresentationDevice =
                (presentation != null && presentation.selectedDevice != null)
                        ? presentation.selectedDevice
                        : selectedDeviceCache;
        AudioDeviceInfo defaultPresentationDevice = resolveDefaultDevice(OutputDevices, preferredPresentationDevice);

        selectedDevice = defaultMainDevice;
        selectedDeviceCache = defaultPresentationDevice;
        isAudioDeviceSet = (selectedDevice != null);
        device = (selectedDeviceCache != null) ? selectedDeviceCache : 0;

        if (presentation != null) {
            presentation.selectedDevice = selectedDeviceCache;
            presentation.isAudioDeviceSet = (selectedDeviceCache != null);
        }

        if (mediaPlayer != null && selectedDevice != null) {
            setAudioDevice(selectedDevice);
        }
        if (presentation != null && presentation.mediaPlayer != null && selectedDeviceCache != null) {
            presentation.setAudioDevice(selectedDeviceCache);
        }

        for (AudioDeviceInfo device : OutputDevices) {
            String deviceTypeName = getDeviceTypeName(device.getType());
            deviceNames.add(deviceTypeName);
        }
        ArrayAdapter<DisplayItem> displayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, displayItems);
        displayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        displaySpinner.setAdapter(displayAdapter);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, deviceNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        mAudioDevicesSpinner1.setAdapter(adapter);
        mAudioDevicesSpinner2.setAdapter(adapter);

        int mainIndex = findDeviceIndex(OutputDevices, selectedDevice);
        if (mainIndex >= 0) {
            mAudioDevicesSpinner1.setText(deviceNames.get(mainIndex), false);
        }
        int presentationIndex = findDeviceIndex(OutputDevices, selectedDeviceCache);
        if (presentationIndex >= 0) {
            mAudioDevicesSpinner2.setText(deviceNames.get(presentationIndex), false);
        }
        int displayIndex = findDisplayIndex(displayItems, selectedDisplayId);
        if (displayIndex < 0 && !displayItems.isEmpty()) {
            displayIndex = 0;
        }
        if (displayIndex >= 0) {
            DisplayItem selectedDisplay = displayItems.get(displayIndex);
            selectedDisplayId = selectedDisplay.displayId;
            displaySpinner.setText(selectedDisplay.toString(), false);
        }
        updateRouteSummaryTexts();

        mAudioDevicesSpinner1.setOnItemClickListener((parent, view, position, id) -> {
            suppressSystemUiSoundEffectsTemporarily();
            handleAudioDeviceSelection(position, false);
            updateRouteSummaryTexts();
        });

        mAudioDevicesSpinner2.setOnItemClickListener((parent, view, position, id) -> {
            suppressSystemUiSoundEffectsTemporarily();
            handleAudioDeviceSelection(position, true);
            updateRouteSummaryTexts();
        });

        displaySpinner.setOnItemClickListener((parent, view, position, id) -> {
            suppressSystemUiSoundEffectsTemporarily();
            if (position < 0 || position >= displayItems.size()) return;
            DisplayItem selectedDisplayItem = displayItems.get(position);
            selectedDisplayId = selectedDisplayItem.displayId;
            initializePresentation(selectedDisplayId);
            updateRouteSummaryTexts();
            if (device instanceof AudioDeviceInfo) {
                handleDeviceSelectionForPresentation(device);
            } else {
                Log.d("AudioDevice", "副屏音频设备尚未选择，跳过设置");
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startMainProgressUpdater();
        updateMainProgressUi();
        updateSubProgressUi();
        updateOverlayMainProgressUi();
        updateMainPlayVisuals();
        updateFullScreenIcon();
        applyMainOverlayUiScale(isFullScreen);
        showMainOverlayControls(true);
    }

    @Override
    public void onStop() {
        if (isFullScreen) {
            isFullScreen = false;
            applyInlineFullScreen(false);
        }
        uiHandler.removeCallbacks(mainProgressUpdater);
        uiHandler.removeCallbacks(hideMainOverlayRunnable);
        uiHandler.removeCallbacks(hideCenterPlayStateRunnable);
        uiHandler.removeCallbacks(applyRouteSwitchQueueRunnable);
        pendingMainRouteDevice = null;
        pendingPresentationRouteDevice = null;
        routeSwitchInProgress = false;
        uiHandler.removeCallbacks(restoreSystemStreamRunnable);
        restoreSystemUiSoundEffects();
        super.onStop();
        if (fullScreenDialog != null && fullScreenDialog.isShowing()) {
            fullScreenDialog.dismiss();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
