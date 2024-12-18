package com.example.dualscreenandsound;

import android.Manifest;
import android.app.Presentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private AudioManager mAudioManager;
    private MyPresentation presentation;  // 用来保存对 Presentation 的引用
    private boolean isVideoPlaying = false;  // 用来追踪视频是否正在播放
    private MediaPlayer mediaPlayer;
    private AudioDeviceInfo[] mOutputDevices;
//    private VideoView videoView;  // 使用 VideoView
    private SurfaceView surfaceView;
    private Surface surface;
    private AudioDeviceInfo speakerDevice;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mediaPlayer = new MediaPlayer();
//        videoView = findViewById(R.id.videoView);  // 确保布局中有 VideoView
        surfaceView = findViewById(R.id.surfaceView);  // SurfaceView 用于显示视频
        surface = surfaceView.getHolder().getSurface();
        Button btn2 = findViewById(R.id.btn_presentation_displaymanager);
        Button btn3 = findViewById(R.id.btn_set_audio_device_type);
        Button btn4 = findViewById(R.id.btn_set_presentation_audio_device_type);
        // 按钮点击事件
        Button playButton = findViewById(R.id.playButton);
        // 按钮点击事件
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 检查权限并执行播放或暂停操作
                if (checkPermissions()) {
                    if (mediaPlayer.isPlaying()) {
                        // 如果正在播放，则暂停
                        mediaPlayer.pause();
                        playButton.setText("播放");
                    } else {
                        // 如果没有播放，则开始播放
                        mediaPlayer.setDisplay(surfaceView.getHolder());  // 设置显示 SurfaceView
                        mediaPlayer.start();
                        //                        mediaPlayer.start();
                        playButton.setText("暂停");

                    }
                } else {
                    requestPermissions();
                }
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 如果 presentation 已经初始化
                if (presentation != null) {
                    // 切换播放/暂停状态
                    Log.d("MainActivity", "presentation");
                    if (isVideoPlaying) {
                        presentation.pauseVideo();  // 暂停视频
                    } else {
                        presentation.playVideo();   // 播放视频
                    }
                    isVideoPlaying = !isVideoPlaying;  // 切换播放/暂停状态
                }else{
                    Log.e("MainActivity", "presentation is null. Cannot control video playback.");
                }

            }
        });

        btn3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    setAudioDevice();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        });

        btn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    showSecondByDisplayManager(MainActivity.this);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });
    }

    // 检查是否拥有读取存储的权限
    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    // 请求权限
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
    }

    // 设置音频设备
    private void setAudioDevice() throws IOException {
        // 获取所有的输出设备
        mOutputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        mediaPlayer.setDataSource("/sdcard/Music/Melody.mp4");  // 设置视频路径
        for (AudioDeviceInfo device : mOutputDevices) {
            if (device.getType() == AudioDeviceInfo.TYPE_HDMI) {
                boolean success = mediaPlayer.setPreferredDevice(device);
                if (success) {
                    Log.d("AudioDevice", "已设置音频输出为耳机: " + device.getProductName());
                } else {
                    Log.d("AudioDevice", "设置耳机设备失败");
                }
            }
        }
        // 准备和开始播放
        mediaPlayer.prepare();  // 异步准备
    }

    // 释放 MediaPlayer 资源
    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            switch (requestCode) {
//                case REQUEST_CODE_STORAGE_PERMISSION:
//                    // 权限被授予，继续执行展示视频
//                    try {
//                        displayVideoOnSecondScreen(this);
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    break;
//
//                case PERMISSION_REQUEST_CODE:
//                    // 处理另一个权限请求，设置音频设备并播放视频
//                    try {
//                        playVideo();
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                    break;
//
//                // 如果有其他权限请求，可以继续添加其他 case
//                default:
//                    break;
//            }
//        } else {
//            // 权限被拒绝
//            switch (requestCode) {
//                case REQUEST_CODE_STORAGE_PERMISSION:
//                    Toast.makeText(this, "Storage permission is required to play video.", Toast.LENGTH_SHORT).show();
//                    break;
//
//                case PERMISSION_REQUEST_CODE:
//                    Toast.makeText(this, "存储权限被拒绝，无法播放视频", Toast.LENGTH_SHORT).show();
//                    break;
//
//                default:
//                    break;
//            }
//        }
//    }
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1001;
    private void showSecondByDisplayManager(Context context) throws IOException {
        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
        }else {
            // 权限已授予，继续执行展示视频
            displayVideoOnSecondScreen(context);
        }
    }
    private void displayVideoOnSecondScreen(Context context) {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

        if (displays != null && displays.length > 0) {
            Display display = displays[displays.length - 1];  // 选择最后一个显示设备
            presentation = new MyPresentation(this, display);
            Log.d("AudioDevice", "enter displayVideoOnSecondScreen " );
            // 设置视频路径（可以是本地文件或网络视频 URL）
            String videoPath = "file:///sdcard/Music/easy love.mp4";  // 或者你的实际视频路径

            // 在 Presentation 中播放视频
//            presentation.setVideoPath(videoPath);
            presentation.setVideoPathAndAudioDevice(videoPath);  // 设置视频路径和音频设备
            // 显示 Presentation
            presentation.show();
        } else {
            Toast.makeText(this, "No external display found.", Toast.LENGTH_SHORT).show();
        }
    }
}