package com.player.demo.services;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;
import com.player.demo.R;
import com.player.demo.activities.MainActivity;
import com.player.demo.activities.ScreenRecordRequestActivity;
import com.player.demo.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScreenCaptureService extends Service {

    public static final String CHANNEL_ID = "DemoForegroundServiceChannel";
    private static final String SCREEN_CAPTURE_NAME = "PRIVATE_SCREEN_CAPTURE";
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

    private static String mStoragePath;
    private MediaProjection mMediaProjection = null;
    private MediaProjectionManager mProjectionManager;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private static int mWidth;
    private static int mHeight;
    private static int mRotation;
    private OrientationChangeCallback mOrientationChangeCallback;
    private Timer mTimer;

    @Override
    public void onCreate() {
        super.onCreate();
        createStorageDirectory();
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (mWindowManager != null) {
            mDisplay = mWindowManager.getDefaultDisplay();
            mDisplay.getMetrics(metrics);
            mDensity = metrics.densityDpi;
        }
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
        launchScreenRecordIntent();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText("Running Service...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
        startTimerTask();
        return START_NOT_STICKY;
    }

    private void startTimerTask() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new PeriodicTimerTask(), 0, Util.SCREEN_SHOT_INTERVAL);
    }

    private void stopTimerTask() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
        }
    }

    private class PeriodicTimerTask extends TimerTask {
        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (Util.isScreenInteractive(getApplicationContext()))
                                captureScreenShot();
                        }
                    }).start();
                }
            });
        }
    }

    private void launchScreenRecordIntent() {
        if (Util.screenRecordIntent == null)
            startActivity(new Intent(getApplicationContext(), ScreenRecordRequestActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
    }

    private void captureScreenShot() {
        mMediaProjection = mProjectionManager.getMediaProjection(Activity.RESULT_OK, Objects.requireNonNull(Util.screenRecordIntent));
        mMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
        mOrientationChangeCallback = new OrientationChangeCallback(this);
        if (mOrientationChangeCallback.canDetectOrientation()) {
            mOrientationChangeCallback.enable();
        }
        createVirtualDisplay();
    }

    private void createVirtualDisplay() {
        Point size = new Point();
        mDisplay.getSize(size);
        mWidth = size.x;
        mHeight = size.y;
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(SCREEN_CAPTURE_NAME, mWidth, mHeight, mDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(final ImageReader reader) {
            new WriteImageTask(reader, new OnImageTaskCompleted()).execute();
        }
    }

    private class OnImageTaskCompleted implements onTaskComplete {
        @Override
        public void onTaskCompleted() {
            stopProjection();
        }
    }

    static class WriteImageTask extends AsyncTask<Void, Void, Void> {

        private ImageReader imageReader;
        private onTaskComplete listener;

        private WriteImageTask(ImageReader imageReader, onTaskComplete listener) {
            this.imageReader = imageReader;
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;
                    bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                    fos = new FileOutputStream(mStoragePath + Util.formatDate(String.valueOf(System.currentTimeMillis())) + ".png");
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
                if (image != null) {
                    image.close();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            listener.onTaskCompleted();
        }
    }

    public interface onTaskComplete {
        void onTaskCompleted();
    }

    private class OrientationChangeCallback extends OrientationEventListener {

        OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                mRotation = rotation;
                try {
                    releaseVirtualDisplay();
                    resetImageReaderListener();
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    releaseVirtualDisplay();
                    resetImageReaderListener();
                    disableOrientationChangeCallback();
                    mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }

    private void releaseVirtualDisplay() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    private void resetImageReaderListener() {
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null);
            mImageReader = null;
        }
    }

    private void disableOrientationChangeCallback() {
        if (mOrientationChangeCallback != null) {
            mOrientationChangeCallback.disable();
            mOrientationChangeCallback = null;
        }
    }

    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mMediaProjection != null) {
                    mMediaProjection.stop();
                }
            }
        });
    }

    private void createStorageDirectory() {
        mStoragePath = Environment.getExternalStorageDirectory() + "/" + "DemoApp/";
        File file = new File(mStoragePath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTimerTask();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Demo Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}