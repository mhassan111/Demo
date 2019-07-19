package com.player.demo.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {

    public static Intent screenRecordIntent = null;
    public static final int SCREEN_SHOT_INTERVAL = 10 * 1000;

    @SuppressLint("SimpleDateFormat")
    public static String formatDate(String date) {
        if (date == null) {
            return null;
        }
        long timestamp = Long.valueOf(date);
        if (date.length() < 13) {
            timestamp = timestamp * 1000;
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    public static boolean isScreenInteractive(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                if (powerManager.isInteractive())
                    return true;
            } else {
                if (powerManager.isScreenOn())
                    return true;
            }
        } catch (Exception e) {
            e.getMessage();
        }
        return false;
    }
}
