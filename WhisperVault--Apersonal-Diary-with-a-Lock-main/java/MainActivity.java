package com.example.madproject;
// MainActivity.java
// Adjust your package name accordingly

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // UI elements declaration
    private EditText pinInput;
    private Button confirmButton;
    private TextView authTitle;
    private TextView messageText;

    // SharedPreferences to store the PIN securely (for this example)
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "DiaryAppPrefs";
    private static final String KEY_PIN = "diary_pin";
    private String storedPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Set the layout for this activity

        // Initialize UI elements
        pinInput = findViewById(R.id.pinInput);
        confirmButton = findViewById(R.id.confirmButton);
        authTitle = findViewById(R.id.authTitle);
        messageText = findViewById(R.id.messageText);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Retrieve stored PIN (if any)
        storedPin = sharedPreferences.getString(KEY_PIN, null);

        // Check if a PIN has been set previously
        if (storedPin == null) {
            // No PIN set, prompt user to set a new one
            authTitle.setText("Set Your New PIN");
            confirmButton.setText("Set PIN");
            messageText.setText("Please set a 4-digit PIN for your diary.");
        } else {
            // PIN already set, prompt user to enter it for authentication
            authTitle.setText("Enter Your PIN");
            confirmButton.setText("Unlock Diary");
            messageText.setText("Enter your 4-digit PIN to access your diary.");
        }

        // Set up the button click listener
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handlePinInput(); // Call method to handle PIN logic
            }
        });
    }

    /**
     * Handles the logic for setting or verifying the PIN.
     */
    private void handlePinInput() {
        String enteredPin = pinInput.getText().toString();

        // Basic validation for PIN length
        if (TextUtils.isEmpty(enteredPin) || enteredPin.length() != 4) {
            messageText.setText("PIN must be 4 digits long.");
            return;
        }

        if (storedPin == null) {
            // Case 1: No PIN is set, user wants to set a new PIN
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_PIN, enteredPin); // Save the new PIN
            editor.apply(); // Apply changes asynchronously
            storedPin = enteredPin; // Update local variable
            messageText.setText("PIN set successfully!");
            Toast.makeText(this, "PIN set! Unlocking diary...", Toast.LENGTH_SHORT).show();
            navigateToDiaryActivity(); // Navigate to the diary
        } else {
            // Case 2: PIN is already set, user is trying to authenticate
            if (enteredPin.equals(storedPin)) {
                messageText.setText("PIN correct! Unlocking diary...");
                Toast.makeText(this, "PIN correct! Unlocking diary...", Toast.LENGTH_SHORT).show();
                navigateToDiaryActivity(); // Navigate to the diary
            } else {
                messageText.setText("Incorrect PIN. Please try again.");
                Toast.makeText(this, "Incorrect PIN.", Toast.LENGTH_SHORT).show();
                pinInput.setText(""); // Clear the input field
            }
        }
    }

    /**
     * Navigates to the DiaryActivity.
     */
    private void navigateToDiaryActivity() {
        Intent intent = new Intent(MainActivity.this, DiaryActivity.class);
        startActivity(intent);
        finish(); // Finish MainActivity so user can't go back to it with back button
    }
}
