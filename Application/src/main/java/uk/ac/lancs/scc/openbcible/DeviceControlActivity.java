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

/*
* This file is a modified version of https://github.com/googlesamples/android-BluetoothLeGatt
* This Activity is started by the DeviceScanActivity and is passed a device name and address to start with
* Clicking on the listed device, opens up a page which allows connection and for scanning of GATT Services available for the device
* */
//package com.example.android.openbciBLE;
package uk.ac.lancs.scc.openbcible;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import java.util.Date;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

//JG START
//FROM NEURODORO

//JG END

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = "OpenBCIBLE/"+ DeviceControlActivity.class.getSimpleName();

    final static double MCP3912_Vref = 1.2f;  //reference voltage for MCP3912 chip.  set by its hardware
    //	final static double ADS1299_gain = 24;  //assumed gain setting for ADS1299.  set by its Arduino code: Apparently don't need: see difference between Ganglion/MCP3912 from link in next line and cyton found here: http://docs.openbci.com/Hardware/03-Cyton_Data_Format
    final static double scale_fac_uVolts_per_count = (double) (MCP3912_Vref * 8388607.0 * 1.5 * 51.0);  //JG equation found in MCP3912 hardware or here: http://docs.openbci.com/Hardware/08-Ganglion_Data_Format

    int finalSample = 0;
    // Filename for each session JG START
    private String mFilenamePrefix = "openbci";
    private String mExtention = ".csv";
    private String mFilenameSuffix = "";
    private String mFilename = "openbci.txt"; // Default Filename
    //JG END
    //jg start
    private File directory = new File(
            Environment.getExternalStorageDirectory(), "OpenBCI");

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    //jg end

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final byte[] mCommands = {'b','s'};
    private static int mCommandIdx = 0;
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyOnRead;

    private boolean mIsDeviceGanglion;
    private boolean mIsDeviceCyton;
    private boolean mIsUnknownCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            Log.v(TAG,"Trying to connect to GATTServer on: "+mDeviceName+" Address: "+mDeviceAddress );
            mBluetoothLeService.connect(mDeviceAddress);
            mCommandIdx = 0;

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public double lastSampleCh1 = 0;
    public double lastSampleCh2 = 0;
    public double lastSampleCh3 = 0;
    public double lastSampleCh4 = 0;
    public int lastPacket = 0;
    public List<Double> sampleBuffer = new ArrayList<Double>();
    public List<double[]> sampleBufferdouble = new ArrayList<double[]>();




    public double lastSampleCh1s1 = 0;
    public double lastSampleCh2s1 = 0;
    public double lastSampleCh3s1 = 0;
    public double lastSampleCh4s1 = 0;




    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.v(TAG,"GattServer Connected");
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.v(TAG,"GattServer Disconnected");
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.v(TAG,"GattServer Services Discovered");
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));


                //JG START
                byte[] data = intent
                        .getByteArrayExtra(BluetoothLeService.EXTRA_DATA);


                //byte[] packetData = Arrays.copyOfRange(data, 1, data.length);
                int packetNumber = data[0] & 0xFF;

                if (packetNumber == 0) {

                    System.out.println("SIZE: " + sampleBuffer.size());

                    //convert Double object list array to double primitive array
                    //double[] newData = ArrayUtils.toPrimitive(sampleBuffer.toArray(new Double[sampleBuffer.size()]));
                    //System.out.println("newData newData newData newData newData: " + newData);
                    //System.out.println("newData1 newData1 newData1 newData1 newData1: " + newData[1]);

                    //String[] newDataStr = new String[newData.length];
                    //for (int i = 0; i < newDataStr.length; i++)
                    //    newDataStr[i] = String.valueOf(newData[i]);
                    //System.out.println("newDataStr1 newDataStr1 newDataStr1 newDataStr1 newDataStr1: " + newDataStr[1]);

                    if (lastPacket > 0) {
                        double[] target = new double[sampleBuffer.size()];
                        for (int i = 0; i < target.length; i++) {
                            //    target[i] = sampleBuffer.get(i).doubleValue();  // java 1.4 style
                            // or:
                            target[i] = sampleBuffer.get(i);                // java 1.5+ style (outboxing)
                        }

                        //System.out.println("Arrays.toString(target): " + Arrays.toString(target));
                        //System.out.println("target.length: " + target.length);
                        //System.out.println("target: " + target);
                    }





                    //Let's get the decompressed ganglion data from packet 0!
                    //Channel 1 packet 0
                    byte[] packetDataZeroCh1 = Arrays.copyOfRange(data, 1, 4);
                    String packetDataZeroCh1S0 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh1[0] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh1S1 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh1[1] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh1S2 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh1[2] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh1Sfinal = packetDataZeroCh1S0 + packetDataZeroCh1S1 + packetDataZeroCh1S2;
                    //String packetDataZeroCh1Sfinalsub = packetDataZeroCh1Sfinal.substring(23, 24);
                    String packetDataZeroCh1Sfinal2 = "00000000" + packetDataZeroCh1Sfinal;
                    int packetDataZeroCh1Int = Integer.parseInt(packetDataZeroCh1Sfinal2, 2);
                    //System.out.println("packetDataZeroCh1 uV: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh1Sfinal2, 2));
                    lastSampleCh1 = (scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh1Sfinal2, 2));
                    //channel1 = Integer.parseInt(packetDataZeroCh1Sfinal2, 2);
                    //List<Double> sampleBuffer = new ArrayList<Double>();
                    sampleBuffer.clear();
                    sampleBuffer.add((scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh1Sfinal2, 2)));

                    //Channel 2 packet 0
                    byte[] packetDataZeroCh2 = Arrays.copyOfRange(data, 3, 6);
                    String packetDataZeroCh2S0 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh2[0] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh2S1 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh2[1] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh2S2 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh2[2] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh2Sfinal = packetDataZeroCh2S0 + packetDataZeroCh2S1 + packetDataZeroCh2S2;
                    //String packetDataZeroCh2Sfinalsub = packetDataZeroCh2Sfinal.substring(23, 24);
                    String packetDataZeroCh2Sfinal2 = "00000000" + packetDataZeroCh2Sfinal;
                    int packetDataZeroCh2Int = Integer.parseInt(packetDataZeroCh2Sfinal2, 2);
                    //System.out.println("packetDataZeroCh2 uV: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh2Sfinal2, 2));
                    lastSampleCh2 = (scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh2Sfinal2, 2));
                    //channel1 = Integer.parseInt(packetDataZeroCh2Sfinal2, 2);
                    //List<Double> sampleBuffer = new ArrayList<Double>();
                    sampleBuffer.clear();
                    sampleBuffer.add((scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh2Sfinal2, 2)));


                    //Channel 3 packet 0
                    byte[] packetDataZeroCh3 = Arrays.copyOfRange(data, 3, 6);
                    String packetDataZeroCh3S0 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh3[0] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh3S1 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh3[1] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh3S2 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh3[2] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh3Sfinal = packetDataZeroCh3S0 + packetDataZeroCh3S1 + packetDataZeroCh3S2;
                    //String packetDataZeroCh3Sfinalsub = packetDataZeroCh3Sfinal.substring(23, 24);
                    String packetDataZeroCh3Sfinal2 = "00000000" + packetDataZeroCh3Sfinal;
                    int packetDataZeroCh3Int = Integer.parseInt(packetDataZeroCh3Sfinal2, 2);
                    //System.out.println("packetDataZeroCh3 uV: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh3Sfinal2, 2));
                    lastSampleCh3 = (scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh3Sfinal2, 2));
                    //channel1 = Integer.parseInt(packetDataZeroCh3Sfinal2, 2);
                    //List<Double> sampleBuffer = new ArrayList<Double>();
                    sampleBuffer.clear();
                    sampleBuffer.add((scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh3Sfinal2, 2)));

                    //Channel 3 packet 0
                    byte[] packetDataZeroCh4 = Arrays.copyOfRange(data, 3, 6);
                    String packetDataZeroCh4S0 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh4[0] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh4S1 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh4[1] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh4S2 = String.format("%8s", Integer.toBinaryString(packetDataZeroCh4[2] & 0xFF)).replace(' ', '0');
                    String packetDataZeroCh4Sfinal = packetDataZeroCh4S0 + packetDataZeroCh4S1 + packetDataZeroCh4S2;
                    //String packetDataZeroCh4Sfinalsub = packetDataZeroCh4Sfinal.substring(23, 24);
                    String packetDataZeroCh4Sfinal2 = "00000000" + packetDataZeroCh4Sfinal;
                    int packetDataZeroCh4Int = Integer.parseInt(packetDataZeroCh4Sfinal2, 2);
                    //System.out.println("packetDataZeroCh4 uV: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh4Sfinal2, 2));
                    lastSampleCh4 = (scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh4Sfinal2, 2));
                    //channel1 = Integer.parseInt(packetDataZeroCh4Sfinal2, 2);
                    //List<Double> sampleBuffer = new ArrayList<Double>();
                    sampleBuffer.clear();
                    sampleBuffer.add((scale_fac_uVolts_per_count*Integer.parseInt(packetDataZeroCh4Sfinal2, 2)));
                    //Save last packet number
                    lastPacket = packetNumber;


                    Date currentTime = Calendar.getInstance().getTime();
                    Log.d(TAG, "currentTime: " + currentTime);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd  HH:mm");

                    String filename = sdf.format(currentTime) + ".txt";
                    Log.d(TAG, "filename: " + filename);
                    //String filename = new String("OpenBCI_Test.txt");
                    File directory = new File(Environment.getExternalStorageDirectory(),
                            "OpenBCI");
                    File file = new File(directory, filename);
                    DataOutputStream dos;
                    try {
                        dos = new DataOutputStream(new FileOutputStream(file.getPath(),
                                true));
                        dos.writeChars(String.valueOf(lastSampleCh1));    //JG
                        dos.writeChars(", ");
                        dos.writeChars(String.valueOf(lastSampleCh2));    //JG
                        dos.writeChars(", ");
                        dos.writeChars(String.valueOf(lastSampleCh3));    //JG
                        dos.writeChars(", ");
                        dos.writeChars(String.valueOf(lastSampleCh4));    //JG
                        dos.writeChars(", ");
                        //dos.writeChars(Float.toString((scale_fac_uVolts_per_count * Integer.parseInt(AddStringsFullCh1s2, 2))+lastSample));    //JG
                        //dos.writeChars(auxNumberString);
                        dos.write(System.getProperty("line.separator").getBytes());
                        dos.close();
                    } catch (FileNotFoundException e) {
                        Log.e("Save to File AsyncTask", "Error finding file");
                    } catch (IOException e) {
                        Log.e("Save to File AsyncTask", "Error saving data");
                    }


                }



                //Time to decompress ganglion data! http://docs.openbci.com/Hardware/08-Ganglion_Data_Format
                if (packetNumber > 99 && packetNumber < 201) {


                    //Channel 1 SAMPLE 1=====================================================================================
                    byte[] packetDataCh1s1 = Arrays.copyOfRange(data, 1, 4);
                    String Byte2str0Ch1s1 = String.format("%8s", Integer.toBinaryString(packetDataCh1s1[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch1s1 = String.format("%8s", Integer.toBinaryString(packetDataCh1s1[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch1s1 = String.format("%8s", Integer.toBinaryString(packetDataCh1s1[2] & 0xFF)).replace(' ', '0');
                    String AddStringsCh1s1 = (Byte2str0Ch1s1 + Byte2str1Ch1s1 + Byte2str2Ch1s1);
                    String AddStringsCh1s1Sub = AddStringsCh1s1.substring(0, 19);
                    //String Byte2str2subfirstCh1s1 = AddStringsCh1s1Sub.substring(0, 1);
                    String Byte2str2sublastCh1s1 = AddStringsCh1s1Sub.substring(18, 19);
                    //If MSB (first bit) but LSB is 1 (last bit), for Ganglgion this is NEGATIVE
                    //so we append a 1 on the begining of the final 19 bit string to make it negative
                    //and then send it through the sign thing below
                    if (Integer.parseInt(Byte2str2sublastCh1s1) == 1) {
                        String AddStringsFullCh1s1 = "1111111111111" + AddStringsCh1s1Sub; //add a 1 to MSB first slot
                        //The next block of code converts binary string to its compliment and finds its signed int
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh1s1.toCharArray()) {
                            // if bit is '0', append a 1. if bit is '1', append a 0.
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        //System.out.println(onesComplement); // should be the NOT of binString
                        int converted = Integer.valueOf(onesComplement, 2);
                        // two's complement = one's complement + 1. This is the positive value
                        // of our original binary string, so make it negative again.
                        int valueCh1s1 = -(converted + 1);
                        //System.out.println("MicrovoltsCh1s1: " + packetNumber + " "  + scale_fac_uVolts_per_count*valueCh1s1);
                        //System.out.println("PackNumSigned: " + packetNumber);

                        //grab ch1 even sample and append to buffer
                        lastSampleCh1 = (scale_fac_uVolts_per_count*valueCh1s1) + lastSampleCh1;
//                        System.out.println("lastSample1: " + packetNumber + " " + lastSample);
                        sampleBuffer.add(lastSampleCh1);

                        //Here we check if uncompressed packet 0 is missed.
                        //If so, clear the buffer, and add the last sample to find differnce
                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
//                            System.out.println("packetNumberInt - lastPacketInt: " + (packetNumberInt - lastPacketInt));
//                            System.out.println("packetNumberInt:              " + packetNumberInt);
//                            System.out.println("lastPacketInt:                " + lastPacketInt);
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh1);
                        }

                    } else {
                        //If LSB is positive, it is unsigned, and we convert to 32bit int
                        //and multiply by scale factor to find uV of ch1 even sample
                        String AddStringsFullCh1s1 = "0000000000000" + AddStringsCh1s1Sub;
                        //System.out.println("MicrovoltsCh1s1: " + packetNumber + " "  + scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s1, 2));

                        //grab ch1 even sample and append to buffer
                        lastSampleCh1 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s1, 2)) + lastSampleCh1;
//                        System.out.println("lastSample1: " + packetNumber + " " + lastSample);
                        sampleBuffer.add(lastSampleCh1);

                        //System.out.println("PackNumUnsigned: " + packetNumber);

                        //double packetNumberDouble = packetNumber;
                        //Here we check if uncompressed packet 0 is missed.
                        //If so, we clear the buffer, and add the last sample to find difference
                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            //System.out.println("packetNumberInt - lastPacketInt: " + (packetNumberInt - lastPacketInt));
                            //System.out.println("packetNumberInt:              " + packetNumberInt);
                            //System.out.println("lastPacketInt:                " + lastPacketInt);
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh1);
                        }
                    }
                    //Channel 1 SAMPLE 1=====================================================================================


                    //Channel 2 SAMPLE 1=====================================================================================
                    byte[] packetDataCh2s1 = Arrays.copyOfRange(data, 3, 6);
                    String Byte2str0Ch2s1 = String.format("%8s", Integer.toBinaryString(packetDataCh2s1[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch2s1 = String.format("%8s", Integer.toBinaryString(packetDataCh2s1[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch2s1 = String.format("%8s", Integer.toBinaryString(packetDataCh2s1[2] & 0xFF)).replace(' ', '0');
                    String AddStringsCh2s1 = (Byte2str0Ch2s1 + Byte2str1Ch2s1 + Byte2str2Ch2s1);
                    String AddStringsCh2s1SubLong = AddStringsCh2s1.substring(0, 23);
                    String AddStringsCh2s1Sub = AddStringsCh2s1SubLong.substring(3, 22);

                    //Log.d(TAG, "AddStringsCh2s1Sub: " + AddStringsCh2s1Sub);

                    String Byte2str2sublastCh2s1 = AddStringsCh2s1Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh2s1) == 1) {
                        String AddStringsFullCh2s1 = "1111111111111" + AddStringsCh2s1Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh2s1.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh2s1 = -(converted + 1);
                        lastSampleCh2 = (scale_fac_uVolts_per_count*valueCh2s1) + lastSampleCh2;
                        sampleBuffer.add(lastSampleCh2);

                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh2);
                        }

                    } else {
                        String AddStringsFullCh2s1 = "0000000000000" + AddStringsCh2s1Sub;
                        lastSampleCh2 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh2s1, 2)) + lastSampleCh2;
                        sampleBuffer.add(lastSampleCh2);
                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh2);
                        }
                    }
                    //Channel 2 SAMPLE 1=====================================================================================


                    //Channel 3 SAMPLE 1=====================================================================================
                    byte[] packetDataCh3s1 = Arrays.copyOfRange(data, 5, 9);
                    String Byte2str0Ch3s1 = String.format("%8s", Integer.toBinaryString(packetDataCh3s1[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch3s1 = String.format("%8s", Integer.toBinaryString(packetDataCh3s1[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch3s1 = String.format("%8s", Integer.toBinaryString(packetDataCh3s1[2] & 0xFF)).replace(' ', '0');
                    String Byte2str3Ch3s1 = String.format("%8s", Integer.toBinaryString(packetDataCh3s1[3] & 0xFF)).replace(' ', '0');
                    String AddStringsCh3s1 = (Byte2str0Ch3s1 + Byte2str1Ch3s1 + Byte2str2Ch3s1 + Byte2str3Ch3s1);
                    String AddStringsCh3s1Sub = AddStringsCh3s1.substring(6, 25);
                    String Byte2str2sublastCh3s1 = AddStringsCh3s1Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh3s1) == 1) {
                        String AddStringsFullCh3s1 = "1111111111111" + AddStringsCh3s1Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh3s1.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh3s1 = -(converted + 1);
                        lastSampleCh3 = (scale_fac_uVolts_per_count*valueCh3s1) + lastSampleCh3;
                        sampleBuffer.add(lastSampleCh3);

                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh3);
                        }

                    } else {
                        String AddStringsFullCh3s1 = "0000000000000" + AddStringsCh3s1Sub;
                        lastSampleCh3 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh3s1, 2)) + lastSampleCh3;
                        sampleBuffer.add(lastSampleCh3);
                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh3);
                        }
                    }
                    //Channel 3 SAMPLE 1=====================================================================================


                    //Channel 4 SAMPLE 1=====================================================================================
                    byte[] packetDataCh4s1 = Arrays.copyOfRange(data, 8, 11);
                    String Byte2str0Ch4s1 = String.format("%8s", Integer.toBinaryString(packetDataCh4s1[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch4s1 = String.format("%8s", Integer.toBinaryString(packetDataCh4s1[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch4s1 = String.format("%8s", Integer.toBinaryString(packetDataCh4s1[2] & 0xFF)).replace(' ', '0');
                    String AddStringsCh4s1 = (Byte2str0Ch4s1 + Byte2str1Ch4s1 + Byte2str2Ch4s1);
                    String AddStringsCh4s1Sub = AddStringsCh4s1.substring(1, 21);
                    String Byte2str2sublastCh4s1 = AddStringsCh4s1Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh4s1) == 1) {
                        String AddStringsFullCh4s1 = "1111111111111" + AddStringsCh4s1Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh4s1.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh4s1 = -(converted + 1);
                        lastSampleCh4 = (scale_fac_uVolts_per_count*valueCh4s1) + lastSampleCh4;
                        sampleBuffer.add(lastSampleCh4);

                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh4);
                        }

                    } else {
                        String AddStringsFullCh4s1 = "0000000000000" + AddStringsCh4s1Sub;
                        lastSampleCh4 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh4s1, 2)) + lastSampleCh4;
                        sampleBuffer.add(lastSampleCh4);
                        int packetNumberInt = packetNumber;
                        int lastPacketInt = lastPacket;
                        if ((packetNumberInt - lastPacketInt) < 0) {
                            sampleBuffer.clear();
                            sampleBuffer.add(lastSampleCh4);
                        }
                    }
                    //Channel 4 SAMPLE 1=====================================================================================

                    lastSampleCh1s1 = lastSampleCh1;
                    lastSampleCh2s1 = lastSampleCh2;
                    lastSampleCh3s1 = lastSampleCh3;
                    lastSampleCh4s1 = lastSampleCh4;


                    //Channel 1 SAMPLE 2=====================================================================================
                    byte[] packetDataCh1s2 = Arrays.copyOfRange(data, 10, 13);
                    String Byte2str0Ch1s2 = String.format("%8s", Integer.toBinaryString(packetDataCh1s2[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch1s2 = String.format("%8s", Integer.toBinaryString(packetDataCh1s2[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch1s2 = String.format("%8s", Integer.toBinaryString(packetDataCh1s2[2] & 0xFF)).replace(' ', '0');
                    String AddStringsCh1s2 = (Byte2str0Ch1s2 + Byte2str1Ch1s2 + Byte2str2Ch1s2);
                    String AddStringsCh1s2Sub = AddStringsCh1s2.substring(4, 23);
                    String Byte2str2sublastCh1s2 = AddStringsCh1s2Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh1s2) == 1) {
                        String AddStringsFullCh1s2 = "11111" + AddStringsCh1s2Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh1s2.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh1s2 = -(converted + 1);
                        lastSampleCh1 = (scale_fac_uVolts_per_count*valueCh1s2) + lastSampleCh1;
                        sampleBuffer.add(lastSampleCh1);
                        lastPacket = packetNumber;
                    } else {
                        String AddStringsFullCh1s2 = "00000" + AddStringsCh1s2Sub;
                        lastSampleCh1 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh1s2, 2)) + lastSampleCh1;
                        sampleBuffer.add(lastSampleCh1);
                        lastPacket = packetNumber;
                    }
                    //Channel 1 SAMPLE 2=====================================================================================


                    //Channel 2 SAMPLE 2=====================================================================================
                    byte[] packetDataCh2s2 = Arrays.copyOfRange(data, 12, 16);
                    String Byte2str0Ch2s2 = String.format("%8s", Integer.toBinaryString(packetDataCh2s2[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch2s2 = String.format("%8s", Integer.toBinaryString(packetDataCh2s2[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch2s2 = String.format("%8s", Integer.toBinaryString(packetDataCh2s2[2] & 0xFF)).replace(' ', '0');
                    String Byte2str3Ch2s2 = String.format("%8s", Integer.toBinaryString(packetDataCh2s2[3] & 0xFF)).replace(' ', '0');
                    String AddStringsCh2s2 = (Byte2str0Ch2s2 + Byte2str1Ch2s2 + Byte2str2Ch2s2 + Byte2str3Ch2s2);
                    String AddStringsCh2s2Sub = AddStringsCh2s2.substring(7, 26);
                    String Byte2str2sublastCh2s2 = AddStringsCh2s2Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh2s2) == 1) {
                        String AddStringsFullCh2s2 = "11111" + AddStringsCh2s2Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh2s2.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh2s2 = -(converted + 1);
                        lastSampleCh2 = (scale_fac_uVolts_per_count*valueCh2s2) + lastSampleCh2;
                        sampleBuffer.add(lastSampleCh2);
                        lastPacket = packetNumber;
                    } else {
                        String AddStringsFullCh2s2 = "00000" + AddStringsCh2s2Sub;
                        lastSampleCh2 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh2s2, 2)) + lastSampleCh2;
                        sampleBuffer.add(lastSampleCh2);
                        lastPacket = packetNumber;
                    }
                    //Channel 2 SAMPLE 2=====================================================================================


                    //Channel 3 SAMPLE 2=====================================================================================
                    byte[] packetDataCh3s2 = Arrays.copyOfRange(data, 15, 18);
                    String Byte2str0Ch3s2 = String.format("%8s", Integer.toBinaryString(packetDataCh3s2[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch3s2 = String.format("%8s", Integer.toBinaryString(packetDataCh3s2[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch3s2 = String.format("%8s", Integer.toBinaryString(packetDataCh3s2[2] & 0xFF)).replace(' ', '0');
                    String AddStringsCh3s2 = (Byte2str0Ch3s2 + Byte2str1Ch3s2 + Byte2str2Ch3s2);
                    String AddStringsCh3s2Sub = AddStringsCh3s2.substring(2, 21);
                    String Byte2str2sublastCh3s2 = AddStringsCh3s2Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh3s2) == 1) {
                        String AddStringsFullCh3s2 = "11111" + AddStringsCh3s2Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh3s2.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh3s2 = -(converted + 1);
                        lastSampleCh3 = (scale_fac_uVolts_per_count*valueCh3s2) + lastSampleCh3;
                        sampleBuffer.add(lastSampleCh3);
                        lastPacket = packetNumber;
                    } else {
                        String AddStringsFullCh3s2 = "00000" + AddStringsCh3s2Sub;
                        lastSampleCh3 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh3s2, 2)) + lastSampleCh3;
                        sampleBuffer.add(lastSampleCh3);
                        lastPacket = packetNumber;
                    }
                    //Channel 3 SAMPLE 2=====================================================================================


                    //Channel 4 SAMPLE 2=====================================================================================
                    byte[] packetDataCh4s2 = Arrays.copyOfRange(data, 17, 20);
                    String Byte2str0Ch4s2 = String.format("%8s", Integer.toBinaryString(packetDataCh4s2[0] & 0xFF)).replace(' ', '0');
                    String Byte2str1Ch4s2 = String.format("%8s", Integer.toBinaryString(packetDataCh4s2[1] & 0xFF)).replace(' ', '0');
                    String Byte2str2Ch4s2 = String.format("%8s", Integer.toBinaryString(packetDataCh4s2[2] & 0xFF)).replace(' ', '0');
                    String AddStringsCh4s2 = (Byte2str0Ch4s2 + Byte2str1Ch4s2 + Byte2str2Ch4s2);
                    String AddStringsCh4s2Sub = AddStringsCh4s2.substring(5, 24);
                    String Byte2str2sublastCh4s2 = AddStringsCh4s2Sub.substring(18, 19);
                    if (Integer.parseInt(Byte2str2sublastCh4s2) == 1) {
                        String AddStringsFullCh4s2 = "11111" + AddStringsCh4s2Sub;
                        StringBuilder onesComplementBuilder = new StringBuilder();
                        for (char bit : AddStringsFullCh4s2.toCharArray()) {
                            onesComplementBuilder.append((bit == '0') ? 1 : 0);
                        }
                        String onesComplement = onesComplementBuilder.toString();
                        int converted = Integer.valueOf(onesComplement, 2);
                        int valueCh4s2 = -(converted + 1);
                        lastSampleCh4 = (scale_fac_uVolts_per_count*valueCh4s2) + lastSampleCh4;
                        sampleBuffer.add(lastSampleCh4);
                        lastPacket = packetNumber;
                    } else {
                        String AddStringsFullCh4s2 = "00000" + AddStringsCh4s2Sub;
                        lastSampleCh4 = (scale_fac_uVolts_per_count*Integer.parseInt(AddStringsFullCh4s2, 2)) + lastSampleCh4;
                        sampleBuffer.add(lastSampleCh4);
                        lastPacket = packetNumber;
                    }
                    //Channel 4 SAMPLE 2=====================================================================================

                }

                //SAVE DATA THROUGH CLASS
                //byte[][] dataForAsyncTask = { mFilename.getBytes(), data };
                //new SaveBytesToFileJG().execute(dataForAsyncTask);


            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        //if it is a characteristic related view item, get the characteristic
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();

                        if(mIsDeviceGanglion){
                            if(SampleGattAttributes.UUID_GANGLION_SEND.equals(characteristic.getUuid().toString())){
                                //we use this only when the device is a ganglion
                                char c = toggleDataStream(characteristic);
                                Toast.makeText(getApplicationContext(), "Sent: '"+c+"' to Ganglion", Toast.LENGTH_SHORT).show();
                            }

                            if(SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(characteristic.getUuid().toString())){
                                //check if we have registered for notifications
                                boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,"Ganglion RECEIVE");
                                if(updateNotifyOnRead) mNotifyOnRead = characteristic;

                                //also read it for just now
                                mBluetoothLeService.readCharacteristic(characteristic);
                            }

                            if(SampleGattAttributes.UUID_GANGLION_DISCONNECT.equals(characteristic.getUuid().toString())){
                                Log.v(TAG,"Not sure what the disconnect characteristic does");
                                Toast.makeText(getApplicationContext(), "Disconnect Not Actionable", Toast.LENGTH_SHORT).show();
                            }
                            return true;//all done, return
                        }

                        if(mIsDeviceCyton){
                            if(SampleGattAttributes.UUID_CYTON_SEND.equals(characteristic.getUuid().toString())){
                                //we use this only when the device is a ganglion
                                char c = toggleDataStream(characteristic);
                                Toast.makeText(getApplicationContext(), "Sent: '"+c+"' to Cyton", Toast.LENGTH_SHORT).show();
                            }

                            if(SampleGattAttributes.UUID_CYTON_RECEIVE.equals(characteristic.getUuid().toString())){
                                //check if we have registered for notifications
                                boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,"Cyton RECEIVE");
                                if(updateNotifyOnRead) mNotifyOnRead = characteristic;

                                //also read it for just now
                                mBluetoothLeService.readCharacteristic(characteristic);
                            }

                            if(SampleGattAttributes.UUID_CYTON_DISCONNECT.equals(characteristic.getUuid().toString())){
                                Log.v(TAG,"Not sure what the disconnect characteristic does");
                                Toast.makeText(getApplicationContext(), "Disconnect Not Actionable", Toast.LENGTH_SHORT).show();
                            }
                            return true;//all done, return
                        }


                        //If here, this is either not a Cyton/Ganglion board OR not the 3 primary characteristics

                        //specific readable characteristics

                        //sample test using battery level service (PlayStore:BLE Peripheral Simulator) BATTERY_LEVEL characteristic notifies
                        if(SampleGattAttributes.UUID_BATTERY_LEVEL.equals(characteristic.getUuid().toString())){//the
                            //we set it to notify, if it isn't already on notify
                            Log.v(TAG,"Battery level notification registration");
                            boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,"Battery Level");
                            if(updateNotifyOnRead) mNotifyOnRead = characteristic;
                            mBluetoothLeService.readCharacteristic(characteristic);
                            return true;//all done, return
                        }

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            //read it immediately
                            Log.v(TAG, "Reading characteristic: "+characteristic.getUuid().toString());
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,SampleGattAttributes.getShortUUID(characteristic.getUuid()));
                            if(updateNotifyOnRead) mNotifyOnRead = characteristic;
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private char toggleDataStream(BluetoothGattCharacteristic  BLEGChar){
        char cmd = (char) mCommands[mCommandIdx];
        Log.v(TAG,"Sending Command : "+cmd);
        BLEGChar.setValue(new byte[]{(byte)cmd});
        mBluetoothLeService.writeCharacteristic((BLEGChar));
        mCommandIdx = (mCommandIdx +1)% mCommands.length; //update for next run to toggle off
        return cmd;
    }

    private boolean setCharacteristicNotification(BluetoothGattCharacteristic currentNotify, BluetoothGattCharacteristic newNotify, String toastMsg){
        if(currentNotify==null){//none registered previously
            mBluetoothLeService.setCharacteristicNotification(newNotify, true);
        }
        else {//something was registered previously
            if (!currentNotify.getUuid().equals(newNotify.getUuid())) {//we are subscribed to another characteristic?
                mBluetoothLeService.setCharacteristicNotification(currentNotify, false);//unsubscribe
                mBluetoothLeService.setCharacteristicNotification(newNotify, true); //subscribe to Receive
            }
            else{
                //no change required
                return false;
            }
        }
        Toast.makeText(getApplicationContext(), "Notify: "+toastMsg, Toast.LENGTH_SHORT).show();
        return true;//indicates reassignment needed for mNotifyOnRead
    }

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        //this activity was started by another with data stored in an intent, process it
        final Intent intent = getIntent();

        //get the devcie name and address
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        //set flags if CYTON or GANGLION is being used
        Log.v(TAG,"deviceName '"+mDeviceName+"'");
        if(mDeviceName!=null) {
            mIsDeviceCyton = mDeviceName.toUpperCase().contains(SampleGattAttributes.DEVICE_NAME_CYTON);
            mIsDeviceGanglion = mDeviceName.toUpperCase().contains(SampleGattAttributes.DEVICE_NAME_GANGLION);
        }
        else{//device name is not available
            mIsDeviceGanglion = false;mIsDeviceCyton = false;
        }

        if(mIsDeviceCyton||mIsDeviceGanglion){//if it is a desirable device
            Toast.makeText(getApplicationContext(), "OpenBCI " + (mIsDeviceCyton?"Cyton":"Ganglion") + " Connected", Toast.LENGTH_SHORT).show();
        }

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);

        Log.v(TAG,"Creating Service to Handle all further BLE Interactions");
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {

            Log.v(TAG,"Trying to connect to: "+mDeviceName+" Address: "+mDeviceAddress);
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                //the 'connect' button is clicked and this triggers the connection request
                Log.v(TAG,"Trying to connect to: "+mDeviceName+" Address: "+mDeviceAddress+ " on click");
                mBluetoothLeService.connect(mDeviceAddress);
                //the completion of the connection is returned separately
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    //JG START
    // Calculate filename for session
    private String getFileNameForSession() {
        directory.mkdir();
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy_MM_dd_HH_mm_ss", Locale.getDefault());
        Date now = new Date();
        mFilenameSuffix = formatter.format(now);
        String filename = mFilenamePrefix + mFilenameSuffix + mExtention;
        return filename;
    }
    //JG END


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            Log.w(TAG,"Service Iterator:"+gattService.getUuid());

            if(mIsDeviceGanglion){////we only want the SIMBLEE SERVICE, rest, we junk...
                if(!SampleGattAttributes.UUID_GANGLION_SERVICE.equals(gattService.getUuid().toString())) continue;
            }

            if(mIsDeviceCyton){////we only want the RFDuino SERVICE, rest, we junk...
                if(!SampleGattAttributes.UUID_CYTON_SERVICE.equals(gattService.getUuid().toString())) continue;
            }

            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

                //if this is the read attribute for Cyton/Ganglion, register for notify service
                if((SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(uuid))||(SampleGattAttributes.UUID_CYTON_RECEIVE.equals(uuid))){//the RECEIVE characteristic
                    Log.v(TAG,"Registering notify for: "+uuid);
                    //we set it to notify, if it isn't already on notify
                    if(mNotifyOnRead==null){
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mNotifyOnRead = gattCharacteristic;
                    }
                    else{
                        Log.v(TAG, "De-registering Notification for: "+mNotifyOnRead.getUuid().toString() +" first");
                        mBluetoothLeService.setCharacteristicNotification(mNotifyOnRead, false);
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mNotifyOnRead = gattCharacteristic;
                    }
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
