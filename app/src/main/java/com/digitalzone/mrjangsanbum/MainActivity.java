package com.digitalzone.mrjangsanbum;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

//import org.apache.commons.math3.complex.Complex;
//import org.apache.commons.math3.transform.FastFourierTransformer;
//import org.apache.commons.math3.transform.DftNormalization;
//import org.apache.commons.math3.transform.TransformType;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.transform.FastFourierTransformer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements OnAudioStreamInterface{

    private Button t ,st;
    private EditText rate;
    String TAG = "jangsanbum";
    AudioStreamPlayer mAudioPlayer = null;

    ImageView imageView;
    Bitmap bitmap;
    Canvas canvas;
    Paint paint;

    private Context thisActivity;
    private OnAudioStreamInterface thisStream;

    boolean isPlay = false;

    private String musicName = "";

    private double editRate;

    List<byte[]> collectByte = new ArrayList<>();

    private DrawHZHandler handler = new DrawHZHandler();

    @SuppressLint("HandlerLeak")
    class DrawHZHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

        }

        public void printHZ() {

            try{

                FastFourierTransformer fft = new FastFourierTransformer();

                for(int i = 0; i < collectByte.size(); i++){

//                byte [] pcmByte = new byte[pcmBuffer.limit()];
//                pcmBuffer.get(pcmByte,0,pcmByte.length);
//                Log.d(TAG,"pcmBuffer.limit() : " + pcmBuffer.limit());
//
//                for(int j = 0; j < pcmByte.length; j++){
//                    Log.d(TAG,"pcmByte [" + j + "] : " + pcmByte[j]);
//                }

                    ByteBuffer bb = ByteBuffer.wrap(collectByte.get(i));

                    bb.rewind();

                    double[] pcmData = new double[8192];

                    for(int y= 0; bb.hasRemaining();y++) {

                        short s = bb.getShort();

                        pcmData[ y ] = (double)s/Short.MAX_VALUE;
                        pcmData[ y ] = (new Short(s)).doubleValue();

//                        long dB = (long) (20 * Math.log10(Math.abs(pcmData[ y ] ) / 32768));
//                        Log.d(TAG,"데시벨 : " + dB);
                    }

                    Complex[] cmplx= fft.transform(pcmData);

                    double real;
                    double im;
                    double mag[] = new double[cmplx.length];

                    for(int j = 0; j < cmplx.length / 2; j++){
                        real = cmplx[j].getReal();
                        im = cmplx[j].getImaginary();
                        mag[j] = Math.sqrt((real * real) + (im*im));
                    }

                    double peak = -1.0;
                    int index=-1;
                    int j = 0;

                    for(j = 0; j < cmplx.length; j++){

                        if(peak < mag[j]){
                            index = j;
                            peak= mag[j];
                        }

                    }

                    double frequency = (double) (44100 * index) / j;
                    Log.d(TAG,"collectByte.get(" + i + ") : " + collectByte.get(i).length);
                    Log.d(TAG,"Index : "+index+", Frequency : "+ frequency +", peak : " + peak + ", length : " + j);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        public void createHZ(){

            try{

                final long kTimeOutUs = 10000;
                MediaExtractor mExtractor = new MediaExtractor();
                AssetFileDescriptor afd = thisActivity.getResources().openRawResourceFd(R.raw.nightmare);
                try {
                    mExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                MediaFormat format = mExtractor.getTrackFormat(0);
                String mime = format.getString(MediaFormat.KEY_MIME);
                MediaCodec mMediaCodec = MediaCodec.createDecoderByType(mime);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputBufIndex = mMediaCodec.dequeueOutputBuffer(info, kTimeOutUs);
                ByteBuffer buf;

                if(Build.VERSION.SDK_INT > 21){
                    buf = mMediaCodec.getOutputBuffer(outputBufIndex);
                }else{
                    buf = mMediaCodec.getOutputBuffers()[outputBufIndex];
                }

                final byte[] chunk = new byte[info.size];
                buf.get(chunk);

                collectByte.add(chunk);

            }catch (IOException e) {e.printStackTrace();}
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        final int duration = 10; // duration of sound
//        final int sampleRate = 100; // Hz (maximum frequency is 7902.13Hz (B8))
//        final int numSamples = duration * sampleRate;
//        final double[] samples = new double[numSamples];
//        final short[] buffer = new short[numSamples];
//        for (int i = 0; i < numSamples; ++i){
//            samples[i] = sin(2 * PI * i / (sampleRate )); // Sine wave
//            buffer[i] = (short) (samples[i] * Short.MAX_VALUE);  // Higher amplitude increases volume
//            Log.d(TAG,"sample : " + samples[i];
//            Log.d(TAG,"buffer : " + buffer[i]);
//        }

        thisActivity = this;
        thisStream = this;

        rate = findViewById(R.id.rate);

        imageView = (ImageView)findViewById(R.id.ImageView01);
        bitmap = Bitmap.createBitmap((int)256, (int)100, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        paint = new Paint();

        paint.setColor(Color.GREEN);
        imageView.setImageBitmap(bitmap);

        t = findViewById(R.id.start_btn);
        st = findViewById(R.id.stop_btn);

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                try{
                    editRate = Double.valueOf(String.valueOf(rate.getText()));

                    if(editRate == 0){
                        throw new NumberFormatException();
                    }
                }catch(NumberFormatException e) {
                    editRate = 1.0;
                    e.printStackTrace();
                }

//                try{
//                    intent.setData(Uri.parse("mister://jangsan"));
//                    startActivity(intent);
//                } catch (ActivityNotFoundException e ){
//                    try{
//                        e.printStackTrace();
//                        intent.setData(Uri.parse("market://details?id=com.digitalzone.medcerti"));
//                        startActivity(intent);
//                    }catch (ActivityNotFoundException E ){
//
//                        E.printStackTrace();
//                        intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.digitalzone.medcerti"));
//                        startActivity(intent);
//                    }
//                }


                //실제 핸드폰에서 해볼것
//                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
//                    String[] rs = getMusicAbsolutePath();
//
//                    String mp3Path = rs[(int) (Math.random() * (rs.length - 1))];
//
//                    final String r = mp3Path.split("/")[mp3Path.split("/").length-1];
//
//                    runOnUiThread(new Runnable()
//                    {
//                        @Override
//                        public void run()
//                        {
//                            t.setText(r);
//                        }
//                    });
//                }

//                Log.d(TAG,thisActivity.getResources().openRawResourceFd(R.raw.nightmare).getExtras().toString());

                if (t.isSelected()) {
                    if (mAudioPlayer != null && mAudioPlayer.getState() == AudioStreamPlayer.State.Pause) {
                        mAudioPlayer.pauseToPlay();
                    }
                    else {
                        pause();
                    }
                }
                else {
//					String[] rs = getMusicAbsolutePath();
//
//					String mp3Path = rs[(int) (Math.random() * (rs.length - 1))];
//
//					for(int i =0; i < rs.length; i++){
//						if(rs[i].contains(musicNameSetter.getText())){
//							mp3Path = rs[i];
//						}
//					}
//
//					final String r = mp3Path.split("/")[mp3Path.split("/").length-1];
//
//					mp3File.setText(r);
//
//					Log.d(TAG,"name : " + r);
                    play();
                }

                isPlay = !isPlay;

//                    try {
////                        res.setContentType("text/json");
//                        MinimServiceProvider mimp = new MinimServiceProvider() {
//                            String mimTAG = "MinimServiceProvider";
//                            @Override
//                            public void start() {
//                                Log.d(mimTAG,"start");
//                            }
//
//                            @Override
//                            public void stop() {
//
//                                Log.d(mimTAG,"stop");
//                            }
//
//                            @Override
//                            public void debugOn() {
//                                Log.d(mimTAG,"debugOn");
//
//                            }
//
//                            @Override
//                            public void debugOff() {
//                                Log.d(mimTAG,"debugOff");
//
//                            }
//
//                            @Override
//                            public AudioRecording getAudioRecording(String filename) {
//                                Log.d(mimTAG,"getAudioRecording");
//                                Log.d(mimTAG,"filename : " + filename);
//                                return null;
//                            }
//
//                            @Override
//                            public AudioRecordingStream getAudioRecordingStream(String filename, int bufferSize, boolean inMemory) {
//                                Log.d(mimTAG,"getAudioRecordingStream");
//                                Log.d(mimTAG,"filename : " + filename);
//                                Log.d(mimTAG,"bufferSize : " + bufferSize);
//                                Log.d(mimTAG,"inMemory : " + inMemory);
//                                return null;
//                            }
//
//                            @Override
//                            public AudioStream getAudioInput(int type, int bufferSize, float sampleRate, int bitDepth) {
//                                Log.d(mimTAG,"getAudioInput");
//                                Log.d(mimTAG,"type : " + type);
//                                Log.d(mimTAG,"bufferSize : " + bufferSize);
//                                Log.d(mimTAG,"sampleRate : " + sampleRate);
//                                Log.d(mimTAG,"bitDepth : " + bitDepth);
//                                return null;
//                            }
//
//                            @Override
//                            public AudioOut getAudioOutput(int type, int bufferSize, float sampleRate, int bitDepth) {
//                                Log.d(mimTAG,"getAudioOutput");
//                                Log.d(mimTAG,"type : " + type);
//                                Log.d(mimTAG,"bufferSize : " + bufferSize);
//                                Log.d(mimTAG,"sampleRate : " + sampleRate);
//                                Log.d(mimTAG,"bitDepth : " + bitDepth);
//
//                                if (bitDepth != 8 && bitDepth != 16)
//                                {
//                                    throw new IllegalArgumentException("Unsupported bit depth, use either 8 or 16.");
//                                }
//                                AudioFormat format = new AudioFormat(sampleRate, bitDepth, type, true, false);
//
//                                return null;
//                            }
//
//                            @Override
//                            public AudioSample getAudioSample(String filename, int bufferSize) {
//                                Log.d(mimTAG,"getAudioSample");
//                                Log.d(mimTAG,"filename : " + filename);
//                                Log.d(mimTAG,"bufferSize : " + bufferSize);
//                                return null;
//                            }
//
//                            @Override
//                            public AudioSample getAudioSample(float[] samples, AudioFormat format, int bufferSize) {
//                                Log.d(mimTAG,"getAudioSample");
//                                for(float sample : samples){
//
//                                    Log.d(mimTAG,"sample : " + sample);
//                                }
//                                Log.d(mimTAG,"bufferSize : " + bufferSize);
//                                return null;
//                            }
//
//                            @Override
//                            public AudioSample getAudioSample(float[] left, float[] right, AudioFormat format, int bufferSize) {
//
//                                Log.d(mimTAG,"getAudioSample");
//                                for(float lefts : left){
//
//                                    Log.d(mimTAG,"lefts : " + lefts);
//                                }
//                                for(float rights : right){
//
//                                    Log.d(mimTAG,"rights : " + rights);
//                                }
//                                Log.d(mimTAG,"bufferSize : " + bufferSize);
//
//                                return null;
//                            }
//
//                            @Override
//                            public SampleRecorder getSampleRecorder(Recordable source, String saveTo, boolean buffered) {
//                                Log.d(mimTAG,"getSampleRecorder");
//                                Log.d(mimTAG,"saveTo : " + saveTo);
//                                Log.d(mimTAG,"buffered : " + buffered);
//                                return null;
//                            }
//                        };
//
//
//
//
//                        Minim minim = new Minim(mimp);
//
//                        AudioSample jingle = minim.loadSample(r, 2048);
//
//                        // get the left channel of the audio as a float array
//                        // getChannel is defined in the interface BuffereAudio,
//                        // which also defines two constants to use as an argument
//                        // BufferedAudio.LEFT and BufferedAudio.RIGHT
//                        float[] leftChannel = jingle.getChannel(AudioSample.LEFT);
//                        Log.d(TAG,"channel length: " + leftChannel.length);
//                        Log.d(TAG,"length / 500: " + (leftChannel.length / 500) );
//                        Log.d(TAG,"near pow2: " + (tempPowerOfTwo(leftChannel.length / 500) ));
//                        // timeSize는 2의 제곱만 허용하기 때문에 사이즈에서 제일 가까운(그러나 작으면 안됨) 2의 제곱수를 찾는다.
//
//
//                        // then we create an array we'll copy sample data into for the FFT object
//                        // this should be as large as you want your FFT to be. generally speaking, 1024 is probably fine.
//                        int fftSize = (int)(tempPowerOfTwo(leftChannel.length / 500));
//                        float[] fftSamples = new float[fftSize];
//                        Log.d(TAG,"sample rate: " + jingle.sampleRate());
//                        FFT fft = new FFT( fftSize, jingle.sampleRate() );
//
//                        // now analyze this buffer
//                        fft.forward( fftSamples );
//
//                        // now we'll analyze the samples in chunks
//                        int totalChunks = (leftChannel.length / fftSize) + 1;
//
//                        Log.d(TAG,"totalChunks: " + totalChunks);
//                        List<Double> outputFreqArr = new ArrayList<>();
//                        Map<String, Object> outputMap = new HashMap<String, Object>();
//
//
//                        // allocate a 2-dimensional array that will hold all of the spectrum data for all of the chunks.
//                        // the second dimension if fftSize/2 because the spectrum size is always half the number of samples analyzed.
//                        float[][] spectra = new float[totalChunks][fftSize/2];
//
//                        for(int chunkIdx = 0; chunkIdx < totalChunks; ++chunkIdx)
//                        {
//                            int chunkStartIndex = chunkIdx * fftSize;
//
//                            // the chunk size will always be fftSize, except for the
//                            // last chunk, which will be however many samples are left in source
//                            int chunkSize = Math.min( leftChannel.length - chunkStartIndex, fftSize );
//
//                            // copy first chunk into our analysis array
//                            System.arraycopy( leftChannel, // source of the copy
//                                    chunkStartIndex, // index to start in the source
//                                    fftSamples, // destination of the copy
//                                    0, // index to copy to
//                                    chunkSize // how many samples to copy
//                            );
//
//                            // if the chunk was smaller than the fftSize, we need to pad the analysis buffer with zeroes
//                            if ( chunkSize < fftSize )
//                            {
//                                // we use a system call for this
//                                Arrays.fill( fftSamples, chunkSize, fftSamples.length - 1, 0.0F );
//                            }
//
//                            // now analyze this buffer
//                            fft.forward( fftSamples );
//
//                            // and copy the resulting spectrum into our spectra array
//                            for(int i = 0; i < (fftSize / 2); ++i)
//                            {
//                                spectra[chunkIdx][i] = fft.getBand(i);
//                            }
//                        }
//
//                        jingle.close();
//
//                        double maxi = -1;
//                        double mini = 99;
//                        for(int i = 0; i < spectra.length; i++) {
//                            double sum = 0;
//                            for(int j = 0; j < spectra[i].length; j++) {
//                                // System.out.print(spectra[i][j] + " ");
//                                sum += spectra[i][j];
//                            }
//                            double ele = (sum / spectra[i].length);
//                            outputFreqArr.add( ele );
//                            if(maxi < ele) {
//                                maxi = ele;
//                            } else if (mini > ele){
//                                mini = ele;
//                            }
//                        }
//
//                        Log.d(TAG,"max: " + maxi);
//                        Log.d(TAG,"min: " + mini);
//                        outputMap.put("max", maxi);
//                        outputMap.put("min", mini);
//                        outputMap.put("totalChunks;", totalChunks);
//                        outputMap.put("outputFreqArr", outputFreqArr);
//
//                        Log.d(TAG,"max: " + maxi);
//                        Log.d(TAG,"mini: " + mini);
//                        Log.d(TAG,"totalChunks: " + totalChunks);
//                        Log.d(TAG,"outputFreqArr: " + outputFreqArr);
//
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
            }
        });

        st.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });
    }

    private void pause() {
        if (this.mAudioPlayer != null) {
            this.mAudioPlayer.pause();
        }
    }

    private void play() {
        releaseAudioPlayer();

        mAudioPlayer = new AudioStreamPlayer(this);
        mAudioPlayer.setUrlString(musicName);
        mAudioPlayer.setOnAudioStreamInterface(this);

        mAudioPlayer.play();
    }

    private void releaseAudioPlayer() {
        if (this.mAudioPlayer != null) {
            this.mAudioPlayer.stop();
            this.mAudioPlayer.release();
            this.mAudioPlayer = null;
        }
    }

    private void stop() {
        if (this.mAudioPlayer != null) {
            this.mAudioPlayer.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stop();
    }

    @Override
    public void onAudioPlayerPCMData(ByteBuffer pcmBuffer) {

        if(pcmBuffer.hasRemaining()){
            Log.d(TAG,"onAudioPlayerPCMData pcmData has data");
        }
        else{
            Log.d(TAG,"onAudioPlayerPCMData pcmData hasn't data");
        }

//        double[] pcmData = new double[8192];
//
//        int y = 0;
//
//        for( ; pcmBuffer.remaining() > 2;) {
//            short s = pcmBuffer.getShort();
//
//            System.out.println("s : " + s);
//            pcmData[ y ] = (new Short(s)).doubleValue();
//            System.out.println("pcmData[ y ] : " + pcmData[ y ]);
//            ++y;
//        }
        int length = pcmBuffer.remaining();
        Log.d(TAG,"length : " + length);
        byte[] b = new byte[length];

        pcmBuffer.get(b);

        Log.d(TAG,"b[0] : " + b[0]);

        collectByte.add(b);

    }

    @Override
    public void onAudioPlayerStart(AudioStreamPlayer player) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                //updatePlayer(AudioStreamPlayer.State.Playing);
            }
        });
    }

    @Override
    public void onAudioPlayerStop(AudioStreamPlayer player) {

        handler.printHZ();

        runOnUiThread(new Runnable() {

            @Override
            public void run()
            {
                //updatePlayer(AudioStreamPlayer.State.Stopped);
            }
        });

    }

    @Override
    public void onAudioPlayerError(AudioStreamPlayer player) {
        handler.printHZ();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //updatePlayer(AudioStreamPlayer.State.Stopped);
            }
        });
    }

    @Override
    public void onAudioPlayerBuffering(AudioStreamPlayer player) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                //updatePlayer(AudioStreamPlayer.State.Buffering);
            }
        });

    }

    @Override
    public void onAudioPlayerDuration(int totalSec) {
        int h =0;
        if(totalSec >= 3600){
            h = totalSec /= 60;
        }
        int m = totalSec / 60;
        int s = totalSec % 60;

        Log.d(TAG,"onAudioPlayerDuration");

        if(totalSec >= 3600){

            Log.d(TAG,"time : " + h + ":" + + m + ":" + s);

        } else{
            Log.d(TAG,"time : " + m + ":" + s);
        }

        //        runOnUiThread(new Runnable()
//        {
//            @Override
//            public void run()
//            {
//                if (totalSec > 0)
//                {
//                    int min = totalSec / 60;
//                    int sec = totalSec % 60;
//
//                    mTextDuration.setText(String.format("%02d:%02d", min, sec));
//
//                    mSeekProgress.setMax(totalSec);
//                }
//            }
//
//        });
    }

    @Override
    public void onAudioPlayerCurrentTime(final int sec) {
        int m = sec / 60;
        int s = sec % 60;

        Log.d(TAG,"onAudioPlayerCurrentTime");
        Log.d(TAG,"time : " + m + ":" + s);
//        runOnUiThread(new Runnable()
//        {
//            @Override
//            public void run()
//            {
//                if (!isSeekBarTouch)
//                {
//                    int m = sec / 60;
//                    int s = sec % 60;
//
//                    mTextCurrentTime.setText(String.format("%02d:%02d", m, s));
//
//                    mSeekProgress.setProgress(sec);
//                }
//            }
//        });
    }

    @Override
    public void onAudioPlayerPause(AudioStreamPlayer player) {
//        runOnUiThread(new Runnable()
//        {
//            @Override
//            public void run()
//            {
//                mPlayButton.setText("Play");
//            }
//        });
    }

    String[] getMusicAbsolutePath(){
        String[] resultPath = null;
        String selectionMimeType = MediaStore.Files.FileColumns.MIME_TYPE + "=?";
        // 찾고자하는 파일 확장자명.
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp3");

        String[] selectionArgsMp3 = new String[]{ mimeType };

        Cursor c = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media.DATA}, selectionMimeType, selectionArgsMp3, null);

        if (c.getCount() == 0)
            return null;

        resultPath = new String[c.getCount()];
        while (c.moveToNext()) {
            // 경로 데이터 셋팅.
            resultPath[c.getPosition()] = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
            Log.i(TAG, resultPath[c.getPosition()]);
        }

        return resultPath;
    }

    public double tempPowerOfTwo(double numThatNotPowerOfTwo){
        double[] numList = new double[36];
        for(int power = 1; power <= 36; power++) {
            numList[power - 1] = (long) Math.pow(2, power);
        }

        double nearNum = 0;
        for(int i = 0; i < numList.length; i++) {
            if (numThatNotPowerOfTwo >= numList[35]) {
                nearNum = numList[35];
                break;
            } else if(numList[i] < numThatNotPowerOfTwo) {
                continue;
            } else if (numList[i] == numThatNotPowerOfTwo) {
                nearNum = numList[i];
                break;
            } else {
                nearNum = numList[i - 1];
                break;
            }
        }

        return nearNum;
    }
}
