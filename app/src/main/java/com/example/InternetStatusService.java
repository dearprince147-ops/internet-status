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
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class InternetStatusService extends Service {
    private static final String CHANNEL_ID = "internet_status_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final long PING_INTERVAL = 5000;

    private boolean isRunning = false;
    private boolean isScreenOn = true;
    private boolean lastStatus = false;
    private Handler handler;
    private NotificationManager notificationManager;
    private ScreenStateReceiver screenStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        
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
        if (!isRunning) {
            isRunning = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(false));
            }
            startPinging();
        }
        return START_STICKY;
    }

    private void startPinging() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isRunning && isScreenOn) {
                    checkInternetAsync();
                }
                if (isRunning) {
                    handler.postDelayed(this, PING_INTERVAL);
                }
            }
        });
    }

    private void checkInternetAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean hasInternet = hasActualInternetAccess();
                if (hasInternet != lastStatus) {
                    lastStatus = hasInternet;
                    updateNotification(hasInternet);
                    
                    // Broadcast to MainActivity if active
                    Intent intent = new Intent("com.example.INTERNET_STATUS_UPDATE");
                    intent.putExtra("status", hasInternet);
                    sendBroadcast(intent);
                }
            }
        }).start();
    }

    private boolean hasActualInternetAccess() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            try {
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
                sock.close();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
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
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(boolean isConnected) {
        Intent pendingIntent = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pIntent = PendingIntent.getActivity(
                this, 0, pendingIntent, pendingFlags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
            builder.setPriority(Notification.PRIORITY_LOW);
        }

        builder.setContentTitle("Internet Status")
                .setContentText(isConnected ? "Internet: Connected ✓" : "Internet: No Access ✗")
                .setSmallIcon(R.drawable.ic_status_dot_small)
                .setColor(isConnected ? 0xFF4CAF50 : 0xFFF44336)
                .setContentIntent(pIntent)
                .setOngoing(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.graphics.drawable.Icon largeIcon = android.graphics.drawable.Icon.createWithResource(this, 
                isConnected ? R.drawable.ic_dot_green : R.drawable.ic_dot_red);
            builder.setLargeIcon(largeIcon);
        } else {
            // For older devices, create bitmap from vector/shape drawable
            android.graphics.drawable.Drawable drawable = getResources().getDrawable(isConnected ? R.drawable.ic_dot_green : R.drawable.ic_dot_red);
            if (drawable != null) {
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 64,
                    drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 64, 
                    android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                builder.setLargeIcon(bitmap);
            }
        }
        
        return builder.build();
    }

    private void updateNotification(boolean isConnected) {
        if (notificationManager != null && isRunning) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(isConnected));
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (screenStateReceiver != null) {
            unregisterReceiver(screenStateReceiver);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class ScreenStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                // Force an immediate check
                checkInternetAsync();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
            }
        }
    }
}
