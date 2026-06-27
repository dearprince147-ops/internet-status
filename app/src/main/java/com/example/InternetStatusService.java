package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InternetStatusService extends Service {

    private static final String CHANNEL_ID = "internet_status_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final long PING_INTERVAL_SECONDS = 5;

    // Single background thread - reused forever, never spawns extras
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pingTask;

    private final AtomicBoolean isScreenOn = new AtomicBoolean(true);
    private final AtomicBoolean lastStatus = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private NotificationManager notificationManager;
    private ScreenStateReceiver screenStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        // Single thread that lives for the lifetime of the service
        scheduler = Executors.newSingleThreadScheduledExecutor();

        screenStateReceiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning.getAndSet(true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(false),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(false));
            }
            startPinging();
        }
        return START_STICKY;
    }

    private void startPinging() {
        // Fixed rate task on single reusable thread - no new threads ever created
        pingTask = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                // Skip ping entirely when screen is off - save battery
                if (!isScreenOn.get()) return;

                boolean hasInternet = hasActualInternetAccess();

                // Save to prefs so UI can read on reopen
                getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("last_known_status", hasInternet).apply();

                // Only update notification when status actually changes
                if (hasInternet != lastStatus.getAndSet(hasInternet)) {
                    updateNotification(hasInternet);
                }

                // Always broadcast so UI dot stays live when app is open
                Intent broadcast = new Intent("com.example.INTERNET_STATUS_UPDATE");
                broadcast.putExtra("status", hasInternet);
                sendBroadcast(broadcast);
            }
        }, 0, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private boolean hasActualInternetAccess() {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL("https://clients3.google.com/generate_204").openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(1500);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Android");
            conn.setUseCaches(false);
            conn.connect();
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 204;
        } catch (Exception e) {
            return false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Internet Status Monitor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows real-time internet connectivity status");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.enableLights(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(boolean isConnected) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent pIntent = PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class), flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);

        builder.setContentTitle("Internet Status")
            .setContentText(isConnected ? "Internet: Connected ✓" : "Internet: No Access ✗")
            .setSmallIcon(R.drawable.ic_status_dot_small)
            .setColor(isConnected ? 0xFF4CAF50 : 0xFFF44336)
            .setContentIntent(pIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setLargeIcon(android.graphics.drawable.Icon.createWithResource(
                this, isConnected ? R.drawable.ic_dot_green : R.drawable.ic_dot_red));
        }

        return builder.build();
    }

    private void updateNotification(boolean isConnected) {
        if (notificationManager != null && isRunning.get()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(isConnected));
        }
    }

    @Override
    public void onDestroy() {
        isRunning.set(false);
        if (pingTask != null) pingTask.cancel(false);
        if (scheduler != null) scheduler.shutdownNow();
        if (screenStateReceiver != null) unregisterReceiver(screenStateReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn.set(true);
                // Immediate check on screen on without waiting for next interval
                scheduler.execute(new Runnable() {
                    @Override
                    public void run() {
                        boolean hasInternet = hasActualInternetAccess();
                        getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("last_known_status", hasInternet).apply();
                        if (hasInternet != lastStatus.getAndSet(hasInternet)) {
                            updateNotification(hasInternet);
                        }
                        Intent broadcast = new Intent("com.example.INTERNET_STATUS_UPDATE");
                        broadcast.putExtra("status", hasInternet);
                        sendBroadcast(broadcast);
                    }
                });
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn.set(false);
            }
        }
    }
}
