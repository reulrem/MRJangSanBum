package com.digitalzone.mrjangsanbum;

import com.digitalzone.mrjangsanbum.AudioStreamPlayer;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

public interface OnAudioStreamInterface
{
    public void onAudioPlayerStart(AudioStreamPlayer player);

    public void onAudioPlayerPause(AudioStreamPlayer player);

    public void onAudioPlayerStop(AudioStreamPlayer player);

    public void onAudioPlayerError(AudioStreamPlayer player);

    public void onAudioPlayerBuffering(AudioStreamPlayer player);

    public void onAudioPlayerDuration(int totalSec);

    public void onAudioPlayerCurrentTime(int sec);

    public void onAudioPlayerPCMData(ByteBuffer pcmData);
}