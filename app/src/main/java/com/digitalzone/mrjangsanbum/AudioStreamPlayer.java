package com.digitalzone.mrjangsanbum;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AudioStreamPlayer {

    private static final String TAG = "AudioStreamPlayer";

    private MediaExtractor mExtractor = null;
    private MediaCodec mMediaCodec = null;

    private int mInputBufIndex = 0;

    private boolean isForceStop = false;
    private volatile boolean isPause = false;

    ByteBuffer[] codecInputBuffers;
    ByteBuffer[] codecOutputBuffers;

    protected OnAudioStreamInterface mListener = null;

    public void setOnAudioStreamInterface(OnAudioStreamInterface listener) {
        this.mListener = listener;
    }

    public enum State {
        Stopped, Prepare, Buffering, Playing, Pause
    };

    private Context thisActivity = null;

    State mState = State.Stopped;

    public State getState() {
        return mState;
    }

    private String mMediaPath;

    private double editRate = 1.0;
    private final int mMaxRate = 122000;

    public void setUrlString(String mUrlString) {
        this.mMediaPath = mUrlString;
    }

    public void setRate(double rate) {
        editRate = rate;
    }

    public AudioStreamPlayer(Context activity) {
        mState = State.Stopped;
        thisActivity = activity;
    }

    public void play() {
        mState = State.Prepare;
        isForceStop = false;

        mAudioPlayerHandler.onAudioPlayerBuffering(AudioStreamPlayer.this);

        new Thread(new Runnable() {
            @Override
            public void run(){
                try{
                    decodeLoop();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
//				Intent intent = new Intent(thisActivity, AudioProcessing.class);
//
//				intent.putExtra("input",codecInputBuffers[0].toString());
//				intent.putExtra("output",codecOutputBuffers[0].toString());
//				thisActivity.startActivity(intent);
            }
        }).start();
    }

    private DelegateHandler mAudioPlayerHandler = new DelegateHandler();

    @SuppressLint("HandlerLeak")
    class DelegateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        }

        public void onAudioPlayerPlayerStart(AudioStreamPlayer player) {
            Log.d(TAG,"onAudioPlayerPlayerStart");
            if (mListener != null) {
                mListener.onAudioPlayerStart(player);
            }
        }

        public void onAudioPlayerStop(AudioStreamPlayer player) {
            Log.d(TAG,"onAudioPlayerStop");
            if (mListener != null) {
                mListener.onAudioPlayerStop(player);
            }
        }

        public void onAudioPlayerError(AudioStreamPlayer player) {
            Log.d(TAG,"onAudioPlayerError");
            if (mListener != null) {
                mListener.onAudioPlayerError(player);
            }
        }

        public void onAudioPlayerBuffering(AudioStreamPlayer player) {
            Log.d(TAG,"onAudioPlayerBuffering");
            if (mListener != null) {
                mListener.onAudioPlayerBuffering(player);
            }
        }

        public void onAudioPlayerDuration(int totalSec) {
            Log.d(TAG,"onAudioPlayerDuration : " + totalSec);
            if (mListener != null) {
                mListener.onAudioPlayerDuration(totalSec);
            }
        }

        public void onAudioPlayerCurrentTime(int sec) {
            Log.d(TAG,"onAudioPlayerCurrentTime : " + sec);
            if (mListener != null) {
                mListener.onAudioPlayerCurrentTime(sec);
            }
        }

        public void onAudioPlayerPlayerPCM(ByteBuffer pcm) {
            if (mListener != null) {

                if(pcm.hasRemaining()){
                    Log.d(TAG,"mAudioPlayerHandler pcmData has data");
                }
                else{
                    Log.d(TAG,"mAudioPlayerHandler pcmData hasn't data");
                }
                mListener.onAudioPlayerPCMData(pcm);
            }
        }

        public void onAudioPlayerPause() {
            if(mListener != null) {
                mListener.onAudioPlayerPause(AudioStreamPlayer.this);
            }
        }
    }

    private void decodeLoop() throws IOException {
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        mExtractor = new MediaExtractor();
        AssetFileDescriptor afd = thisActivity.getResources().openRawResourceFd(R.raw.nightmare);
        try {
            mExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (Exception e) {
            mAudioPlayerHandler.onAudioPlayerError(AudioStreamPlayer.this);
            return;
        }

        MediaFormat format = mExtractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        long duration = format.getLong(MediaFormat.KEY_DURATION);
        int totalSec = (int) (duration / 1000 / 1000);
        int min = totalSec / 60;
        int sec = totalSec % 60;

        mAudioPlayerHandler.onAudioPlayerDuration(totalSec);

        Log.d(TAG, String.format("Time = %02d : %02d",min,sec));
        Log.d(TAG, "Duration = " + duration);

        mMediaCodec = MediaCodec.createDecoderByType(mime);
        mMediaCodec.configure(format, null, null, 0);

        mMediaCodec.start();

        codecInputBuffers = mMediaCodec.getInputBuffers();
        codecOutputBuffers = mMediaCodec.getOutputBuffers();

//		for(ByteBuffer codecInputBuffer : codecInputBuffers){
////			byte[] array = codecInputBuffer.array();
//			String bufferToString = codecInputBuffer.toString();
//
//			Log.d(TAG, "codecInputBuffer = " + bufferToString);
//		}
//
//		for(ByteBuffer codecOutputBuffer : codecOutputBuffers){
////			byte[] array = codecOutputBuffer.array();
//			String bufferToString = codecOutputBuffer.toString();
//			Log.d(TAG, "codecOutputBuffer = " + bufferToString);
//		}

        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        Log.i(TAG, "mime " + mime);
        Log.i(TAG, "sampleRate " + (sampleRate));
        Log.i(TAG, "sampleRate " + (sampleRate * editRate));
        Log.i(TAG, "sampleRate " + (int) (sampleRate * editRate));

//		Toast.makeText(thisActivity,"sampleRate " + (sampleRate * editRate) + "\nsampleRate" + (int) (sampleRate * editRate) ,Toast.LENGTH_LONG).show();

        sampleRate = (int) (sampleRate * editRate);
        if(sampleRate > mMaxRate) {
            sampleRate = mMaxRate;
        }

        mExtractor.selectTrack(0);

        final long kTimeOutUs = 10000;
        final long nTimeOutUs = 10;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        int noOutputCounter = 0;
        int noOutputCounterLimit = 50;

        int countChunk = 0;

        while (!sawInputEOS && noOutputCounter < noOutputCounterLimit && !isForceStop) {
            {
                if(isPause) {
                    if(mState != State.Pause) {
                        mState = State.Pause;
                        mAudioPlayerHandler.onAudioPlayerPause();
                    }
                    continue;
                }
                noOutputCounter++;
                mInputBufIndex = mMediaCodec.dequeueInputBuffer(kTimeOutUs);
//                mInputBufIndex = mMediaCodec.dequeueInputBuffer(nTimeOutUs);
                if (mInputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[mInputBufIndex];

                    int sampleSize = mExtractor.readSampleData(dstBuf, 0);

//					Log.d(TAG,"sampleSize : " + sampleSize);
//
//					double[] toTransform = new double[256];
//					short[] buffer = new short[256];
//
//					RealDoubleFFT transformer = new RealDoubleFFT(256);
//
//					for(int i = 0; i < 256 && i < sampleSize; i++){
//
//						toTransform[i] = (double)buffer[i] / Short.MAX_VALUE; // 부호 있는 16비트
//
//						transformer.ft(toTransform);
//
//						StringBuilder valueOfTransform = new StringBuilder("[");
//						for(int j = 0; j < toTransform.length; j++){
//							valueOfTransform.append(j).append(", ");
//						}
//						valueOfTransform.append("]");
//
//						String mValueOfHz = new String(valueOfTransform);
//
//						Log.d(TAG,"hz : " + mValueOfHz);
//					}

                    long presentationTimeUs = 0;

                    if (sampleSize < 0) {
                        sawInputEOS = true;
                        sampleSize = 0;
                    } else {
                        presentationTimeUs = mExtractor.getSampleTime();
                        mAudioPlayerHandler.onAudioPlayerCurrentTime((int) (presentationTimeUs / 1000 / 1000));
                    }

                    mMediaCodec.queueInputBuffer(mInputBufIndex, 0, sampleSize, presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS) {
                        mExtractor.advance();
                    }
                }
                else {
                    Log.e(TAG, "inputBufIndex " + mInputBufIndex);
                }
            }

            int res = mMediaCodec.dequeueOutputBuffer(info, kTimeOutUs);
//            int res = mMediaCodec.dequeueOutputBuffer(info, nTimeOutUs);

            if (res >= 0) {
                if (info.size > 0) {
                    noOutputCounter = 0;
                }

                int outputBufIndex = res;
                Log.i(TAG, "outputBufIndex : " + outputBufIndex);

                ByteBuffer buf;

                try{

                    if (Build.VERSION.SDK_INT >= 21) {
                        buf = mMediaCodec.getOutputBuffer(outputBufIndex);
                    } else {
                        buf = codecOutputBuffers[outputBufIndex];
                    }

                    if (buf != null){
                        ByteBuffer copy = buf.duplicate();

                        byte[] chunk = new byte[info.size];
                        buf.get(chunk);
                        if (chunk.length > 0) {
//					String chunkToString = new String(chunk);

//                        chunk = editChunkData(chunk);

                            countChunk++;
                            if (this.mState != State.Playing) {
                                mAudioPlayerHandler.onAudioPlayerPlayerStart(AudioStreamPlayer.this);
                                this.mState = State.Playing;
                            }
                            mAudioPlayerHandler.onAudioPlayerPlayerPCM(copy);
                        }
                    }

                }catch (NullPointerException e ){
                    e.printStackTrace();
                    mAudioPlayerHandler.onAudioPlayerError(AudioStreamPlayer.this);
                }catch (Exception e ){
                    e.printStackTrace();
                    mAudioPlayerHandler.onAudioPlayerError(AudioStreamPlayer.this);
                }

                mMediaCodec.releaseOutputBuffer(outputBufIndex, false);
            }
            else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = mMediaCodec.getOutputBuffers();

                Log.d(TAG, "output buffers have changed.");
            }
            else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat oformat = mMediaCodec.getOutputFormat();

                Log.d(TAG, "output format has changed to " + oformat);
            }
            else {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }

        this.codecInputBuffers  = codecInputBuffers;
        this.codecOutputBuffers = codecOutputBuffers;

        Log.d(TAG, "stopping...");

        Log.d(TAG,"countChunk : " + countChunk);

        releaseResources(true);

        this.mState = State.Stopped;
        isForceStop = true;

        if (noOutputCounter >= noOutputCounterLimit) {
            mAudioPlayerHandler.onAudioPlayerError(AudioStreamPlayer.this);
        }
        else {
            mAudioPlayerHandler.onAudioPlayerStop(AudioStreamPlayer.this);
        }
    }

    public void release() {
        stop();
        releaseResources(false);
    }

    private void releaseResources(Boolean release) {
        if (mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }

        if (mMediaCodec != null) {
            if (release) {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            }
        }
    }

    public void pause() {
        isPause = true;
    }

    public void stop() {
        isForceStop = true;
    }

    public void pauseToPlay() {
        isPause = false;
    }

    public byte [] editChunkData( byte [] chunk) {

        for(int i = 0; i < chunk.length; i++){
            chunk[i] ++;
        }

        return chunk;
    }
}
