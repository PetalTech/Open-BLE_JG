package uk.ac.lancs.scc.openbcible;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class LocalDataActivity extends Activity {


    //NEURODORO START
    public Filter bandstopFilter;
    public double[][] bandstopFiltState;
    private double[] newData;

    private double[][] rawBuffer;
    private double[][] PSD;
    private double[] bandMeans;

    private static final int NUM_CHANNELS = 1;
    //private static final int FFT_LENGTH = 0;
    private static final int EPOCHS_PER_SECOND = 1;

    // Reference to global Muse
    private int samplingRate = 200;
    private FFT fft;
    private int nbBins;
    private BandPowerExtractor bandExtractor;
    private PSDBuffer2D psdBuffer2D;

    private double[] logpower;

    public double targetBandPass;

    int FFT_LENGTH = 0;

    // Filter variables BANDPASS
    public Filter bandPassFilter;
    public double[] bandPassFiltState2;
    public double[][] bandPassFiltState;
    // Filter variables BANDPASS
    //NEURODORO END



    private static final String TAG = LocalDataActivity.class.getSimpleName();
    private Button localDataButton;
    private ImageView localDataImage;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_data);
        localDataButton = (Button) findViewById(R.id.localDataButton);
        localDataImage = (ImageView) findViewById(R.id.localDataImage);

        localDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO ADD CODE TO RUN WHEN BUTTON IS CLICKED
            //setContentView(R.layout.menu);
                Intent myIntent = new Intent(getApplicationContext(), DeviceScanActivity.class);
                startActivityForResult(myIntent, 0);
                //return true;

            }
        });
        changeImageColor(true);
    }

    private void changeImageColor(final boolean one) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                int color = ContextCompat.getColor(LocalDataActivity.this, one ? R.color.color_one : R.color.color_two);
                localDataImage.setImageDrawable(new ColorDrawable(color));
                changeImageColor(!one);
            }

        }, 100);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        List<Double> data = new ArrayList<>();

        try {
            String str;
            StringBuffer buf = new StringBuffer();
            InputStream is = getResources().openRawResource(R.raw.data);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            if (is != null) {
                while ((str = reader.readLine()) != null) {
                    buf.append(str + "\n");
                    str = str.replace(",", "");
                    double dataValue = Double.parseDouble(str);
                    data.add(dataValue);
                }
            }

            is.close();

        } catch (IOException e) {
            Log.wtf(TAG, e);
        }

        //Log.d(TAG, "Local Data: " + data.toString());

        //TODO ADD STUFF HERE FOR DATA

        double[] target = new double[data.size()];
        //for (int i = 0; i < target.length; i++) {
        for (int i = 0; i < data.size(); i++) {
            //    target[i] = sampleBuffer.get(i).doubleValue();  // java 1.4 style
            // or:
            target[i] = data.get(i);                // java 1.5+ style (outboxing)
        }

        double[] target1 = target;




        //BAND POWER











        //WORKING BANDPASS
//        bandPassFilter = new Filter(200, "bandpass", 4, 12, 18);
//        bandPassFiltState = new double[200][bandPassFilter.getNB()];

//        double[] targetShortFull = new double[200];
//        double[] targetShortAppend = new double[data.size()];

        //for (int i = 0; i < target.length; i++) {
//        for (int i = 0; i < data.size(); i++) {
            //    target[i] = sampleBuffer.get(i).doubleValue();  // java 1.4 style
            // or:

//            targetShortFull[0] = data.get(i);                // java 1.5+ style (outboxing)

//            bandPassFiltState = bandPassFilter.transform(targetShortFull, bandPassFiltState);
//            targetShortFull = bandPassFilter.extractFilteredSamples(bandPassFiltState);

//            targetShortAppend[i] = targetShortFull[0];
//            targetShortFull = new double[200];
//        }

//        Log.d(TAG, "targetShortFull: " + Arrays.toString(targetShortFull));
//        Log.d(TAG, "targetShortAppend: " + Arrays.toString(targetShortAppend));
        //WORKING BANDPASS





        //WORKING BANDPASS
        bandPassFilter = new Filter(200, "bandpass", 4, 12, 18);
        bandPassFiltState = new double[200][bandPassFilter.getNB()];

        double[] targetShortFull = new double[200];
        double[] targetShortAppend = new double[data.size()];

        //fft = new FFT(data.size(), FFT_LENGTH, samplingRate);
        //nbBins = fft.getFreqBins().length;
        //bandExtractor = new BandPowerExtractor(fft.getFreqBins());
        //dataListener = new ClassifierDataListener();

        //int inputLength = data.size();
        //int fftLength = inputLength*2;
        //double fs = data.size();

        //for (int i = 0; i < target.length; i++) {
        for (int i = 0; i < data.size(); i++) {
            //    target[i] = sampleBuffer.get(i).doubleValue();  // java 1.4 style
            // or:

            targetShortFull[0] = data.get(i);                // java 1.5+ style (outboxing)

            bandPassFiltState = bandPassFilter.transform(targetShortFull, bandPassFiltState);
            targetShortFull = bandPassFilter.extractFilteredSamples(bandPassFiltState);

            targetShortAppend[i] = targetShortFull[0];

            targetShortFull = new double[200];
        }

        Log.d(TAG, "targetShortFull: " + Arrays.toString(targetShortFull));
        Log.d(TAG, "targetShortAppend: " + Arrays.toString(targetShortAppend));



        FFT_LENGTH = targetShortAppend.length;
        fft = new FFT(targetShortAppend.length, FFT_LENGTH, samplingRate);
        //FFT fft = new FFT(inputLength, fftLength, fs);
        double[] logpower = fft.computePSD(targetShortAppend);



        Log.d(TAG, "Arrays.toString(fft.getFreqBins()): " + Arrays.toString(fft.getFreqBins()));

        Log.d(TAG, "Arrays.toString(logpower): " + Arrays.toString(logpower));




        // Create fake time series of size `inputLength`
        //double[] values = new double[inputLength];
        //for (int i = 0; i < inputLength; i++) {
        //    values[i] = i;
        //}

        // Compute log PSD
        //double[] logpower = fft.computeLogPSD(target);







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
            dos.writeChars(Arrays.toString(target1));    //JG
            //dos.writeChars(", ");
            dos.writeChars("========================================================================== ");
            dos.writeChars(Arrays.toString(target));    //JG
            //dos.writeChars(Float.toString((scale_fac_uVolts_per_count * Integer.parseInt(AddStringsFullCh1s2, 2))+lastSample));    //JG
            //dos.writeChars(auxNumberString);
            dos.write(System.getProperty("line.separator").getBytes());
            dos.close();
        } catch (FileNotFoundException e) {
            Log.e("Save to File AsyncTask", "Error finding file");
        } catch (IOException e) {
            Log.e("Save to File AsyncTask", "Error saving data");
        }





        //Filter.extractFilteredSamples(bandPassFilter);

        //Log.d(TAG, "Filter.extractFilteredSamples(target): " + Filter.extractFilteredSamples(target));

        //Filter.extractFilteredSamples(target);



        //target = bandPassFilter.extractFilteredSamples(bandPassFiltState);
        //targetBandPass = Filter.extractFilteredSamples(target);


        //System.out.println("bandPassFilter.getNB(): " + bandPassFilter.getNB());
        //System.out.println("bandPassFilter: " + bandPassFilter);
        //System.out.println("bandPassFiltState: " + Arrays.toString(bandPassFiltState));

        //Log.d(TAG, "Arrays.toString(target) " + Arrays.toString(target));

        //bandPassFiltState = bandPassFilter.transform(target, bandPassFiltState);
        //target = bandPassFilter.extractFilteredSamples(bandPassFiltState);

        //bandPassFiltState = bandPassFilter.transform(target, bandPassFiltState);

        //Log.d(TAG, "targetBandPass: " + targetBandPass);
        //Log.d(TAG, "bandPassFiltState: " + Arrays.toString(bandPassFiltState));

        //Log.d(TAG, "Double.toString(targetBandPass): " + Double.toString(targetBandPass));

        // Optional filter for filtered data BANDPASS

    }
}