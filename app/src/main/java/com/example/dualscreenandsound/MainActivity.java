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
import android.media.SyncParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private AudioManager mAudioManager;
    private MyPresentation presentation;  // 用来保存对 Presentation 的引用
    private boolean isVideoPlaying= false;  // 用来追踪视频是否正在播放
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private FrameLayout surfaceContainer;
    private ScrollView scrollView;
    private Button btnFullScreen, btnMainPlay;
    private List<AudioDeviceInfo> OutputDevices;
    private Spinner mAudioDevicesSpinner1,mAudioDevicesSpinner2, displaySpinner;
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
    private boolean inlineOriginalPaddingCaptured = false;
    private int inlineOriginalPaddingLeft = 0;
    private int inlineOriginalPaddingTop = 0;
    private int inlineOriginalPaddingRight = 0;
    private int inlineOriginalPaddingBottom = 0;
    private int inlineOriginalControlsLayoutHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
    private boolean wasPlayingBeforeFullScreen = false;
    private boolean mainAudioSpinnerTouched = false;
    private boolean presentationAudioSpinnerTouched = false;
    private boolean displaySpinnerTouched = false;
    private long mainAudioSpinnerTouchTs = 0L;
    private long presentationAudioSpinnerTouchTs = 0L;
    private long displaySpinnerTouchTs = 0L;
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
        mAudioDevicesSpinner1.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                suppressSystemUiSoundEffectsTemporarily();
                mainAudioSpinnerTouched = true;
                mainAudioSpinnerTouchTs = SystemClock.uptimeMillis();
            }
            return false;
        });
        mAudioDevicesSpinner2.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                suppressSystemUiSoundEffectsTemporarily();
                presentationAudioSpinnerTouched = true;
                presentationAudioSpinnerTouchTs = SystemClock.uptimeMillis();
            }
            return false;
        });
        displaySpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                suppressSystemUiSoundEffectsTemporarily();
                displaySpinnerTouched = true;
                displaySpinnerTouchTs = SystemClock.uptimeMillis();
            }
            return false;
        });
        refreshAudioDeviceList();

        /********************
         添加主副屏视频播放的按钮
         *********************/
        Button btn1 = btnMainPlay;
        Button btn2 = findViewById(R.id.btn_presentation_displaymanager);
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
        Button selectPrensentionFileButton = findViewById(R.id.btn_presentation_selectfile);
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
                mAudioDevicesSpinner1,
                mAudioDevicesSpinner2,
                displaySpinner
        );
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
        ViewGroup.LayoutParams params = surfaceContainer.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) params;

        if (enable) {
            if (inlineOriginalSurfaceHeight < 0) {
                inlineOriginalSurfaceHeight = lp.height;
                inlineOriginalSurfaceTopMargin = lp.topMargin;
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
            surfaceContainer.setLayoutParams(lp);
            btnFullScreen.setText("退出全屏");
            applyImmersiveMode(getWindow());
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
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
            btnFullScreen.setText("全屏");
            clearImmersiveMode();
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
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
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
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
                    buttonText.setText("播放");
                } else {
                    mediaPlayer.start();
                    scheduleMainSyncNudge("main-manual-start");
                    buttonText.setText("暂停");
                }
            } else {
                Toast.makeText(getApplicationContext(), "请选择音频通道", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "请选择媒体文件", Toast.LENGTH_SHORT).show();
        }
    }

    // 通用方法：设置音频设备并恢复播放状态
    private void handleAudioDeviceSelection(AdapterView<?> parentView, View selectedItemView, int position, long id, boolean isPresentation) {
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
        if (player.isPlaying()) {
            player.pause();
            buttonText.setText("播放");
            return;
        }

        boolean mainWasPlaying = mediaPlayer != null && mediaPlayer.isPlaying();
        try {
            player.start();
            buttonText.setText("暂停");
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
                // 恢复播放状态
                Log.d("MediaPlayer", "准备恢复restorePlaybackState");
                restorePlaybackState();

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
        if (player == null) return;
        try {
            SyncParams syncParams = new SyncParams()
                    .allowDefaults()
                    .setSyncSource(SyncParams.SYNC_SOURCE_SYSTEM_CLOCK)
                    .setAudioAdjustMode(SyncParams.AUDIO_ADJUST_MODE_RESAMPLE);
            player.setSyncParams(syncParams);
            Log.d("MediaPlayer", "主屏 SyncParams 应用成功: " + reason);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w("MediaPlayer", "主屏 SyncParams 应用失败: " + reason, e);
        }
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


    private void refreshAudioDeviceList() {
        // 清空“用户触发”标志，防止 setAdapter 触发的程序性回调误执行重路由
        mainAudioSpinnerTouched = false;
        presentationAudioSpinnerTouched = false;
        displaySpinnerTouched = false;
        mainAudioSpinnerTouchTs = 0L;
        presentationAudioSpinnerTouchTs = 0L;
        displaySpinnerTouchTs = 0L;
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

        displaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (!displaySpinnerTouched || (SystemClock.uptimeMillis() - displaySpinnerTouchTs) > 2000L) {
                    displaySpinnerTouched = false;
                    displaySpinnerTouchTs = 0L;
                    return;
                }
                displaySpinnerTouched = false;
                displaySpinnerTouchTs = 0L;
                if (position >= 0) {
                    DisplayItem selectedDisplayItem = displayItems.get(position);
                    selectedDisplayId = selectedDisplayItem.displayId;
                    initializePresentation(selectedDisplayId);
                    if (device instanceof AudioDeviceInfo) {
                        handleDeviceSelectionForPresentation(device);
                    } else {
                        Log.d("AudioDevice", "副屏音频设备尚未选择，跳过设置");
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        OutputDevices = Arrays.asList(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
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
        ArrayAdapter<DisplayItem> displayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayItems);
        displayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        displaySpinner.setAdapter(displayAdapter);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mAudioDevicesSpinner1.setAdapter(adapter);
        mAudioDevicesSpinner2.setAdapter(adapter);
        int mainIndex = findDeviceIndex(OutputDevices, selectedDevice);
        if (mainIndex >= 0) {
            mAudioDevicesSpinner1.setSelection(mainIndex, false);
        }
        int presentationIndex = findDeviceIndex(OutputDevices, selectedDeviceCache);
        if (presentationIndex >= 0) {
            mAudioDevicesSpinner2.setSelection(presentationIndex, false);
        }

        mAudioDevicesSpinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (!mainAudioSpinnerTouched || (SystemClock.uptimeMillis() - mainAudioSpinnerTouchTs) > 2000L) {
                    mainAudioSpinnerTouched = false;
                    mainAudioSpinnerTouchTs = 0L;
                    return;
                }
                mainAudioSpinnerTouched = false;
                mainAudioSpinnerTouchTs = 0L;
                handleAudioDeviceSelection(parentView, selectedItemView, position, id, false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        mAudioDevicesSpinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (!presentationAudioSpinnerTouched || (SystemClock.uptimeMillis() - presentationAudioSpinnerTouchTs) > 2000L) {
                    presentationAudioSpinnerTouched = false;
                    presentationAudioSpinnerTouchTs = 0L;
                    return;
                }
                presentationAudioSpinnerTouched = false;
                presentationAudioSpinnerTouchTs = 0L;
                handleAudioDeviceSelection(parentView, selectedItemView, position, id, true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
    }
    @Override
    public void onStop() {
        if (isFullScreen) {
            isFullScreen = false;
            applyInlineFullScreen(false);
        }
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
