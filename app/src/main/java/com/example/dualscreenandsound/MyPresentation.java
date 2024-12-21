package com.example.dualscreenandsound;

import android.app.Presentation;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
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
    private AudioDeviceInfo selectedDevice;
    private Surface surface;  // 引用 Surface，确保在正确的时机设置 display
    public MyPresentation(Context context, Display display) {
        super(context, display);
        setContentView(R.layout.presentation);  // 这里你可以定义布局文件，包含一个 SurfaceView
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource("/sdcard/Music/easy love.mp4");  // 设置视频路径
            mediaPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        surfaceView = findViewById(R.id.surface_view); // 获取 SurfaceView
        // 初始化音频管理器

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
    public void initMediaPlayer(Uri fileUri) {
        try {
            // 创建 MediaPlayer 实例
            mediaPlayer = new MediaPlayer();
            // 设置数据源为本地视频文件的路径
            mediaPlayer.setDataSource(getContext(), fileUri);
            // 准备播放器
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 设置视频路径并选择音频输出设备
    public void setVideoPathAndAudioDevice(AudioDeviceInfo selectedDevice) {

            boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
            if (success) {
                Log.d("AudioDevice", "已设置音频输出设备为: " + selectedDevice.getType());
            } else {
                Log.d("AudioDevice", "设置音频设备失败");
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