/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import android.util.Log;

import java.net.Authenticator;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Date;
import java.text.SimpleDateFormat;

import com.example.android.bluetoothlegatt.ADPCMDecoder;
import android.os.Environment;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;




/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);
    public final static UUID THINGY_MICROPHONE_CHARACTERISTIC =
            UUID.fromString(SampleGattAttributes.THINGY_MICROPHONE_CHARACTERISTIC);

    private int mMtu;
    private int mtu = 276;
    private final Handler mMtuHandler;
    private AudioTrack mAudioTrack;
    private Context mContext;
    private ADPCMDecoder mAdpcmDecoder;
    private final Handler mRecordHandler;
    //记录播放状态
    private boolean isRecording = false;
    private boolean isRecordUnsave = false;
    //数字信号数组
    private byte [] noteArray;
    //PCM文件
    private File pcmFile;
    //WAV文件
    private File wavFile;
    //文件输出流
    private OutputStream os;
    //文件根目录
    private String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/thingy_voice";
    //wav文件目录
    SimpleDateFormat format = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
    Date date = new Date(System.currentTimeMillis());
    private String outFileName = basePath+"/agatha_"+format.format(date)+".wav";
    //pcm文件目录
    private String inFileName = basePath+"/yinfu.pcm";


    //Receive from DeviceControlActivity
    public static final String ACTION_START_RECORDING_SERVICE = "ACTION_START_RECORDING_SERVICE";
    public static final String ACTION_STOP_RECORDING_SERVICE = "ACTION_STOP_RECORDING_SERVICE";
    public static final String ACTION_START_TRANS_WAV = "ACTION_START_TRANS_WAV";
    //send to DeviceControlActivity
    public static final String ACTION_FINISH_WAV ="ACTION_FINISH_WAV";
    public static final String ACTION_RECV_MIC_STREAM ="ACTION_RECV_MIC_STREAM";


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null && !action.isEmpty())
                switch (action){
                    case ACTION_START_RECORDING_SERVICE:
                        Log.i(TAG,"ACTION_START_RECORDING_SERVICE");
                        createFile();
                        startRecord();
                        break;
                    case ACTION_STOP_RECORDING_SERVICE:
                        Log.i(TAG,"ACTION_STOP_RECORDING_SERVICE");
                        stopRecord();
                        break;
                    case ACTION_START_TRANS_WAV:
                        Log.i(TAG,"ACTION_START_TRANS_WAV");
                        convertWaveFile();
                        break;

                }
            }
        return super.onStartCommand(intent, flags, startId);
    }

    public void createFile(){
        File baseFile = new File(basePath);

        if(!baseFile.exists())
            baseFile.mkdirs();
        pcmFile = new File(basePath+"/yinfu.pcm");
        wavFile = new File(basePath+"/agatha.wav");
        if(pcmFile.exists()){
            pcmFile.delete();
        }
        if(wavFile.exists()){
            wavFile.delete();
        }
        try{
            boolean i = pcmFile.createNewFile();
            Log.i(TAG,"create pcmfile:"+i);
            boolean j =wavFile.createNewFile();
            Log.i(TAG,"create pcmfile:"+j+",base"+basePath);
            os = new BufferedOutputStream(new FileOutputStream(pcmFile));
        }catch(IOException e){
            Log.i(TAG,e.toString());
        }
    }

    public void startRecord(){
        isRecording = true;
    }

    public void stopRecord(){
        isRecording = false;
    }

    public void convertWaveFile(){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = 16000;
        int channels = 1;
        long byteRate = 16 *longSampleRate* channels / 8;
        byte[] data = new byte[512];
        try{
            in = new FileInputStream(inFileName);

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            String outFileName_new = basePath+"/agatha_"+format.format(date)+".wav";
            out = new FileOutputStream(outFileName_new);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();

        }catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        String outFileName_new = basePath+"/agatha_"+format.format(date)+".wav";
        final Intent intent = new Intent(ACTION_FINISH_WAV);
        intent.putExtra(EXTRA_DATA,outFileName_new);
        sendBroadcast(intent);

    }
    //https://blog.csdn.net/chezi008/article/details/53064604
    //https://blog.csdn.net/tong5956/article/details/82687001
    //https://blog.xuite.net/john75310/wretch/137622947-%5BAndroid%5D+AudioRecord+%E9%8C%84%E8%A3%BD+Wav+File
    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen, long totalDataLen, long longSampleRate,
                                     int channels, long byteRate) throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);//数据大小
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';//WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //FMT Chunk
        header[12] = 'f'; // 'fmt '
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';//过渡字节
        //数据大小
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        //通道数
        header[22] = (byte) channels;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte) (1 * 16 / 8);
        header[33] = 0;
        //每个样本的数据位数
        header[34] = 16;
        header[35] = 0;
        //Data chunk
        header[36] = 'd';//data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS){
                Log.i(TAG, "onMtuChanged() " + mtu + " Status: " + status);
                mMtu = mtu;
            }
            else
            {
                Log.i(TAG, "MTU configuration failed with error:"+ status);
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                mMtuHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "requestMtu:"+mMtu);
                        if (mBluetoothGatt != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            {
                                while(mMtu!=276)
                                {
                                    Log.i(TAG, "mMtu length"+mMtu);
                                    if (mMtu < mtu) {
                                        boolean isMtuRequestSuccess = mBluetoothGatt.requestMtu(276);
                                        Log.i(TAG, "mMtu length in request="+isMtuRequestSuccess);
                                    }
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e){
                                        e.printStackTrace();
                                    }
                                }
                            } else {
                                mMtu = 23;
                                Log.i(TAG, "mMtu length in else"+mMtu);
                            }
                        }
                    }
                }, 1000);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG, "characteristic change");
            /*
            if(THINGY_MICROPHONE_CHARACTERISTIC.equals(characteristic.getUuid()))
            {
                if(mAdpcmDecoder != null)
                {
                    if(mMtu == 276)
                    {
                        final byte[] temp = characteristic.getValue();
                        final byte[] data = new byte[131];

                        System.arraycopy(temp, 0, data, 0,131);
                        Log.i(TAG, "recv mic"+temp.toString()+" len="+temp.length);
                        mAdpcmDecoder.add(data);
                    }
                }
            }
            else{
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
            * */
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);


        }



    };

    public BluetoothLeService() {
        mContext = this;
        mMtuHandler = new Handler();
        mRecordHandler = new Handler();
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        }
        else if(THINGY_MICROPHONE_CHARACTERISTIC.equals(characteristic.getUuid()))
        {
            if(mAdpcmDecoder != null)
            {
                if(mMtu == 276)
                {
                    final byte[] temp = characteristic.getValue();
                    final byte[] data = new byte[131];

                    System.arraycopy(temp, 0, data, 0,131);
                    //Log.i(TAG, "recv mic"+temp.toString()+" len="+temp.length);
                    mAdpcmDecoder.add(data);
                    mAdpcmDecoder.setListener(new ADPCMDecoder.DecoderListener(){
                        @Override
                        public void onFrameDecoded(byte[] pcm, int frameNumber){
                            //Log.i(TAG,"加入pcm:"+pcm.toString()+"length:"+pcm.length);
                            if(isRecording){
                                try{
                                    os.write(pcm);
                                }catch (IOException e){

                                }
                            }else if (os != null) {
                                try {
                                    os.close();
                                }catch (IOException e){

                                }
                            }else {
                                final Intent intent = new Intent(ACTION_RECV_MIC_STREAM);
                                intent.putExtra(EXTRA_DATA, "加入pcm:"+pcm.toString()+"length:"+pcm.length);
                            }
                            /*這是用來撥放的，千萬不要刪掉!!
                            if(mAudioTrack != null) {
                                //final int status = mAudioTrack.write(pcm, 0, pcm.length);
                                Log.i(TAG,"正在寫入pcm:"+pcm.toString()+"length:"+pcm.length);
                                System.arraycopy(pcm, 0, noteArray, 0, pcm.length);
                            }else{
                                mAudioTrack.stop();
                                mAudioTrack.release();
                                mAudioTrack = null;
                                mAdpcmDecoder = null;
                                Log.i(TAG,"pcm 已停止...");
                            }
                            * */
                        }
                    });

                }
            }
        }
        else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                Log.i(TAG, "recv"+data.toString());
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    private void enableAdpcmMode(final boolean enable)
    {
        int bufferSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
        mAudioTrack.play();
        mAdpcmDecoder = new ADPCMDecoder(mContext,false);
        /*
        mAdpcmDecoder.setListener(new ADPCMDecoder.DecoderListener() {
            @Override
            public void onFrameDecoded(byte[] pcm, int frameNumber) {
                if(mAudioTrack != null) {
                    final int status = mAudioTrack.write(pcm, 0, pcm.length);
                    Log.i(TAG,"正在寫入pcm:"+pcm.toString()+"length:"+pcm.length);
                }else{
                    mAudioTrack.stop();
                    mAudioTrack.release();
                    mAudioTrack = null;
                    mAdpcmDecoder = null;
                    Log.i(TAG,"pcm 已停止...");
                }

            }
        });
        * */
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    private enum RequestType {
        READ_CHARACTERISTIC,
        READ_DESCRIPTOR,
        WRITE_CHARACTERISTIC,
        WRITE_DESCRIPTOR
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */


    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
        else if(THINGY_MICROPHONE_CHARACTERISTIC.equals(characteristic.getUuid()))
        {
            final BluetoothGattDescriptor microphoneDescriptor = characteristic.getDescriptor( UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            microphoneDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(microphoneDescriptor);

            enableAdpcmMode(true);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
