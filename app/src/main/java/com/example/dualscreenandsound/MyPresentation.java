package com.example.dualscreenandsound;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;


public class MyPresentation extends Presentation {
    public MediaPlayer mediaPlayer;
    public SurfaceView surfaceView;
    public Surface surface;
    private AudioManager mAudioManager;
    public boolean isAudioDeviceSet = false;
    public boolean isVideoPlaying  = false;
    public long currentPosition  = 0;
    public ActivityResultLauncher<Intent> selectFileLauncher;
    public AudioDeviceInfo selectedDevice;
    public MyPresentation(Context context, Display display) {
        super(context, display);
        setContentView(R.layout.presentation);  // 这里你可以定义布局文件，包含一个 SurfaceView
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        surfaceView = findViewById(R.id.surface_view); // 获取 SurfaceView
        surface = surfaceView.getHolder().getSurface();
    }

    // 初始化 MediaPlayer
    public void initMediaPlayer(Uri fileUri,Surface surface) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                Log.d("MediaPlayer", "Presention MediaPlayer 被初始化");
            } else {
                Log.d("MediaPlayer", "Presention MediaPlayer 重置");
                mediaPlayer.reset(); // 重置MediaPlayer
            }
            Log.d("MediaPlayer", "副屏 URI: " + fileUri.toString());
            // 设置数据源为本地视频文件的路径
            mediaPlayer.setDataSource(getContext(), fileUri);
            mediaPlayer.setSurface(surface);  // 绑定Surface
            mediaPlayer.setOnPreparedListener(mp -> {
                Log.d("MediaPlayer", "副屏播放器准备完毕");
                // 恢复播放状态
                Log.d("MediaPlayer", "准备恢复PresentionrestorePlaybackState");
                restorePlaybackState();

            });
            // 准备播放器
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 释放 MediaPlayer
    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            Log.d("MediaPlayer", "presentation.MediaPlayer 被释放");
        }
    }

    // 保存MediaPlayer播放状态
    public void savePlaybackState() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            isVideoPlaying = true;
            Log.d("MediaPlayer", "尝试获取presentation当前播放位置");
            currentPosition =  mediaPlayer.getCurrentPosition(); // 获取当前播放位置
        } else {
            Log.d("MediaPlayer", "未去获取presentation当前播放位置");
            isVideoPlaying = false;
        }
    }

    // 恢复MediaPlayer播放状态
    public void restorePlaybackState() {
        if (mediaPlayer != null) {
            try {
                Log.d("MediaPlayer", "尝试恢复PresentionrestorerestorePlaybackState");
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
    public void openFileChooser() {
        Log.d("ActivityResult", "正在打开文件选择器");
        // 检查是否有文件读取权限
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getOwnerActivity(), new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } else {
            // 启动文件选择器（选择音频或视频）
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");  // 可以选择所有类型的文件，或者修改为"video/*"或"audio/*"
            selectFileLauncher.launch(intent);  // 使用新的方式启动文件选择器
        }
    }


    // 设置音频输出设备
    public void setAudioDevice(AudioDeviceInfo selectedDevice) {
            boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
            if (success) {
                isAudioDeviceSet = true;  // 设置成功，标志位为 true
                Log.d("AudioDevice", "已设置音频输出为: " + selectedDevice.getType());
            } else {
                isAudioDeviceSet = false;  // 设置失败，标志位为 false
                Log.d("AudioDevice", "设置设备失败");
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