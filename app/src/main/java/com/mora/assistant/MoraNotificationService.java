package com.mora.assistant;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;

public class MoraNotificationService extends NotificationListenerService {
    private static final List<String> recentNotifications = new ArrayList<>();

    public static List<String> getRecentNotifications() {
        return new ArrayList<>(recentNotifications);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        String pkg = sbn.getPackageName();
        CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");
        String entry = "[" + pkg + "] " + (title != null ? title : "") + ": " + (text != null ? text : "");
        if (recentNotifications.size() >= 30) {
            recentNotifications.remove(0);
        }
        recentNotifications.add(entry);
    }
}
