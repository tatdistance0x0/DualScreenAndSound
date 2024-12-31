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
import android.view.SurfaceView;
import java.io.IOException;


public class MyPresentation extends Presentation {
    public MediaPlayer mediaPlayer;
    public SurfaceView surfaceView;
    private Surface surface;
    private AudioManager mAudioManager;
    public boolean isPrensentationAudioDeviceSet = false;

    public MyPresentation(Context context, Display display) {
        super(context, display);
        setContentView(R.layout.presentation);  // 这里你可以定义布局文件，包含一个 SurfaceView
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        surfaceView = findViewById(R.id.surface_view); // 获取 SurfaceView
        surface = surfaceView.getHolder().getSurface();
    }
    public void initPresentionMediaPlayer(Uri fileUri) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();  // 重置之前的 MediaPlayer
                mediaPlayer.release();  // 释放资源
                mediaPlayer = null;  // 设置为 null，避免引用
            }
            // 创建 MediaPlayer 实例
            mediaPlayer = new MediaPlayer();
            Log.d("MediaPlayer", "副屏 URI: " + fileUri.toString());
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
                isPrensentationAudioDeviceSet = true;  // 设置成功，标志位为 true
                Log.d("AudioDevice", "已设置音频输出为: " + selectedDevice.getType());
            } else {
                isPrensentationAudioDeviceSet = false;  // 设置失败，标志位为 false
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