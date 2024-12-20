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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private AudioManager mAudioManager;
    private MyPresentation presentation;  // 用来保存对 Presentation 的引用
    private boolean isVideoPlaying = false;  // 用来追踪视频是否正在播放
    private MediaPlayer mediaPlayer;
    private AudioDeviceInfo[] mOutputDevices;
    private SurfaceView surfaceView;
    private Surface surface;

    private List<AudioDeviceInfo> OutputDevices;
    private Spinner mAudioDevicesSpinner1,mAudioDevicesSpinner2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mediaPlayer = new MediaPlayer();
//        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

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

        // 设置 Spinner 的适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAudioDevicesSpinner1.setAdapter(adapter);
        mAudioDevicesSpinner2.setAdapter(adapter);

        // 监听 Spinner 的选择事件
        mAudioDevicesSpinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                AudioDeviceInfo selectedDevice = OutputDevices.get(position);
                try {
                    setAudioDevice(selectedDevice);  // 根据选择的设备设置音频输出
                } catch (IOException e) {
                    throw new RuntimeException(e);
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
                AudioDeviceInfo selectedDevice = OutputDevices.get(position);
                try {
                    showSecondByDisplayManager(selectedDevice);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                // 如果没有选择任何设备，可以执行一些默认操作
            }
        });

        surfaceView = findViewById(R.id.surfaceView);  // SurfaceView 用于显示视频
        surface = surfaceView.getHolder().getSurface();

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
                // 检查权限并执行播放或暂停操作
                if (checkPermissions()) {
                    if (mediaPlayer.isPlaying()) {
                        // 如果正在播放，则暂停
                        mediaPlayer.pause();
                        btn1.setText("播放");
                    } else {
                        // 如果没有播放，则开始播放
                        mediaPlayer.setDisplay(surfaceView.getHolder());  // 设置显示 SurfaceView
                        mediaPlayer.start();
                        //                        mediaPlayer.start();
                        btn1.setText("暂停");

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
                        btn2.setText("播放");
                    } else {
                        presentation.playVideo();   // 播放视频
                        btn2.setText("暂停");
                    }
                    isVideoPlaying = !isVideoPlaying;  // 切换播放/暂停状态
                }else{
                    Log.e("MainActivity", "presentation is null. Cannot control video playback.");
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
        mediaPlayer.reset();
        mediaPlayer.setDataSource("/sdcard/Music/Melody.mp4");  // 设置视频路径
        boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
        if (success) {
            Log.d("AudioDevice", "已设置音频输出为: " + getDeviceTypeName(selectedDevice.getType()));
        } else {
            Log.d("AudioDevice", "设置设备失败");
        }
        // 准备和开始播放
        mediaPlayer.prepare();
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

//
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1001;
    private void showSecondByDisplayManager(AudioDeviceInfo selectedDevice) throws IOException {
        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
        }else {
            // 权限已授予，继续执行展示视频
            displayVideoOnSecondScreen(selectedDevice);
        }
    }
    private void displayVideoOnSecondScreen(AudioDeviceInfo selectedDevice) {
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

        if (displays != null && displays.length > 0) {
            Display display = displays[displays.length - 1];  // 选择最后一个显示设备
            presentation = new MyPresentation(this, display);
            Log.d("AudioDevice", "enter displayVideoOnSecondScreen " );

            // 在 Presentation 中播放视频
            presentation.setVideoPathAndAudioDevice(selectedDevice);  // 设置视频路径和音频设备
            // 显示 Presentation
            presentation.show();
        } else {
            Toast.makeText(this, "No external display found.", Toast.LENGTH_SHORT).show();
        }
    }


}