package com.tapguard.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private Switch mainSwitch;
    private SeekBar durationSeekBar;
    private TextView durationValue, durationDesc, blockedCount, allowedCount, sessionTime;
    private TextView logArea;
    private Button testBtn, btnQuick, btnBalanced, btnCareful, btnMax;
    private Button btnClearStats;
    private Button btnEnableKeyboard;

    private boolean isEnabled = true;
    private float duration = 0.05f;
    private long lastTapTime = 0;
    private int blocked = 0;
    private int allowed = 0;
    private long sessionStart;
    private Handler handler = new Handler();
    private List<String> logs = new ArrayList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("TapGuardPrefs", MODE_PRIVATE);
        sessionStart = SystemClock.elapsedRealtime();

        initViews();
        loadPrefs();
        setupListeners();
        startSessionTimer();
    }

    private void initViews() {
        mainSwitch    = findViewById(R.id.mainSwitch);
        durationSeekBar = findViewById(R.id.durationSeekBar);
        durationValue = findViewById(R.id.durationValue);
        durationDesc  = findViewById(R.id.durationDesc);
        blockedCount  = findViewById(R.id.blockedCount);
        allowedCount  = findViewById(R.id.allowedCount);
        sessionTime   = findViewById(R.id.sessionTime);
        logArea       = findViewById(R.id.logArea);
        testBtn       = findViewById(R.id.testBtn);
        btnQuick      = findViewById(R.id.btnQuick);
        btnBalanced   = findViewById(R.id.btnBalanced);
        btnCareful    = findViewById(R.id.btnCareful);
        btnMax        = findViewById(R.id.btnMax);
        btnClearStats = findViewById(R.id.btnClearStats);
        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard);
    }

    private void loadPrefs() {
        isEnabled = prefs.getBoolean("enabled", true);
        int progress = prefs.getInt("progress", 3);
        mainSwitch.setChecked(isEnabled);
        durationSeekBar.setProgress(progress);
        updateDuration(progress);
    }

    private void setupListeners() {
        mainSwitch.setOnCheckedChangeListener((btn, checked) -> {
            isEnabled = checked;
            prefs.edit().putBoolean("enabled", checked).apply();
        });

        durationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean user) {
                updateDuration(progress);
                prefs.edit().putInt("progress", progress).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        testBtn.setOnClickListener(v -> handleTestTap());
        btnQuick.setOnClickListener(v -> { durationSeekBar.setProgress(1); updateDuration(1); });
        btnBalanced.setOnClickListener(v -> { durationSeekBar.setProgress(5); updateDuration(5); });
        btnCareful.setOnClickListener(v -> { durationSeekBar.setProgress(8); updateDuration(8); });
        btnMax.setOnClickListener(v -> { durationSeekBar.setProgress(10); updateDuration(10); });

        btnEnableKeyboard.setOnClickListener(v -> {
            // Step 1: send the user to Settings to turn TapGuard's keyboard ON
            // (Android requires this manual step for security -- an app cannot
            // enable itself as an input method automatically).
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));

            // Step 2: also show the keyboard picker so they can switch to it
            // right away once it's enabled.
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });

        btnClearStats.setOnClickListener(v -> {
            blocked = 0; allowed = 0;
            logs.clear();
            blockedCount.setText("0");
            allowedCount.setText("0");
            logArea.setText("কোনো activity নেই");
            sessionStart = SystemClock.elapsedRealtime();
        });
    }

    private void updateDuration(int progress) {
        if (progress < 1) progress = 1;
        // Progress now represents whole seconds directly (1-15), so long
        // debounce delays are possible, unlike the OS's built-in ~4s cap.
        duration = progress;
        durationValue.setText(String.format(Locale.getDefault(), "%.1fs", duration));
        String[] descs = {"", "খুব দ্রুত", "দ্রুত", "মধ্যম", "ব্যালেন্সড", "ব্যালেন্সড",
                "সতর্ক", "সতর্ক", "সাবধান", "সাবধান", "সাবধান",
                "সর্বোচ্চ", "সর্বোচ্চ", "সর্বোচ্চ", "সর্বোচ্চ", "সর্বোচ্চ সুরক্ষা"};
        if (progress < descs.length) durationDesc.setText(descs[progress]);
        highlightPreset(progress);

        // Persist in the exact key + unit (float seconds) that TapGuardIME reads.
        prefs.edit().putFloat("debounceSeconds", duration).apply();
    }

    private void highlightPreset(int p) {
        int normal = getResources().getColor(R.color.preset_normal, null);
        int active = getResources().getColor(R.color.preset_active, null);
        btnQuick.setBackgroundColor(p == 1 ? active : normal);
        btnBalanced.setBackgroundColor(p == 5 ? active : normal);
        btnCareful.setBackgroundColor(p == 8 ? active : normal);
        btnMax.setBackgroundColor(p == 10 ? active : normal);
    }

    private void handleTestTap() {
        long now = SystemClock.elapsedRealtime();
        float diff = (now - lastTapTime) / 1000f;
        String time = sdf.format(new Date());

        if (!isEnabled) {
            allowed++;
            addLog("✓ Tap passed (বন্ধ)", time, false);
        } else if (lastTapTime == 0 || diff >= duration) {
            allowed++;
            lastTapTime = now;
            addLog(String.format(Locale.getDefault(), "✓ Allowed (%.3fs gap)", diff), time, false);
        } else {
            blocked++;
            addLog(String.format(Locale.getDefault(), "✗ Blocked! (%.3fs < %.2fs)", diff, duration), time, true);
        }

        blockedCount.setText(String.valueOf(blocked));
        allowedCount.setText(String.valueOf(allowed));
    }

    private void addLog(String msg, String time, boolean isBlock) {
        String entry = time + "  " + msg;
        logs.add(0, entry);
        if (logs.size() > 5) logs.remove(logs.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String l : logs) { sb.append(l).append("\n"); }
        logArea.setText(sb.toString().trim());
    }

    private void startSessionTimer() {
        handler.post(new Runnable() {
            public void run() {
                long elapsed = (SystemClock.elapsedRealtime() - sessionStart) / 1000;
                if (elapsed < 60) sessionTime.setText(elapsed + "s");
                else sessionTime.setText((elapsed / 60) + "m " + (elapsed % 60) + "s");
                handler.postDelayed(this, 1000);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
