// android/app/src/main/java/com/detoxdial/UsageMonitorService.java
package com.detoxdial;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class UsageMonitorService extends Service {
    private static final String TAG = "UsageMonitorService";
    private static final String CHANNEL_ID = "DetoxDialChannel";
    private static final int NOTIFICATION_ID = 1;
    private Handler handler;
    private boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Detox Dial Running")
                .setContentText("Monitoring your app usage...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        // Start monitoring in a background thread using Handler instead of raw Thread
        handler.post(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        checkForInstagram();
                        // Post the next check after delay instead of sleep
                        handler.postDelayed(this, 10000); // 10 seconds delay
                        return; // Exit this iteration
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking Instagram usage", e);
                    }
                }
            }
        });

        return START_STICKY;
    }

    private void checkForInstagram() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        long beginTime = endTime - 10000; // last 10 seconds
        UsageEvents events = usm.queryEvents(beginTime, endTime);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getPackageName().equals("com.instagram.android") && event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                Intent chatIntent = new Intent(this, ChatActivity.class);
                String personality = getSharedPreferences("MBTI", MODE_PRIVATE)
                        .getString("finalPersonality", "NEUTRAL");
                chatIntent.putExtra("personality", personality);
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chatIntent);
                break;
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Detox Dial Service",
                    NotificationManager.IMPORTANCE_LOW);
            
            NotificationManager notificationManager = 
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
