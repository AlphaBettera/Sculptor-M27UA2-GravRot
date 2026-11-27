package com.dsh.gravrot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

/**
 * Corrects the inverted gravity-sensor mapping of the SCULPTOR M27UA2 panel.
 *
 * The accelerometer is mounted 180 degrees about the vertical axis: X and Z are
 * inverted while Y is intact. Landscape poses are therefore reported correctly,
 * but both portrait poses come out exactly 180 degrees wrong.
 *
 * Empirical mapping (verified on the unit):
 *     |y| dominant, y > 0  -> rotation 0  (landscape,  correct)
 *     |y| dominant, y < 0  -> rotation 2  (seascape,   correct)
 *     |x| dominant, x > 0  -> rotation 3  (portrait,   X is inverted)
 *     |x| dominant, x < 0  -> rotation 1  (portrait,   X is inverted)
 */
public class GravRotService extends Service implements SensorEventListener {

    public static volatile boolean RUNNING = false;

    private static final String CHANNEL = "gravrot";
    private static final long DEBOUNCE_MS = 700;
    private static final long ASSERT_INTERVAL_MS = 15000;

    private SensorManager sensorManager;
    private int candidate = -1;
    private long candidateSince = 0;
    private int applied = -1;
    private long lastAssert = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        createChannel();
        startForeground(1, buildNotification());

        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }

        applied = Settings.System.getInt(getContentResolver(), Settings.System.USER_ROTATION, 0);
        enforceAutoRotateOff(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];

        int rotation;
        if (Math.abs(y) >= Math.abs(x)) {
            rotation = (y > 0) ? 0 : 2;
        } else {
            rotation = (x > 0) ? 3 : 1;
        }

        long now = System.currentTimeMillis();
        if (rotation != candidate) {
            candidate = rotation;
            candidateSince = now;
            return;
        }
        if (now - candidateSince < DEBOUNCE_MS) {
            return;
        }
        if (rotation != applied && Settings.System.canWrite(this)) {
            if (Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, rotation)) {
                applied = rotation;
            }
        }
        enforceAutoRotateOff(false);
    }

    private void enforceAutoRotateOff(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastAssert < ASSERT_INTERVAL_MS) {
            return;
        }
        lastAssert = now;
        if (!Settings.System.canWrite(this)) {
            return;
        }
        int auto = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        if (auto != 0) {
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "GravRot", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("GravRot")
                .setContentText("正在修正重力旋转")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
