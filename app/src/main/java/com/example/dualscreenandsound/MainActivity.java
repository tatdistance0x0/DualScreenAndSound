package com.example.dualscreenandsound;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
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
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private AudioManager mAudioManager;
    private MyPresentation presentation;  // 用来保存对 Presentation 的引用
    private boolean isVideoPlaying = false;  // 用来追踪视频是否正在播放
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private List<AudioDeviceInfo> OutputDevices;
    private Spinner mAudioDevicesSpinner1,mAudioDevicesSpinner2;
    private String selectedFilePath = "";  // 用于保存选择的文件路径
    private String selectedPresentionFilePath = "";  // 用于保存选择的文件路径
    private boolean isAudioDeviceSet = false;
    private ActivityResultLauncher<Intent> selectFileLauncher, selectPresentationFileLauncher;  // 声明 ActivityResultLauncher
    private Uri selectedUri, presentionselectedUri;
    private boolean wasPresentationPlaying,wasMediaPlayerPlaying = false;
    private AudioDeviceInfo selectedDevice,selectedPresentationDevice;
    private boolean isSelectingPresentation = false;
    private long currentPosition = 0;  // 用来保存当前播放的位置
    private SurfaceHolder surfaceHolder;
    private Surface surface;
    private boolean shouldRestorePlaybackState = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        initializePresentation();
        surfaceView = findViewById(R.id.surfaceView);  // SurfaceView 用于显示视频
        surface = surfaceView.getHolder().getSurface();
        surfaceHolder = surfaceView.getHolder();
        // SurfaceHolder.Callback 用来处理 Surface 创建和销毁
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                // Surface 创建后，可以初始化播放器
                if (selectedUri != null) {
                    initMediaPlayer(selectedUri, holder.getSurface());
                    try {
                        setAudioDevice(selectedDevice);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 处理Surface变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (shouldRestorePlaybackState) {
                    Log.d("MediaPlayer", "准备获取当前播放位置");
                    // 在Surface销毁时保存当前播放状态
                    savePlaybackState();
                }

                // 在 Surface 销毁时释放播放器资源
                releaseMediaPlayer();
            }
        });


        /********************
         添加音频路由设备的下拉列表
        *********************/
        mAudioDevicesSpinner1 = findViewById(R.id.spinner_audio_devices);
        mAudioDevicesSpinner2 = findViewById(R.id.spinner_audio_presentation_devices);

        // 获取所有音频输出设备
        OutputDevices = Arrays.asList(mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        // 获取设备类型并转换为可显示的名称
        List<String> deviceNames = new ArrayList<>();
        for (AudioDeviceInfo device : OutputDevices) {
            String deviceTypeName = getDeviceTypeName(device.getType());
            deviceNames.add(deviceTypeName);
        }

        /********************
         添加主副屏音频通道切换下拉列表
         *********************/
        // 设置 Spinner 的适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAudioDevicesSpinner1.setAdapter(adapter);
        mAudioDevicesSpinner2.setAdapter(adapter);

        // 监听 Spinner 的选择事件
        mAudioDevicesSpinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedDevice = OutputDevices.get(position);

                if (selectedFilePath != null && !selectedFilePath.isEmpty()) {
                    // 判断 mediaPlayer 是否在播放
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        wasMediaPlayerPlaying = true;  // 记录原来的播放状态
                        mediaPlayer.pause();  // 如果正在播放，暂停
                    }
                    // 设置音频设备
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                setAudioDevice(selectedDevice);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            if (mediaPlayer != null) {
                                if (wasMediaPlayerPlaying) {
                                    // 如果之前是播放状态，恢复播放
                                    mediaPlayer.start();
                                }
                                // 如果之前是暂停状态，保持暂停状态
                            }
                        }
                    }, 500);  // 延迟 0.5 秒 (500 毫秒)

                } else {
                    // 如果没有选择文件，做一些其他操作或提示用户选择文件
                    Log.d("AudioDevice", "请选择文件");
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 如果没有选择任何设备，可以执行一些默认操作
            }
        });

        // 监听 Spinner 的选择事件
        mAudioDevicesSpinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                selectedPresentationDevice = OutputDevices.get(position);

                if (selectedPresentionFilePath != null && !selectedPresentionFilePath.isEmpty()) {

                    // 判断 mediaPlayer 是否在播放
                    if (presentation.mediaPlayer != null && presentation.mediaPlayer.isPlaying()) {
                        wasPresentationPlaying = true;  // 记录原来的播放状态
                        presentation.mediaPlayer.pause();  // 如果正在播放，暂停
                    }
                    // 使用 Handler 来延迟恢复播放
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                showSecondByDisplayManager(selectedPresentationDevice);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            // 设置完设备后，恢复原来的播放状态
                            if (presentation.mediaPlayer != null) {
                                if (wasPresentationPlaying) {
                                    // 如果之前是播放状态，恢复播放
                                    presentation.mediaPlayer.setDisplay(presentation.surfaceView.getHolder());  // 设置显示 SurfaceView
                                    presentation.mediaPlayer.start();
                                }
                                // 如果之前已经暂停，保持暂停状态，不做处理
                            }
                        }
                    }, 500);  // 延迟 0.5 秒 (500 毫秒)


                } else {
                    // 如果没有选择文件，做一些其他操作或提示用户选择文件
                    Log.d("AudioDevice", "请选择文件");
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 如果没有选择任何设备，可以执行一些默认操作
            }
        });



        /********************
         添加主副屏视频播放的按钮
         *********************/
        Button btn1 = findViewById(R.id.btn_displaymanager);
        Button btn2 = findViewById(R.id.btn_presentation_displaymanager);
        // 按钮点击事件

        // 按钮点击事件
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayer != null) {  // 检查 mediaPlayer 是否为 null
                    if (isAudioDeviceSet) {
                            if (mediaPlayer.isPlaying()) {
                                // 如果正在播放，则暂停
                                mediaPlayer.pause();
                                btn1.setText("播放");
                                wasMediaPlayerPlaying = false;  // 记录原来的播放状态
                            } else {
                                // 如果没有播放，则开始播放
                                mediaPlayer.setDisplay(surfaceView.getHolder());  // 设置显示 SurfaceView
                                mediaPlayer.start();
                                btn1.setText("暂停");
                            }
                        }else{
                            Toast.makeText(getApplicationContext(), "请选择音频通道", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // 如果 mediaPlayer 为 null，则提示或创建 mediaPlayer
                        Toast.makeText(getApplicationContext(), "请选择媒体文件", Toast.LENGTH_SHORT).show();
                    }
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 检查 presentation 和 mediaPlayer 是否都已初始化
                if (presentation.mediaPlayer != null) {
                    // 如果 presentation 已经初始化
                    Log.d("MainActivity", "presentation is initialized.");
                    if (presentation.isPrensentationAudioDeviceSet) {
                        if (presentation.mediaPlayer.isPlaying()) {
                            // 如果正在播放，则暂停
                            presentation.mediaPlayer.pause();
                            btn2.setText("播放");
                            wasPresentationPlaying = false;  // 记录原来的播放状态
                        } else {
                            // 如果没有播放，则开始播放
                            presentation.mediaPlayer.setDisplay(presentation.surfaceView.getHolder());  // 设置显示 SurfaceView
                            presentation.mediaPlayer.start();
                            btn2.setText("暂停");
                        }
                    } else {
                        // 如果 mediaPlayer 为 null，提示用户
                        Toast.makeText(getApplicationContext(), "请选择音频通道", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // 如果 presentation 为 null，提示用户
                    Toast.makeText(getApplicationContext(), "请选择媒体文件", Toast.LENGTH_SHORT).show();
                }
            }
        });
        /********************
         添加主副屏视频文件选择按钮
         *********************/
        Button selectFileButton = findViewById(R.id.btn_selectfile);
        Button selectPrensentionFileButton = findViewById(R.id.btn_presentation_selectfile);
//         创建一个 ActivityResultLauncher 来替代 startActivityForResult
        selectFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d("ActivityResult", "选择文件回调触发");
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            selectedUri = data.getData();
                            String filePath = getRealPathFromURI(selectedUri);
                            if (filePath != null) {
                                selectedFilePath = filePath;
                                Log.d("SelectedFilePath", "文件路径为: " + selectedFilePath);
                                Toast.makeText(MainActivity.this, "文件已选择: " + selectedFilePath, Toast.LENGTH_SHORT).show();
//                                initMediaPlayer(selectedUri,  surfaceHolder.getSurface());  // 直接传递Uri给MediaPlayer
//                                try {
//                                    setAudioDevice(selectedDevice);
//                                } catch (IOException e) {
//                                    throw new RuntimeException(e);
//                                }
                            } else {
                                Log.d("SelectedFilePath", "无法获取文件路径");
                            }
                        }
                    } else {
                        Log.d("ActivityResult", "文件选择未成功");
                    }
                });
// 创建一个 ActivityResultLauncher 来替代 startActivityForResult
        selectPresentationFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d("ActivityResult", "111选择文件回调触发");
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            presentionselectedUri = data.getData();
                            String presentionfilePath = getRealPathFromURI(presentionselectedUri);
                            if (presentionfilePath != null) {
                                selectedPresentionFilePath = presentionfilePath;
                                Log.d("selectedPresentionFilePath", "111文件路径为: " + selectedPresentionFilePath);
                                Toast.makeText(MainActivity.this, "文件已选择: " + selectedPresentionFilePath, Toast.LENGTH_SHORT).show();

                                presentation.initPresentionMediaPlayer(presentionselectedUri);  // 直接传递Uri给MediaPlayer
//                                initMediaPlayer(selectedUri);  // 直接传递Uri给MediaPlayer
                                Log.d("MediaPlayer", "主屏 MediaPlayer 初始化：" + (mediaPlayer == null ? "空" : "已初始化"));
                                try {
                                    showSecondByDisplayManager(selectedPresentationDevice);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                            } else {
                                Log.d("selectedPresentionFilePath", "无法获取文件路径");
                            }
                        }
                    } else {
                        Log.d("ActivityResult", "文件选择未成功");
                    }
                });

        // 按钮点击事件，打开文件选择器
        selectFileButton.setOnClickListener(v ->{
            shouldRestorePlaybackState = false;
            openFileChooser();
        });
        selectPrensentionFileButton.setOnClickListener(v -> {
            shouldRestorePlaybackState = true;
            openPresentationFileChooser();
        });

    }

    // 初始化 MediaPlayer
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
            mediaPlayer.setSurface(surface);  // 绑定Surface
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("MediaPlayer", "播放器准备完毕");
                // 恢复播放状态
                if (shouldRestorePlaybackState) {
                    Log.d("MediaPlayer", "准备恢复restorePlaybackState");
                    restorePlaybackState();
                }
            });
            mediaPlayer.prepareAsync(); // 异步准备播放
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            Log.d("MediaPlayer", "MediaPlayer 被释放");
        }
    }
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

    private void restorePlaybackState() {
        if (mediaPlayer != null) {
            try {
                Log.d("MediaPlayer", "尝试恢复restorePlaybackState");
                mediaPlayer.seekTo((int) currentPosition);  // 恢复播放进度
                if (isVideoPlaying) {
                    mediaPlayer.start();  // 恢复播放
                }
            } catch (IllegalStateException e) {
                // 处理可能的错误
                e.printStackTrace();
            }
        }
    }
    // 打开文件选择器
    private void openFileChooser() {
        Log.d("ActivityResult", "正在打开文件选择器");
        // 检查是否有文件读取权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {

            // 启动文件选择器（选择音频或视频）
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");  // 可以选择所有类型的文件，或者修改为"video/*"或"audio/*"
            selectFileLauncher.launch(intent);  // 使用新的方式启动文件选择器
        }
    }

    private void openPresentationFileChooser() {
        Log.d("ActivityResult", "正在打开文件选择器");
        // 检查是否有文件读取权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            // 启动文件选择器（选择音频或视频）
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");  // 可以选择所有类型的文件，或者修改为"video/*"或"audio/*"
            selectPresentationFileLauncher.launch(intent);  // 使用新的方式启动文件选择器
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


    // 根据设备类型返回可读的设备名称
    public String getDeviceTypeName(int deviceType) {
        switch (deviceType) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "扬声器";
            case AudioDeviceInfo.TYPE_HDMI:
                return "HDMI";
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
            default:
                return "未知设备类型";
        }
    }
    // 设置音频设备
    private void setAudioDevice(AudioDeviceInfo selectedDevice) throws IOException {
        boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
        if (success) {
            isAudioDeviceSet = true;  // 设置成功，标志位为 true
            Log.d("AudioDevice", "已设置音频输出为: " + getDeviceTypeName(selectedDevice.getType()));
        } else {
            isAudioDeviceSet = false;  // 设置失败，标志位为 false
            Log.d("AudioDevice", "设置设备失败");
        }
    }

    protected void onPause() {
        super.onPause();
        // 暂停视频播放
        if (mediaPlayer != null) {
//            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复视频播放
        if (mediaPlayer != null) {
//            mediaPlayer.start();
        }
    }

//
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1001;
    private void showSecondByDisplayManager(AudioDeviceInfo selectedPresentationDevice) throws IOException {
        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
        }else {
            // 权限已授予，继续执行展示视频
            displayVideoOnSecondScreen(selectedPresentationDevice);
        }
    }

    private void initializePresentation() {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

        if (displays != null && displays.length > 0) {
            Display display = displays[displays.length - 1];  // 选择最后一个显示设备
            // 如果已经有 presentation，则更新其内容
            // 如果没有 presentation，创建新的 presentation 对象
            if (presentation != null) {
                Log.d("AudioDevice", "Updated existing presentation on second screen.");
            } else {
                // 如果没有 presentation，创建新的 presentation 对象
                presentation = new MyPresentation(this, display);
                presentation.show();
                Log.d("AudioDevice", "Created new presentation on second screen.");
            }
        }else{
            Log.d("AudioDevice", "displays = NULL");
        }

    }
    private void displayVideoOnSecondScreen(AudioDeviceInfo selectedPresentationDevice) {
        presentation.setVideoPathAndAudioDevice(selectedPresentationDevice);  // 设置视频路径和音频设
    }


}