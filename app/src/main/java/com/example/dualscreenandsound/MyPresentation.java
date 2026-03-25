package com.example.dualscreenandsound;

import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
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
    private SurfaceHolder surfaceHolder;
    private boolean primingOutput = false;
    public MyPresentation(Context context, Display display) {
        super(context, display);
        setContentView(R.layout.presentation);  // 这里你可以定义布局文件，包含一个 SurfaceView
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        surfaceView = findViewById(R.id.surface_view); // 获取 SurfaceView
        surface = surfaceView.getHolder().getSurface();
        surfaceHolder = surfaceView.getHolder();
        Log.d("MediaPlayer", "获取一个新的 Surface");
        // SurfaceHolder.Callback 用来处理 Surface 创建和销毁
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d("MediaPlayer", "MyPresentation surfaceCreated. Attempting to attach/init MediaPlayer.");
                if (mediaPlayer == null && MainActivity.presentionselectedUri != null) { // Only init if no player exists and a URI is selected
                    initMediaPlayer(MainActivity.presentionselectedUri, holder.getSurface());
                } else if (mediaPlayer != null) { // If player exists, just set the new surface
                    mediaPlayer.setSurface(holder.getSurface());
                    // Restore playback state if it was saved, but only if not currently switching to a new video
                    if (!MainActivity.isSwitchingToNewVideo) {
                        restorePlaybackState();
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d("MediaPlayer", "MyPresentation surfaceChanged");
                // 处理Surface变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d("MediaPlayer", "MyPresentation surfaceDestroyed. Saving playback state and detaching MediaPlayer.");
                savePlaybackState(); // Always save playback state

                // Only detach surface, do NOT release MediaPlayer here
                if (mediaPlayer != null) {
                    mediaPlayer.setSurface(null); // Detach the Surface from the MediaPlayer
                    Log.d("MediaPlayer", "MyPresentation MediaPlayer Surface 已分离，player 未释放。");
                }
            }
        });
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
    private void restorePlaybackState() { // Changed back to private
        if (mediaPlayer != null) {
            try {
                Log.d("MediaPlayer", "尝试恢复restorePlaybackState");

                if (MainActivity.isSwitchingToNewVideo) {
                    // 如果切换到新的视频，确保从0秒开始播放
                    Log.d("MediaPlayer", "从0秒开始播放");
                    mediaPlayer.seekTo(0);
                } else {
                    // 恢复播放进度
                    Log.d("MediaPlayer", "恢复播放进度");
                    mediaPlayer.seekTo((int) currentPosition);
                }

                if (isVideoPlaying && !MainActivity.isSwitchingToNewVideo) {
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

    // 设置音频输出设备
    public void setAudioDevice(AudioDeviceInfo selectedDevice) {
        if(selectedDevice!=null) {
            Log.d("AudioDevice", "设置音频输出" );
            boolean success = mediaPlayer.setPreferredDevice(selectedDevice);
            if (success) {
                isAudioDeviceSet = true;  // 设置成功，标志位为 true
                Log.d("AudioDevice", "已设置音频输出为: " + selectedDevice.getType());
            } else {
                isAudioDeviceSet = false;  // 设置失败，标志位为 false
                Log.d("AudioDevice", "设置设备失败");
            }
        }else {
            Log.d("AudioDevice", "selectedDevice为空" );
        }
    }

    public boolean isPrimingOutput() {
        return primingOutput;
    }

    public boolean primeOutputForSmoothStart() {
        if (mediaPlayer == null || primingOutput || mediaPlayer.isPlaying()) {
            return false;
        }
        primingOutput = true;
        try {
            int resumePosition = Math.max(0, (int) currentPosition);
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
            mediaPlayer.pause();
            mediaPlayer.seekTo(resumePosition);
            return true;
        } catch (IllegalStateException e) {
            Log.w("MediaPlayer", "副屏预热失败", e);
            return false;
        } finally {
            primingOutput = false;
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
