package com.irsakitchen.printagent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    EditText baseUrlInput, tokenInput, pollingInput;
    Button saveButton, startButton, stopButton, testPrintButton;
    SharedPreferences prefs;
    EditText printerPackageInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("print_agent_prefs", Context.MODE_PRIVATE);

        baseUrlInput = findViewById(R.id.base_url);
        tokenInput = findViewById(R.id.token);
        printerPackageInput = findViewById(R.id.printer_package);
        pollingInput = findViewById(R.id.polling_seconds);
        saveButton = findViewById(R.id.save_button);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        testPrintButton = findViewById(R.id.test_print_button);

        baseUrlInput.setText(prefs.getString("base_url","https://irsakitchen.com/print-agent-api.php"));
        tokenInput.setText(prefs.getString("token",""));
        printerPackageInput.setText(prefs.getString("printer_package","com.loopedlabs.escposprintservice"));
        pollingInput.setText(String.valueOf(prefs.getInt("poll_seconds",30)));

        saveButton.setOnClickListener(v -> savePrefs());

        startButton.setOnClickListener(v -> {
            savePrefs();
            Intent svc = new Intent(this, PrintAgentService.class);
            ComponentName cn = startForegroundService(svc);
        });

        stopButton.setOnClickListener(v -> stopService(new Intent(this, PrintAgentService.class)));

        testPrintButton.setOnClickListener(v -> sendTestPrint());
    }

    void savePrefs(){
        SharedPreferences.Editor e = prefs.edit();
        e.putString("base_url", baseUrlInput.getText().toString().trim());
        e.putString("token", tokenInput.getText().toString().trim());
        e.putString("printer_package", printerPackageInput.getText().toString().trim());
        int sec = 30;
        try{ sec = Integer.parseInt(pollingInput.getText().toString().trim()); }catch(Exception ignored){}
        e.putInt("poll_seconds", sec);
        e.apply();
    }

    void sendTestPrint(){
        String receipt = "Irsa Print Agent - Test Receipt\nOrder: 123\nThank you!";
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

        try {
            if (pi.resolveActivity(getPackageManager()) != null) {
                startActivity(pi);
                return;
            }
        } catch (Exception ignored) {}

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        if (pkg != null && pkg.length() > 0) share.setPackage(pkg);
        share.putExtra(Intent.EXTRA_TEXT, receipt);
        share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            if (share.resolveActivity(getPackageManager()) != null) {
                startActivity(share);
            }
        } catch (Exception ignored) {}
    }
}
