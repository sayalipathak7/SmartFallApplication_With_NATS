package com.example.wear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.TimeUtils;
import android.widget.TimePicker;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.wear.Prediction.Prediction;
import com.example.wear.util.Event;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SensorService extends Service implements SensorEventListener {

    private final String TAG = "Sensor Service";
    private SensorManager mmSensorManager;
    private Sensor mmSensor;
    private float[] mmSensorValues;
    private Timer timer;

    private float start_time=0;

    private long startTime=0L;
    private long startTime1=0L;
    private long latency=0L;
    private long total=0L;
    private long endTime=0L;
    private long endTime1=0L;
    private int predictions = 0;
    String status=MainActivity.watchStatus;

    private static int noOfEvents;

    @Override
    public void onCreate() {
        super.onCreate();


            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (mmSensorValues != null) {
                        Intent messageIntent = new Intent("data-receiver");
                        messageIntent.putExtra("message", mmSensorValues[1] + "");
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(messageIntent);

                        ByteBuffer buffer = ByteBuffer.allocate(4 * mmSensorValues.length);

                        for (float value : mmSensorValues) {
                            buffer.putFloat(value);
                        }

                        byte[] bytes = buffer.array();
                        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                        Event event = new Event(bytes, timestamp, UUID.fromString(getApplicationContext().getSharedPreferences("Fall_Detection", 0).getString("uuid", null)));
                        try {
                            startTime = System.nanoTime();
                            //System.out.println("startTime"+startTime);
                            //if(status.equals("Activated"))
                                Prediction.makePrediction(event);
                            noOfEvents++;
                            endTime = System.nanoTime();
                            //System.out.println("endTime"+endTime);
                            latency = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                            //System.out.println("Latency: " + latency + " ms");
                            total = total + latency;
                            //System.out.println("TOTAL VAL"+total);

                            // System.out.println(end_time-start_time + " TIMEEEE" + predictions + " " + (end_time-start_time)/predictions);
                            //System.out.println(endTime-startTime + " TIME NANO" + predictions + " " + (endTime-startTime)/predictions);
                            predictions++;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, 0, 32);

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        start_time = System.currentTimeMillis();
        startTime1 = System.nanoTime();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "ForegroundServiceChannel",
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, "ForegroundServiceChannel")
                .setContentTitle("Foreground Service")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);


        mmSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mmSensor = mmSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mmSensorManager.registerListener(this,mmSensor,10000);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        if (mmSensor != null) {
            mmSensorManager.unregisterListener(this);
        }
        float end_time = System.currentTimeMillis();
        endTime1 = System.nanoTime();

        System.out.println(end_time-start_time + " A TIMEEEE " + predictions + " AVG " + (end_time-start_time)/predictions);
        System.out.println("NANO " + TimeUnit.NANOSECONDS.toMillis(endTime1 - startTime1)/predictions);
        System.out.println( "MY TOTAL :" + total+ "  ::"+total/predictions);
        System.out.println( "Total number of events: " + noOfEvents);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mmSensorValues = event.values;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}