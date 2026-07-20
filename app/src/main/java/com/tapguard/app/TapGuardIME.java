package com.tapguard.app;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;

/**
 * TapGuard keyboard.
 *
 * A minimal lowercase QWERTY keyboard whose only special behaviour is a
 * configurable "debounce" delay: any key press that arrives sooner than
 * {@code debounceSeconds} after the previous ACCEPTED key press is ignored.
 * This is what lets typing feel "slower" / less jumpy, without touching or
 * interfering with any other app's touch input.
 */
public class TapGuardIME extends InputMethodService {

    private boolean capsOn = false;
    private long lastAcceptedKeyTime = 0L;

    @Override
    public View onCreateInputView() {
        View keyboard = getLayoutInflater().inflate(R.layout.keyboard_view, null);

        String[] letters = {
                "q","w","e","r","t","y","u","i","o","p",
                "a","s","d","f","g","h","j","k","l",
                "z","x","c","v","b","n","m"
        };

        for (String letter : letters) {
            int resId = getResources().getIdentifier("key_" + letter, "id", getPackageName());
            Button btn = keyboard.findViewById(resId);
            if (btn != null) {
                btn.setOnClickListener(v -> onLetterKey(letter));
            }
        }

        Button space = keyboard.findViewById(R.id.key_space);
        space.setOnClickListener(v -> onCharacterKey(" "));

        Button backspace = keyboard.findViewById(R.id.key_backspace);
        backspace.setOnClickListener(v -> onBackspace());

        Button enter = keyboard.findViewById(R.id.key_enter);
        enter.setOnClickListener(v -> onEnter());

        Button shift = keyboard.findViewById(R.id.key_shift);
        shift.setOnClickListener(v -> {
            capsOn = !capsOn;
            updateLetterCase(keyboard, letters);
        });

        return keyboard;
    }

    private void updateLetterCase(View keyboard, String[] letters) {
        for (String letter : letters) {
            int resId = getResources().getIdentifier("key_" + letter, "id", getPackageName());
            Button btn = keyboard.findViewById(resId);
            if (btn != null) {
                btn.setText(capsOn ? letter.toUpperCase() : letter);
            }
        }
    }

    private void onLetterKey(String letter) {
        onCharacterKey(capsOn ? letter.toUpperCase() : letter);
    }

    /**
     * Core debounce logic: only forward the character to the app being typed
     * into if enough time has passed since the last accepted key press.
     * Backspace and Enter deliberately bypass this so correcting a mistake
     * is never itself delayed.
     */
    private void onCharacterKey(String text) {
        long now = SystemClock.elapsedRealtime();
        double debounceSeconds = getDebounceSeconds();
        double elapsed = (now - lastAcceptedKeyTime) / 1000.0;

        if (lastAcceptedKeyTime != 0L && elapsed < debounceSeconds) {
            // Too soon after the previous key press -- ignore this one.
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
        lastAcceptedKeyTime = now;
    }

    private void onBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText("\n", 1);
        }
    }

    private double getDebounceSeconds() {
        SharedPreferences prefs = getSharedPreferences("TapGuardPrefs", MODE_PRIVATE);
        // Stored directly in seconds by MainActivity's duration control.
        return prefs.getFloat("debounceSeconds", 0.3f);
    }
}
