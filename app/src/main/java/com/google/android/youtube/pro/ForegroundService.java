package com.google.android.youtube.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;

public class ForegroundService extends Service {
    public static final String CHANNEL_ID = "Media";
    public static final String ACTION_UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION";
    private NotificationManager notificationManager;
    private BroadcastReceiver updateReceiver;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaSession();
        registerUpdateReceiver();
        createNotificationChannel();
    }

    private void initMediaSession() {
        mediaSession = new MediaSession(getApplicationContext(), "YTPROMediaSession");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { super.onPlay(); sendAction("PLAY_ACTION"); }
            @Override public void onPause() { super.onPause(); sendAction("PAUSE_ACTION"); }
            @Override public void onSkipToNext() { super.onSkipToNext(); sendAction("NEXT_ACTION"); }
            @Override public void onSkipToPrevious() { super.onSkipToPrevious(); sendAction("PREV_ACTION"); }
            @Override public void onSeekTo(long pos) { super.onSeekTo(pos); getApplicationContext().sendBroadcast(new Intent("TRACKS_TRACKS").putExtra("actionname", "SEEKTO").putExtra("pos", pos + "")); }
        });
        mediaSession.setActive(true);
    }
    private void sendAction(String action){ getApplicationContext().sendBroadcast(new Intent("TRACKS_TRACKS").putExtra("actionname", action)); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Background Play", NotificationManager.IMPORTANCE_MIN);
            notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }
    }

    public void updateNotification(String icon, String title, String subtitle, String action, long duration, long currentPosition) {
        Context cont = getApplicationContext();
        Bitmap largeIcon = null;
        try { byte[] decodedBytes = Base64.decode(icon, Base64.DEFAULT); largeIcon = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length); } 
        catch (Exception e) { largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.app_icon); }

        int playbackState = "play".equals(action) ? PlaybackState.STATE_PLAYING : ("pause".equals(action) ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_BUFFERING);
        updateMediaSessionMetadata(title, subtitle, largeIcon, duration);
        updatePlaybackState(currentPosition, playbackState);

        Intent openAppIntent = new Intent(cont, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(cont, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? R.drawable.notification : R.mipmap.app_icon)
                .setContentTitle(title).setContentText(subtitle).setLargeIcon(largeIcon).setContentIntent(openAppPendingIntent)
                .setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()))
                .addAction(R.drawable.ic_skip_previous_white, "Previous", getActionIntent(cont, "PREV_ACTION"))
                .addAction("play".equals(action) ? R.drawable.ic_pause_white : R.drawable.ic_play_arrow_white, "play".equals(action) ? "Pause" : "Play", getActionIntent(cont, "play".equals(action) ? "PAUSE_ACTION" : "PLAY_ACTION"))
                .addAction(R.drawable.ic_skip_next_white, "Next", getActionIntent(cont, "NEXT_ACTION"));

        notificationManager.notify(1, builder.build());
    }

    private PendingIntent getActionIntent(Context context, String action){
        Intent intent = new Intent(context, NotificationActionService.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private void registerUpdateReceiver() {
        updateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ACTION_UPDATE_NOTIFICATION.equals(intent.getAction())) {
                    updateNotification(intent.getStringExtra("icon"), intent.getStringExtra("title"), intent.getStringExtra("subtitle"), intent.getStringExtra("action"), intent.getLongExtra("duration", 0), intent.getLongExtra("currentPosition", 0));
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_NOTIFICATION), RECEIVER_EXPORTED);
        else registerReceiver(updateReceiver, new IntentFilter(ACTION_UPDATE_NOTIFICATION));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) { notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); setupNotification(intent); }
        return START_NOT_STICKY;
    }

    private void setupNotification(Intent intent) {
        // Initial setup similar to updateNotification but calls startForeground
        updateNotification(intent.getStringExtra("icon"), intent.getStringExtra("title"), intent.getStringExtra("subtitle"), intent.getStringExtra("action"), intent.getLongExtra("duration", 0), intent.getLongExtra("currentPosition", 0));
        startForeground(1, new Notification.Builder(this).build()); // Placeholder, immediate update handles real notification
    }

    private void updateMediaSessionMetadata(String title, String artist, Bitmap albumArt, long duration) {
        mediaSession.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, artist).putString(MediaMetadata.METADATA_KEY_ALBUM, "YT PRO").putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt).putLong(MediaMetadata.METADATA_KEY_DURATION, duration).build());
    }

    private void updatePlaybackState(long currentPosition, int state) {
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO).setState(state, currentPosition, 1.0f).build());
    }

    @Override public void onDestroy() { super.onDestroy(); unregisterReceiver(updateReceiver); }
    @Override public IBinder onBind(Intent intent) { return null; }
}