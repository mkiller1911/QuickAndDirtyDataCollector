package com.example.prak2datensammler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

//Just the Activity class doesn't do much
public class MainActivity extends AppCompatActivity {

    //onCreate is triggert on every screen turn, so this var says if it is the initial onCreate that is triggert
    private static boolean initial = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //give the DataLogger the main activity so the DataLogger can access the gui
        DataLogger.activity = this;

        //If this is the start of the app and not a screen turn
        if(initial) {

            //check and request gps access
            if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
                if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    //terminate with error -1 when there is no gps access
                    System.exit(-1);
                }
            }

            //give the sensorMmanager and the locationManager to the DataLogger
            DataLogger.sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            DataLogger.locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            //Init the Datalogger
            DataLogger.initApp();

            //Mark the next onCreate as just a screen turn
            initial = false;
        } else {
            //if this is just a screen turn rebuild all the text elements
            DataLogger.flush();
        }

    }

    //if button stand is clicked change the transportationmode tag and update the text elements
    public void onButtonStandClick(View view) {
        DataLogger.tag = "Stand";
        DataLogger.flush();
    }

    public void onButtonWalkClick(View view) {
        DataLogger.tag = "Walk";
        DataLogger.flush();
    }

    public void onButtonRunClick(View v) {
        DataLogger.tag = "Run";
        DataLogger.flush();
    }


}

//That is the class that taks the most work
class DataLogger {

    //The main activity for accessing the gui elements and stuff
    public static MainActivity activity;

    //the tag for the current transportation mode
    public  static String tag = "NO_TAG";
    //the the sensor and gps managers
    public  static SensorManager sensorManager;
    public  static LocationManager locationManager;
    //the files for storing the collected data
    private static File gpsFile;
    private static File accelFile;
    //a millisec. timestamp of the last data collection
    private static long lastDataWriteGPS = 0;
    private static long lastDataWriteAccel = 0;

    private static String log = "";

    //Starts the main things of the app
    public static void initApp() {
        //init the file vars for later writing to the files
        initNewFile();

        //start data collection
        startGPS();
        startAccel();

        //Start a thread that monitors if there is actually data collected
        new Thread() {
            @Override
            public void run() {
                //If data is collected for the beginning
                if(collectingGPSData()) {
                    //say that data is collected
                    log("INFO: Collecting GPS data.");
                    while(true) {
                        //wait until there isn't data collected
                        while(collectingGPSData()) {
                            sleepShortcut(100);
                        }
                        //say that there is no data collected
                        log("INFO: NOT Collecting GPS data.");
                        //wait until there is data collected again
                        while(!collectingGPSData()) {
                            sleepShortcut(100);
                        }
                        //say that there is data again
                        log("INFO: Collecting GPS data.");
                        //and repeat to eternity
                    }
                //very similar to the first block, just this one starts with no data collected
                } else {
                    log("INFO: NOT Collecting GPS data.");
                    while(true) {
                        while (!collectingGPSData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: Collecting GPS data.");
                        while (collectingGPSData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: NOT Collecting GPS data.");
                    }
                }
            }
        }.start();

        //very similar to the first thread, just this one looks for Accel. data
        new Thread() {
            @Override
            public void run() {
                if(collectingAccelData()) {
                    log("INFO: Collecting Accel data.");
                    while(true) {
                        while(collectingAccelData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: NOT Collecting Accel data.");
                        while(!collectingAccelData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: Collecting Accel data.");
                    }
                } else {
                    log("INFO: NOT Collecting Accel data.");
                    while(true) {
                        while (!collectingAccelData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: Collecting Accel data.");
                        while (collectingAccelData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: NOT Collecting Accel data.");
                    }
                }
            }
        }.start();
    }

    //Says if there is gps data collected a the moment
    public static boolean collectingGPSData() {
        //if the last gps data is older than 2 sec. return that there is no data collected
        if(System.currentTimeMillis() - lastDataWriteGPS > 2000)
            return false;
        else
            return true;
    }

    //vary much the same, just with accel. an 1 sec. (because accel. frequency is higher)
    public static boolean collectingAccelData() {
        if(System.currentTimeMillis() - lastDataWriteAccel > 1000)
            return false;
        else
            return true;
    }

    //Just a shortcut for thread sleep with try catch
    public static void sleepShortcut(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //starts gps data collection
    public static void startGPS() {

        //create a locationListener
        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                //Create a new bucket element and fill ist
                GPSData gpsData = new GPSData();
                gpsData.latitude = location.getLatitude();
                gpsData.longitude = location.getLongitude();
                gpsData.altitude = location.getAltitude();
                gpsData.speed = location.getSpeed();
                gpsData.accuracy = location.getAccuracy();

                //write the bucket to the file
                writeData(gpsData, null);
            }

            //this just exists because it has to
            @Override
            public void onStatusChanged(String provider, int Status, Bundle extra) {}
        };

        //activates the data collection
        //the acces was given at the beginning of the app so I don't do it here
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    //starts the accel. data collection
    public static void startAccel() {

        //create a listener for the accel. data
        SensorEventListener sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                //wirte accel. data to bucket
                AccelData accelData = new AccelData();
                accelData.x = event.values[0];
                accelData.y = event.values[1];
                accelData.z = event.values[2];
                //write bucket to file
                writeData(null, accelData);
            }

            //this just exists because it has to
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        //activates the data collection
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    //init the files and say the path
    public static void initNewFile() {
        File path = activity.getExternalFilesDir(null);
        gpsFile   = new File(path, "GPS_"   + String.valueOf(System.currentTimeMillis()/1000) + ".csv");
        accelFile = new File(path, "ACCEL_" + String.valueOf(System.currentTimeMillis()/1000) + ".csv");
        log("INFO: File dir " + path.getAbsolutePath());
    }

    //writes data to file system
    //there can just be gps or accel data and the other value can be null
    public static void writeData(GPSData gps, AccelData accel) {

        //Creates a stream for writing
        FileOutputStream stream = null;

        //if there is gps data to write
        if(gps != null) {
            try {
                //write gps data in csv style
                stream = new FileOutputStream(gpsFile, true);
                stream.write(
                        (String.valueOf(System.currentTimeMillis()) + ';' +
                                String.valueOf(gps.longitude) + ';' +
                                String.valueOf(gps.latitude) + ';' +
                                String.valueOf(gps.altitude) + ';' +
                                String.valueOf(gps.speed) + ';' +
                                String.valueOf(gps.accuracy) + ';' +
                                tag + "\n").getBytes()
                );
                stream.flush();
            } catch (Exception e) {
                //say if writing has an error
                log("ERROR: Kann nicht in die gps Datei schreiben.");
            } finally {
                //close stream
                if(stream != null)
                    try {
                        stream.close();
                    } catch(Exception e) {}
                //store the time of the writ to later see how long was the last writing ago
                lastDataWriteGPS = System.currentTimeMillis();
            }
        }


        //if there is accel. data to write
        //works vary much like the block before
        if(accel != null) {
            stream = null;
            try {
                stream = new FileOutputStream(accelFile, true);
                stream.write(
                        (String.valueOf(System.currentTimeMillis()) + ';' +
                                String.valueOf(accel.x) + ';' +
                                String.valueOf(accel.y) + ';' +
                                String.valueOf(accel.z) + ';' +
                                tag + "\n").getBytes()
                );
                stream.flush();
            } catch (Exception e) {
                log("ERROR: Kann nicht in die accel Datei schreiben.");
            } finally {
                if(stream != null)
                    try {
                        stream.close();
                    } catch(Exception e) {}
                lastDataWriteAccel = System.currentTimeMillis();
            }
        }
    }

    //write something to the log for the user to see
    public static void log(String s) {
        log += s + "\n";
        flush();
    }

    //flush vars to textboxes
    public static void flush() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) activity.findViewById(R.id.debugTextView)).setText(log);
                ((TextView) activity.findViewById(R.id.textViewMode)).setText(tag);
            }
        });
    }
}

//just a bucket for gps data
class GPSData {
    public double longitude;
    public double latitude;
    public double altitude;
    public float speed;
    public float accuracy;
}

//just a bucket for accel. data
class AccelData {
    public double x, y, z;
}