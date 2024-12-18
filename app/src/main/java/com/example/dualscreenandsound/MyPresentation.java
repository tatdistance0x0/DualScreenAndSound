package com.example.dualscreenandsound;

import android.app.Presentation;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;


public class MyPresentation extends Presentation {
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private boolean isPlaying = false;  // 用于追踪视频是否正在播放
    private AudioDeviceInfo[] mOutputDevices;
    private AudioManager mAudioManager;
    private Surface surface;  // 引用 Surface，确保在正确的时机设置 display
    public MyPresentation(Context context, Display display) {
        super(context, display);
        setContentView(R.layout.presentation);  // 这里你可以定义布局文件，包含一个 SurfaceView
        surfaceView = findViewById(R.id.surface_view); // 获取 SurfaceView
        // 初始化音频管理器
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // 注册 SurfaceHolder.Callback
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surface = holder.getSurface();  // 获取 Surface
                Log.d("MyPresentation", "Surface 已准备好");
                // Surface 创建完成后才可以设置给 MediaPlayer

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 可以在这里处理 Surface 的尺寸变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                // Surface 被销毁时，释放 MediaPlayer
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
            }
        });
    }


    // 设置视频路径并选择音频输出设备
    public void setVideoPathAndAudioDevice(String videoPath) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(videoPath);  // 设置视频路径

            // 获取所有输出设备
            mOutputDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

            // 遍历输出设备，选择一个合适的设备
            for (AudioDeviceInfo device : mOutputDevices) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    boolean success = mediaPlayer.setPreferredDevice(device);
                    if (success) {
                        Log.d("AudioDevice", "已设置音频输出设备为: " + device.getType());
                    } else {
                        Log.d("AudioDevice", "设置音频设备失败");
                    }
                }
            }
            mediaPlayer.prepare();  // 异步准备
        } catch (IOException e) {
            Log.e("MyPresentation", "设置视频路径和音频设备时出错", e);
        }
    }


    public void playVideo() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            Log.d("MyPresentation", "Starting video...");
            if (mediaPlayer != null) {
                mediaPlayer.setDisplay(surfaceView.getHolder());  // 设置显示 SurfaceView
                mediaPlayer.start();    // 开始播放

            }else {
                Log.e("MyPresentation", "MediaPlayer 尚未初始化");
            }
            isPlaying = true;
        }
    }

    public void pauseVideo() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d("MyPresentation", "Pausing video...");
            mediaPlayer.pause();  // 暂停视频
            isPlaying = false;
        }
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