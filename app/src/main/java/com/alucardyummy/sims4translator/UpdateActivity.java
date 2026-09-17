package com.alucardyummy.sims4translator;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Estende a LauncherActivity padrão da TWA (biblioteca do Google) só pra
// encaixar a checagem de atualização por cima, sem mexer no comportamento
// normal da TWA (ela continua abrindo o site igualzinho).
public class UpdateActivity extends LauncherActivity {

    private static final String REPO = "alucardyummy/Sims4Translator-App";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkForUpdateInBackground();
    }

    private void checkForUpdateInBackground() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + REPO + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                JSONObject data = new JSONObject(sb.toString());
                String latestTag = data.optString("tag_name", "");

                String apkUrl = null;
                JSONArray assets = data.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url");
                            break;
                        }
                    }
                }

                String currentVersion = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;

                if (apkUrl != null && isNewer(latestTag, currentVersion)) {
                    String finalApkUrl = apkUrl;
                    new Handler(Looper.getMainLooper()).post(() -> showUpdateDialog(latestTag, finalApkUrl));
                }
            } catch (Exception e) {
                // Silencioso: sem internet ou erro na API, o app abre normal mesmo assim
            }
        }).start();
    }

    private boolean isNewer(String latestTag, String currentVersion) {
        int[] latest = parseVersion(latestTag);
        int[] current = parseVersion(currentVersion);
        for (int i = 0; i < Math.max(latest.length, current.length); i++) {
            int l = i < latest.length ? latest[i] : 0;
            int c = i < current.length ? current[i] : 0;
            if (l != c) return l > c;
        }
        return false;
    }

    private int[] parseVersion(String v) {
        if (v == null) return new int[]{0};
        v = v.trim().replaceFirst("^[vV]", "");
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private void showUpdateDialog(String version, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Atualização disponível")
                .setMessage("Nova versão " + version + " disponível. Quer atualizar agora?")
                .setPositiveButton("Atualizar", (dialog, which) -> downloadAndInstall(apkUrl))
                .setNegativeButton("Agora não", null)
                .setCancelable(true)
                .show();
    }

    private void downloadAndInstall(String apkUrl) {
        new Thread(() -> {
            try {
                File apkFile = new File(getFilesDir(), "update.apk");
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }

                installApk(apkFile);
            } catch (Exception e) {
                // Silencioso: se falhar, a pessoa sempre pode baixar manual pelo GitHub
            }
        }).start();
    }

    private void installApk(File apkFile) throws Exception {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        try (OutputStream out = session.openWrite("update_session", 0, apkFile.length());
             FileInputStream in = new FileInputStream(apkFile)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            session.fsync(out);
        }

        // Receiver registrado em tempo de execução (não precisa constar no
        // AndroidManifest) só pra saber se deu certo — o Android mostra a
        // tela de confirmação de instalação sozinho, independente disso.
        String action = getPackageName() + ".UPDATE_RESULT";
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmIntent != null) {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(confirmIntent);
                    }
                }
                unregisterReceiver(this);
            }
        };
        registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        Intent broadcastIntent = new Intent(action).setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, sessionId, broadcastIntent, flags);

        session.commit(pendingIntent.getIntentSender());
        session.close();
    }
}
