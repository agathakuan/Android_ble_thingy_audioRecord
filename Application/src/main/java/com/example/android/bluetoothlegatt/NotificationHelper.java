package com.example.android.bluetoothlegatt;

import android.content.ContextWrapper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import android.graphics.Color;
//https://blog.csdn.net/jdsjlzx/article/details/84327815\
//https://github.com/XuMiaoLee/AndroidNotificationChannel

public class NotificationHelper extends ContextWrapper {

    private NotificationManager mNotificationManager;
    private NotificationChannel mNotificationChannel;

    public static final  String CHANNEL_ID          = "default";
    private static final String CHANNEL_NAME        = "Default Channel";
    private static final String CHANNEL_DESCRIPTION = "this is default channel!";

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");// HH:mm:ss
    private Date date = new Date(System.currentTimeMillis());


    public NotificationHelper(Context base)
    {
        super(base);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            mNotificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationChannel.setDescription(CHANNEL_DESCRIPTION);

            mNotificationChannel.enableLights(true);
            mNotificationChannel.setLightColor(Color.BLUE);
            mNotificationChannel.setShowBadge(true);
            mNotificationChannel.setBypassDnd(true);
            mNotificationChannel.setVibrationPattern(new long[]{100, 100, 200});
            mNotificationChannel.shouldShowLights();

            getNotificationManager().createNotificationChannel(mNotificationChannel);
        }
    }

    public NotificationCompat.Builder getNotification(String title, String content)
    {
        NotificationCompat.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else
        {
            builder = new NotificationCompat.Builder(this);
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
        }
        builder.setContentTitle(title);
        builder.setContentText(content+" "+"at"+" "+simpleDateFormat.format(date));
        builder.setSmallIcon(R.mipmap.comments);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.comments));
        builder.setVibrate(new long[]{0,1000,1000,1000});
        builder.setLights(Color.BLUE, 1000,200);
        //builder.setDefaults(NotificationCompat.DEFAULT_ALL);

        builder.setAutoCancel(true);//点击自动删除通知

        return builder;
    }

    public void notify(int id, NotificationCompat.Builder builder)
    {
        if (getNotificationManager() != null)
        {
            getNotificationManager().notify(id, builder.build());
        }
    }

    private NotificationManager getNotificationManager()
    {
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) this.getSystemService(this.NOTIFICATION_SERVICE);
        return mNotificationManager;
    }
}
