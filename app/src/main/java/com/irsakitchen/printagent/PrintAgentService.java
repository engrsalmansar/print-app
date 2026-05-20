package com.irsakitchen.printagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PrintAgentService extends Service {

    Handler handler = new Handler();
    Runnable pollRunnable;
    SharedPreferences prefs;
    int pollSeconds = 30;
    static final String CHANNEL_ID = "print_agent_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("print_agent_prefs", Context.MODE_PRIVATE);
        pollSeconds = prefs.getInt("poll_seconds",30);
        createNotificationChannel();
        startForeground(1, buildNotification("Print Agent running"));

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkForJobs();
                } catch (Exception e){ e.printStackTrace(); }
                handler.postDelayed(this, pollSeconds * 1000L);
            }
        };
        handler.post(pollRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    void createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Print Agent", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    Notification buildNotification(String text){
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,i, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Irsa Print Agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .build();
    }

    void checkForJobs(){
        String base = prefs.getString("base_url","https://irsakitchen.com/print-agent-api.php");
        String token = prefs.getString("token","");
        try{
            URL url = new URL(base);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            String payload = "action=get_job&token=" + token;
            byte[] out = payload.getBytes();
            conn.setFixedLengthStreamingMode(out.length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.connect();
            try(OutputStream os = conn.getOutputStream()){ os.write(out); }
            int code = conn.getResponseCode();
            if(code==200){
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line=br.readLine())!=null){ sb.append(line).append('\n'); }
                String resp = sb.toString().trim();
                // Expecting a simple format: job_id|||receipt_text
                if(resp.length()>0 && resp.contains("|||")){
                    String[] parts = resp.split("\\|\\|\\|");
                    String jobId = parts[0];
                    String receipt = parts[1];
                    sendToEscPos(receipt);
                    markPrinted(jobId, token, base);
                }
            }
            conn.disconnect();
        }catch(Exception e){ e.printStackTrace(); }
    }

    void sendToEscPos(String receipt){
        String pkg = prefs.getString("printer_package","com.loopedlabs.escposprintservice");

        Intent pi = new Intent();
        pi.setAction("org.escpos.intent.action.PRINT");
        if (pkg != null && pkg.length() > 0) pi.setPackage(pkg);
        pi.putExtra("DATA_TYPE", "TEXT");
        pi.putExtra(Intent.EXTRA_TEXT, receipt);
        pi.putExtra("CONTENT_TYPE", "TEXT");
        pi.putExtra("ENCODING", "UTF-8");
        pi.putExtra("COPIES", 1);
        pi.putExtra("TITLE", "Irsa Kitchen Receipt");
        pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try{
            if (pi.resolveActivity(getPackageManager()) != null){
                startActivity(pi);
                return;
            }
        }catch(Exception ignored){}

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        if (pkg != null && pkg.length() > 0) share.setPackage(pkg);
        share.putExtra(Intent.EXTRA_TEXT, receipt);
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try{
            if (share.resolveActivity(getPackageManager()) != null){
                startActivity(share);
                return;
            }
        }catch(Exception ignored){}

        // final generic fallback
        Intent generic = new Intent("org.escpos.intent.action.PRINT");
        generic.putExtra("content", receipt);
        generic.putExtra("type", "text/plain");
        generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{
            if (generic.resolveActivity(getPackageManager()) != null){
                startActivity(generic);
            }
        }catch(Exception ignored){}
    }

    void markPrinted(String jobId, String token, String baseUrl){
        try{
            URL url = new URL(baseUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            String payload = "action=mark_printed&token="+token+"&job_id="+jobId;
            byte[] out = payload.getBytes();
            conn.setFixedLengthStreamingMode(out.length);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.connect();
            try(OutputStream os = conn.getOutputStream()){ os.write(out); }
            conn.getResponseCode();
            conn.disconnect();
        }catch(Exception e){ e.printStackTrace(); }
    }
}
