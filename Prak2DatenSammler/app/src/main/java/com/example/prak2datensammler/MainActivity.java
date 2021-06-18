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

/*TODO: comments
*       beautify
*       test
*/


public class MainActivity extends AppCompatActivity {

    private static boolean initial = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DataLogger.activity = this;

        if(initial) {

            if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
                if(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    System.exit(-1);
                }
            }

            DataLogger.sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            DataLogger.locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            DataLogger.initApp();

            initial = false;
        } else {
            DataLogger.flush();
        }

    }

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

class DataLogger {

    public static MainActivity activity;

    public  static String tag = "NO_TAG";
    public  static SensorManager sensorManager;
    public  static LocationManager locationManager;
    private static File gpsFile;
    private static File accelFile;
    private static long lastDataWriteGPS = 0;
    private static long lastDataWriteAccel = 0;
    private static boolean initial = true;
    private static String log = "";

    public static void initApp() {
        initial = false;
        initNewFile();

        startGPS();
        startAccel();


        new Thread() {
            @Override
            public void run() {
                if(collectingGPSData()) {
                    log("INFO: Collecting GPS data.");
                    while(true) {
                        while(collectingGPSData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: NOT Collecting GPS data.");
                        while(!collectingGPSData()) {
                            sleepShortcut(100);
                        }
                        log("INFO: Collecting GPS data.");
                    }
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

    public static boolean collectingGPSData() {
        if(System.currentTimeMillis() - lastDataWriteGPS > 2000)
            return false;
        else
            return true;
    }

    public static boolean collectingAccelData() {
        if(System.currentTimeMillis() - lastDataWriteAccel > 1000)
            return false;
        else
            return true;
    }

    public static void sleepShortcut(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void startGPS() {

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                GPSData gpsData = new GPSData();
                gpsData.latitude = location.getLatitude();
                gpsData.longitude = location.getLongitude();
                gpsData.altitude = location.getAltitude();
                gpsData.speed = location.getSpeed();
                gpsData.accuracy = location.getAccuracy();

                writeData(gpsData, null);
            }

            @Override
            public void onStatusChanged(String provider, int Status, Bundle extra) {}
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    public static void startAccel() {

        SensorEventListener sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                AccelData accelData = new AccelData();
                accelData.x = event.values[0];
                accelData.y = event.values[1];
                accelData.z = event.values[2];
                writeData(null, accelData);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
    }

    public static void initNewFile() {
        File path = activity.getExternalFilesDir(null);
        gpsFile   = new File(path, "GPS_"   + String.valueOf(System.currentTimeMillis()/1000) + ".csv");
        accelFile = new File(path, "ACCEL_" + String.valueOf(System.currentTimeMillis()/1000) + ".csv");
        log("INFO: File dir " + path.getAbsolutePath());
    }

    public static void writeData(GPSData gps, AccelData accel) {

        FileOutputStream stream = null;

        if(gps != null) {
            try {
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
                log("ERROR: Kann nicht in die gps Datei schreiben.");
            } finally {
                if(stream != null)
                    try {
                        stream.close();
                    } catch(Exception e) {}
                lastDataWriteGPS = System.currentTimeMillis();
            }
        }



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

    public static void log(String s) {
        log += s + "\n";
        flush();
    }

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

class GPSData {
    public double longitude;
    public double latitude;
    public double altitude;
    public float speed;
    public float accuracy;
}

class AccelData {
    public double x, y, z;
}