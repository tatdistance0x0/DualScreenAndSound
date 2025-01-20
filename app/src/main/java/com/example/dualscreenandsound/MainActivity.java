package com.example.dualscreenandsound;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
    private List<AudioDeviceInfo> OutputDevices;
    private Spinner mAudioDevicesSpinner1,mAudioDevicesSpinner2, displaySpinner;;
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
//        initializePresentation();

        surfaceView = findViewById(R.id.surfaceView);  // SurfaceView 用于显示视频
        surface = surfaceView.getHolder().getSurface();
        surfaceHolder = surfaceView.getHolder();
        // SurfaceHolder.Callback 用来处理 Surface 创建和销毁
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d("MediaPlayer", "准备初始化MediaPlayer或者presentation.MediaPlayer");
                // Surface 创建后，可以初始化播放器
                if (selectedUri != null) {
                    initMediaPlayer(selectedUri, holder.getSurface());
                    setAudioDevice(selectedDevice);
                }
                if (presentation != null) {
                    if (presentionselectedUri != null) {
                        presentation.initMediaPlayer(presentionselectedUri,presentation.surface);  // 直接传递Uri给MediaPlayer
                    } else {
                        Log.d("MediaPlayer", "请选择副屏文件");
                    }
                    if (selectedDeviceCache != null) {
                        presentation.setAudioDevice(selectedDeviceCache);
                    } else {
                        Log.d("MediaPlayer", "selectedDeviceCacheh is null");
                    }
                }else{
                    Log.d("MediaPlayer", "请连接副屏");
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 处理Surface变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("MediaPlayer", "准备获取当前播放位置");
                // 在Surface销毁时保存当前播放状态
                savePlaybackState(); // 保存MediaPlayer播放状态
                presentation.savePlaybackState();

                Log.d("MediaPlayer", "准备释放releaseMediaPlayer");
                // 在 Surface 销毁时释放播放器资源
                if (mediaPlayer != null ) {
                    releaseMediaPlayer(); // 释放资源
                }
                // 增加对 presentation 是否为 null 的检查
                if (presentation != null && presentation.mediaPlayer != null) {
                    presentation.releaseMediaPlayer(); // 释放 presentation 的播放器资源
                }
            }
        });


        /********************
         添加音频路由设备的下拉列表
        *********************/
        mAudioDevicesSpinner1 = findViewById(R.id.spinner_audio_devices);
        mAudioDevicesSpinner2 = findViewById(R.id.spinner_audio_presentation_devices);
        displaySpinner = findViewById(R.id.displaySpinner);
        refreshAudioDeviceList();

        /********************
         添加主副屏视频播放的按钮
         *********************/
        Button btn1 = findViewById(R.id.btn_displaymanager);
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
                    // presentation 不为空时调用通用播放控制方法
                    presentation.isAudioDeviceSet = true;
                    toggleMediaPlayer(presentation.mediaPlayer, presentation.isAudioDeviceSet, btn2);
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

        // 确保 presentation.selectFileLauncher 被初始化

        if (selectFileLauncherCache == null) {
            selectFileLauncherCache = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> handleFileResult(result, true));
            Log.d("AudioDevice", "注册presentation.selectFileLauncher");
        }else{
            Log.d("AudioDevice", "presentation.selectFileLauncher为空");
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
        presentation.selectedDevice = (AudioDeviceInfo) device;
        Log.d("AudioDevice", "选中的设备信息：");
        Log.d("AudioDevice", "设备ID: " + selectedDevice.getId());
        Log.d("AudioDevice", "设备名称: " + selectedDevice.getProductName());
        Log.d("AudioDevice", "设备类型: " + selectedDevice.getType());
        if (presentation.selectedDevice != null) {
            selectedDeviceCache = presentation.selectedDevice;
        }else{
            Log.d("AudioDevice", "presentation.selectedDevice为空，所以没有传递给selectedDeviceCache");
        }

        if (selectedPresentionFilePath == null || selectedPresentionFilePath.isEmpty()) {
            Log.d("AudioDevice", "请选择文件");
            return;
        }
        //切换前最好暂停一下
        pauseMediaPlayerIfPlaying(presentation.mediaPlayer);  // 如果正在播放，暂停
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("AudioDevice", "presentation.setAudioDevice(selectedDeviceCache)");
                presentation. setAudioDevice(selectedDeviceCache);
//                resumeMediaPlayerIfPlaying(presentation.mediaPlayer);  // 恢复播放
            }
        }, 500);  // 延迟 0.5 秒
    }

    // 处理普通音频设备选择的方法
    private void handleDeviceSelectionForAudio(Object device) {
        selectedDevice = (AudioDeviceInfo) device;
        Log.d("AudioDevice", "handleDeviceSelectionForAudio 拿到的Selected Device: " + selectedDevice);
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            Log.d("AudioDevice", "请选择文件");
            return;
        }
        //切换前最好暂停一下
        pauseMediaPlayerIfPlaying(mediaPlayer);  // 如果正在播放，暂停
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                setAudioDevice(selectedDevice);
//                resumeMediaPlayerIfPlaying(mediaPlayer);  // 恢复播放
            }
        }, 500);  // 延迟 0.5 秒
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
                            // 如果是新文件，可以做一些额外操作，比如恢复播放进度等
                            isSwitchingToNewVideo = true;
                        } else {
                            Log.d("PresentationFilePath", "选择的是同一个视频文件");
                            isSwitchingToNewVideo = false;
                        }
                        Log.d("selectedPresentionFilePath", "文件路径为: " + selectedPresentionFilePath);
                        Toast.makeText(MainActivity.this, "文件已选择: " + selectedPresentionFilePath, Toast.LENGTH_SHORT).show();

                        // 更新副屏的 previousFilePath
                        previousPresentationFilePath = filePath;
                    } else {
                        selectedUri = toggleSelectedUri; // 保存主屏文件的 URI
                        selectedFilePath = filePath;

                        // 判断是否为新的视频文件
                        if (!filePath.equals(previousMainScreenFilePath)) {
                            Log.d("SelectedFilePath", "选择了新的视频文件: " + selectedFilePath);
                            // 如果是新文件，可以做一些额外操作，比如恢复播放进度等
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

    // 判断并暂停播放MediaPlayer的通用方法
    private void pauseMediaPlayerIfPlaying(MediaPlayer player) {
        if (player != null && player.isPlaying()) {
            player.pause();  // 暂停播放器
        }
    }

    // 恢复播放MediaPlayer的通用方法
    private void resumeMediaPlayerIfPlaying(MediaPlayer player) {
        if (player != null && !player.isPlaying()) {
            player.start();  // 恢复播放
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
            mediaPlayer.setSurface(surface);  // 绑定Surface
            Log.d("MediaPlayer", "绑定Surface");
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("MediaPlayer", "播放器准备完毕");
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
                // 处理可能的错误
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
        boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
        if (success) {
            isAudioDeviceSet = true;  // 设置成功，标志位为 true
            Log.d("AudioDevice", "已设置音频输出为: " + getDeviceTypeName(selectedDevice.getType()));
        } else {
            isAudioDeviceSet = false;  // 设置失败，标志位为 false
            Log.d("AudioDevice", "设置设备失败");
        }
    }


    //监听副屏连接变化
    private void setupDisplayListener() {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        // 创建 DisplayListener 监听副屏的插拔
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                // 副屏添加时重新初始化 presentation
                Log.d("AudioDevice", "Display added: " + displayId);
                Display  display = displayManager.getDisplay(displayId);
                if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    // 更新副屏列表并刷新 Spinner
                    refreshAudioDeviceList();
//                    initializePresentation();
                }

                // 刷新音频设备列表
                Log.d("AudioDevice", "onDisplayAdded刷新音频设备列表");
            }
            @Override
            public void onDisplayChanged(int displayId) {
                // 副屏发生变化时，可能需要更新 display 或者重新初始化 presentation
                Log.d("AudioDevice", "Display changed: " + displayId);
                 refreshAudioDeviceList();
            }
            @Override
            public void onDisplayRemoved(int displayId) {
                // 副屏移除时，隐藏或销毁 presentation
                if (presentation != null && presentation.getDisplay().getDisplayId() == displayId) {
                    Log.d("AudioDevice", "Display removed: " + displayId);
                    if (presentation.surface != null ) {
                        Log.d("MediaPlayer", "释放 surface");
                        if (presentation.mediaPlayer != null && presentation.mediaPlayer.isPlaying()) {
                            presentation.mediaPlayer.pause();
                            Log.d("MediaPlayer", "暂停 surface");
                        }
                        presentation.releaseMediaPlayer();
                    }
                    presentation.dismiss();
                    presentation = null;
                }
                // 刷新音频设备列表
                Log.d("AudioDevice", "onDisplayRemoved刷新音频设备列表");
                refreshAudioDeviceList();
            }
        }, null);
    }
    //初始化presentation
    private void initializePresentation(int displayId) {
        Log.d("Display", "Initializing presentation for display ID: " + displayId);

        if (allDisplays != null && allDisplays.length > 0) {
            for (Display display : allDisplays) {
                Log.d("Display", "Found display with ID: " + display.getDisplayId());

                // 跳过主屏（ID 为 0）
                if (display.getDisplayId() == 0) {
                    Log.d("AudioDevice", "Skipping main display with ID 0");
                    continue;
                }

                // 如果是目标副屏，则进行处理
                if (display.getDisplayId() == displayId) {
                    // 如果没有找到现有的 presentation，创建新的
                    if (!presentationMap.containsKey(displayId)) {
                        Log.d("AudioDevice", "Creating new presentation on display ID: " + displayId);
                        MyPresentation newPresentation = new MyPresentation(this, display);
                        presentationMap.put(displayId, newPresentation);
                        newPresentation.show();

                    }
                    presentation = presentationMap.get(selectedDisplayId);//将控制的实例与选择的副屏联系起来
                    Log.d("AudioDevice", "Current presentation set to display ID: " + displayId);
                    return; // 一旦找到目标副屏并处理完，退出循环
                }
            }
        } else {
            Log.d("AudioDevice", "displays = NULL");
        }
    }

    // 根据设备类型返回可读的设备名称
    public String getDeviceTypeName(int deviceType) {
        switch (deviceType) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return AudioSystem.DEVICE_OUT_SPEAKER_NAME;
            case AudioDeviceInfo.TYPE_HDMI:
                return AudioSystem.DEVICE_OUT_HDMI_NAME;
//            case AudioDeviceInfo.TYPE_HDMI_1:
//                return AudioSystem.DEVICE_OUT_HDMI1_NAME;
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
//            case AudioDeviceInfo.TYPE_LINE_DIGITAL_1:
//                return AudioSystem.DEVICE_OUT_SPDIF1_NAME;
            default:
                return "未知设备类型";
        }
    }


    private void refreshAudioDeviceList() {

        deviceNames.clear();
        displayItems.clear();
        // 获取所有显示设备的名称（包括主屏和副屏）
        allDisplays = displayManager.getDisplays();
        for (Display display : allDisplays) {
            String displayName = "显示器 " + display.getDisplayId(); // 根据需要自定义显示名称
            int displayId = display.getDisplayId();
            // 检查是否已存在显示器ID，避免重复添加
            boolean exists = false;
            for (DisplayItem item : displayItems) {
                if (item.displayId == displayId) {
                    exists = true;
                    break;
                }
            }
            // 如果没有重复的显示器ID，才添加
            if (!exists) {
                displayItems.add(new DisplayItem(displayName, displayId)); // 创建一个新的 DisplayItem 对象并添加到列表
            }
        }

        displaySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position >= 0) {
                    // 获取选中的 DisplayItem 对象
                    DisplayItem selectedDisplayItem = displayItems.get(position);
                    // 取得 displayId 并传递给 initializePresentation 方法
                    selectedDisplayId = selectedDisplayItem.displayId;
                    initializePresentation(selectedDisplayId);
                    handleDeviceSelectionForPresentation(device);
                }else {
                    Log.d("AudioDevice", " position <= 0 && position > presentations.size()");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

        // 获取所有音频输出设备
        OutputDevices = Arrays.asList(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        // 获取设备类型并转换为可显示的名称
        for (AudioDeviceInfo device : OutputDevices) {
            String deviceTypeName = getDeviceTypeName(device.getType());
            Log.d("AudioDevice", "Device ID: " + device.getId() + " Name: " + device.getProductName() + " Type: " + deviceTypeName);
            deviceNames.add(deviceTypeName);
        }
        // 刷新显示设备 Spinner 适配器
        ArrayAdapter<DisplayItem> displayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayItems);
        displayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        displaySpinner.setAdapter(displayAdapter);
        // 刷新 Spinner 适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 设置新适配器到 Spinner1 和 Spinner2
        mAudioDevicesSpinner1.setAdapter(adapter);
        mAudioDevicesSpinner2.setAdapter(adapter);

        // 重新设置 Spinner 的监听器（如果需要）
        mAudioDevicesSpinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                handleAudioDeviceSelection(parentView, selectedItemView, position, id, false);  // false 表示处理的是普通的 AudioDevice
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 如果没有选择任何设备，可以执行一些默认操作
            }
        });

        mAudioDevicesSpinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                handleAudioDeviceSelection(parentView, selectedItemView, position, id, true);  // true 表示处理的是 Presentation
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 如果没有选择任何设备，可以执行一些默认操作
            }
        });


    }
    @Override
    public void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.release();  // 释放资源
            mediaPlayer = null;
        }
    }
}