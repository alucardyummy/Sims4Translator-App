package com.alucardyummy.sims4translator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.androidbrowserhelper.trusted.DelegationService;

/**
 * O Chrome delega a exibição das notificações do site pra esse serviço (via
 * TRUSTED_WEB_ACTIVITY_SERVICE, ver AndroidManifest.xml). A API padrão de Web
 * Notification (showNotification no sw.js) não tem campo de cor, então a
 * notificação chega aqui já pronta, mas sem cor de marca.
 *
 * Como esse serviço roda no lado nativo (não no JS), a gente intercepta a
 * notificação antes de postar e injeta a cor roxa da marca — funciona sempre,
 * inclusive com o app fechado, diferente do antigo hack via postMessage
 * (window.AndroidNotify) que só existia no app antigo em Kivy/WebView e só
 * funcionava com uma aba aberta.
 */
public class BrandedNotificationDelegationService extends DelegationService {

    private static final int BRAND_COLOR = Color.parseColor("#b89cd9");

    @Override
    public boolean onNotifyNotificationWithChannel(
            @NonNull String platformTag,
            int platformId,
            @NonNull Notification notification,
            @NonNull String channelName) {

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // recoverBuilder só existe a partir do Android 7 (API 24)
            Notification.Builder builder = Notification.Builder.recoverBuilder(this, notification);
            builder.setColor(BRAND_COLOR);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Sem isso, notify() posta num canal que não existe e o
                // Android descarta a notificação sem avisar nada.
                builder.setChannelId(channelName);
                NotificationChannel channel = new NotificationChannel(
                        channelName, channelName, NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }

            notification = builder.build();
        } else {
            // No Android 5 e 6, o campo "color" já existe e pode ser setado direto
            notification.color = BRAND_COLOR;
        }

        notificationManager.notify(platformTag, platformId, notification);
        return true;
    }
}
