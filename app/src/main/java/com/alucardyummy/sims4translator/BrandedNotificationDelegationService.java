package com.alucardyummy.sims4translator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;

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

    // Grupo próprio das notificações do app. Sem isso, quando chegam 2 ou mais
    // o Android cria um "resumo" automático com um ícone genérico dele
    // (os quadradinhos empilhados). Criando o resumo nós mesmos, a gente
    // escolhe o ícone (o fantasminha) e a cor roxa.
    private static final String GROUP_KEY = "sims4translator_updates";
    private static final int SUMMARY_ID = 1;

    private static final String HIGH_SUFFIX = "_alta";

    private static final String SHORTCUT_ID = "sims4translator_novidades";

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
            builder.setSmallIcon(Icon.createWithResource(this, R.drawable.ic_stat_notify));
            builder.setGroup(GROUP_KEY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                applyConversationStyle(builder, notification);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Sem isso, notify() posta num canal que não existe e o
                // Android descarta a notificação sem avisar nada.
                String channelId = highChannelId(channelName);
                builder.setChannelId(channelId);
                NotificationChannel channel = new NotificationChannel(
                        channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
                channel.enableVibration(true);
                notificationManager.createNotificationChannel(channel);
                notificationManager.deleteNotificationChannel(channelName);
            } else {
                builder.setPriority(Notification.PRIORITY_HIGH);
                builder.setDefaults(Notification.DEFAULT_ALL);
            }

            notification = builder.build();
        } else {
            // No Android 5 e 6, o campo "color" já existe e pode ser setado direto
            notification.color = BRAND_COLOR;
            notification.priority = Notification.PRIORITY_HIGH;
            notification.defaults |= Notification.DEFAULT_ALL;
        }

        notificationManager.notify(platformTag, platformId, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            postGroupSummary(notificationManager, channelName);
        }
        return true;
    }

    private void applyConversationStyle(Notification.Builder builder, Notification original) {
        Bundle extras = original.extras;
        CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
        String appName = getString(R.string.app_name);

        Person me = new Person.Builder().setName(appName).build();
        Person sender = new Person.Builder()
                .setName(title != null && title.length() > 0 ? title : appName)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_stat_notify))
                .setImportant(true)
                .build();

        Notification.MessagingStyle style = new Notification.MessagingStyle(me);
        style.addMessage(text != null ? text : "", System.currentTimeMillis(), sender);

        builder.setStyle(style);
        builder.setCategory(Notification.CATEGORY_MESSAGE);
        builder.addPerson(sender);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            publishConversationShortcut(sender, appName);
            builder.setShortcutId(SHORTCUT_ID);
            builder.setLocusId(new LocusId(SHORTCUT_ID));
        }
    }

    private void publishConversationShortcut(Person person, String label) {
        try {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            if (shortcutManager == null) return;
            Intent intent = new Intent(this, UpdateActivity.class).setAction(Intent.ACTION_VIEW);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(this, SHORTCUT_ID)
                    .setLongLived(true)
                    .setShortLabel(label)
                    .setLocusId(new LocusId(SHORTCUT_ID))
                    .setPerson(person)
                    .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build();
            shortcutManager.pushDynamicShortcut(shortcut);
        } catch (Exception ignored) {
            // se falhar, a notificação continua aparecendo normal (só sem a seção de Conversas)
        }
    }

    private static String highChannelId(String channelName) {
        return channelName + HIGH_SUFFIX;
    }

    /**
     * Posta (ou atualiza) o resumo do grupo com o mesmo ícone e cor das
     * notificações. Com só 1 notificação o Android nem mostra o resumo; com 2
     * ou mais ele junta tudo embaixo dele. Quando a última notificação filha
     * é dispensada, o Android remove o resumo sozinho.
     */
    private void postGroupSummary(NotificationManager notificationManager, String channelName) {
        Notification.Builder summary;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            summary = new Notification.Builder(this, highChannelId(channelName));
            // o resumo não toca/vibra de novo — quem avisa é a notificação filha
            summary.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
        } else {
            summary = new Notification.Builder(this);
        }

        summary.setSmallIcon(Icon.createWithResource(this, R.drawable.ic_stat_notify))
                .setColor(BRAND_COLOR)
                .setContentTitle(getString(R.string.app_name))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true);

        notificationManager.notify(GROUP_KEY, SUMMARY_ID, summary.build());
    }
}
