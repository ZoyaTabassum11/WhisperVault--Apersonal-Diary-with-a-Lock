// DiaryActivity.java
package com.example.madproject; // Corrected package name based on user's path

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray; // Import for TypedArray
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue; // Import for TypedValue
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DiaryActivity extends AppCompatActivity {

    private static final String TAG = "DiaryActivity"; // Tag for Logcat filtering

    // UI elements declaration
    private EditText newEntryInput;
    private Button saveEntryButton;
    private Button addImageButton;
    private ImageView selectedImageView;
    private LinearLayout pastEntriesContainer; // Container for dynamically added entries

    // URI of the currently selected image
    private Uri selectedImageUri;

    // File name for storing diary entries (now in JSON format)
    private static final String DIARY_FILE_NAME = "diary_entries.json";

    // ActivityResultLauncher for picking images from gallery
    private ActivityResultLauncher<Intent> pickImageLauncher;

    // ActivityResultLauncher for requesting permissions
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // Launcher for starting EntryDetailActivity and getting a result back
    private ActivityResultLauncher<Intent> editEntryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diary); // Set the layout for this activity

        // Initialize UI elements
        newEntryInput = findViewById(R.id.newEntryInput);
        saveEntryButton = findViewById(R.id.saveEntryButton);
        addImageButton = findViewById(R.id.addImageButton);
        selectedImageView = findViewById(R.id.selectedImageView);
        pastEntriesContainer = findViewById(R.id.pastEntriesContainer);

        // Initialize pickImageLauncher for selecting new images
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            selectedImageView.setImageURI(selectedImageUri); // Display selected image
                            selectedImageView.setVisibility(View.VISIBLE); // Make ImageView visible
                            try {
                                // Persist URI permissions to allow loading image later.
                                // This grants long-term read access to the URI for content URIs.
                                getContentResolver().takePersistableUriPermission(selectedImageUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                Log.d(TAG, "URI permission persisted for: " + selectedImageUri.toString());
                            } catch (SecurityException e) {
                                Log.e(TAG, "Failed to persist URI permission: " + e.getMessage());
                                Toast.makeText(this, "Could not get persistent URI permission for image.", Toast.LENGTH_LONG).show();
                            }
                            Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        selectedImageUri = null; // Clear URI if selection was cancelled
                        selectedImageView.setVisibility(View.GONE); // Hide ImageView
                        Toast.makeText(this, "Image selection cancelled.", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Image selection cancelled.");
                    }
                }
        );

        // Initialize requestPermissionLauncher for requesting storage permissions
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Permission granted: Proceeding to pick image.");
                        pickImage(); // If permission granted, proceed to pick image
                    } else {
                        Log.w(TAG, "Permission denied.");
                        // Check if the user has permanently denied the permission (clicked "Don't ask again")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                !shouldShowRequestPermissionRationale(getPermissionToRequest()) &&
                                // For Android 13+, also check READ_MEDIA_IMAGES
                                !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !shouldShowRequestPermissionRationale(getPermissionToRequest()))) {
                            // Permission permanently denied, guide user to settings
                            showPermissionDeniedDialog();
                        } else {
                            // Permission denied (but not permanently), just inform the user
                            Toast.makeText(this, "Permission to access storage denied. Cannot add image.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );

        // Initialize editEntryLauncher to receive results from EntryDetailActivity
        editEntryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // If EntryDetailActivity sent RESULT_OK, an update or delete occurred.
                        // Reload all entries to reflect changes.
                        Log.d(TAG, "EntryDetailActivity returned RESULT_OK. Reloading entries.");
                        loadDiaryEntries();
                        Toast.makeText(this, "Diary entries updated.", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "EntryDetailActivity returned RESULT_CANCELED.");
                    }
                }
        );

        loadDiaryEntries(); // Initial load when activity is created

        // Set up button click listeners
        saveEntryButton.setOnClickListener(v -> saveDiaryEntry());
        addImageButton.setOnClickListener(v -> checkAndRequestPermission());
    }

    /**
     * Determines the correct storage permission to request based on Android version.
     * Uses READ_MEDIA_IMAGES for Android 13+ and READ_EXTERNAL_STORAGE for older versions.
     */
    private String getPermissionToRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    /**
     * Checks for necessary storage permissions and requests them if not granted.
     */
    private void checkAndRequestPermission() {
        String permission = getPermissionToRequest();
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission already granted: " + permission);
            pickImage(); // Permission already granted, proceed to pick image
        } else {
            Log.d(TAG, "Requesting permission: " + permission);
            requestPermissionLauncher.launch(permission);
        }
    }

    /**
     * Shows an AlertDialog informing the user that permissions are permanently denied
     * and guiding them to app settings.
     */
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Storage access is needed to add images. Please enable it in App Settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent); // Open app settings
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Image functionality unavailable without permission.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Launches the image picker intent.
     */
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*"); // Specify that we want image files
        pickImageLauncher.launch(intent);
    }

    /**
     * Saves the current diary entry to a JSON file.
     */
    private void saveDiaryEntry() {
        String entryText = newEntryInput.getText().toString().trim();

        // Check if both text and image are empty
        if (TextUtils.isEmpty(entryText) && selectedImageUri == null) {
            Toast.makeText(this, "Please write something or add an image before saving.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get current date and time
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        long uniqueId = System.currentTimeMillis(); // Generate a unique ID based on timestamp for each entry

        try {
            JSONArray existingEntries = new JSONArray();
            // Attempt to read existing entries from the file
            try {
                FileInputStream fis = openFileInput(DIARY_FILE_NAME);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder jsonString = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    jsonString.append(line);
                }
                br.close();
                if (jsonString.length() > 0) {
                    existingEntries = new JSONArray(jsonString.toString());
                    Log.d(TAG, "Successfully read existing entries. Count: " + existingEntries.length());
                } else {
                    Log.d(TAG, "Existing diary file is empty. Starting with new JSONArray.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading existing diary file (might be first save): " + e.getMessage());
                // File might not exist or be empty, which is fine for the first entry.
                // existingEntries remains an empty JSONArray.
            }

            // Create a JSONObject for the new entry
            JSONObject newEntry = new JSONObject();
            newEntry.put("uniqueId", uniqueId); // Store the unique ID
            newEntry.put("timestamp", currentDate);
            newEntry.put("text", entryText);
            if (selectedImageUri != null) {
                newEntry.put("imageUri", selectedImageUri.toString());
            }
            Log.d(TAG, "New entry created: " + newEntry.toString(2)); // Log with indentation for readability

            existingEntries.put(newEntry); // Add the new entry to the existing array

            // Write the updated JSON array back to the file
            FileOutputStream fos = openFileOutput(DIARY_FILE_NAME, Context.MODE_PRIVATE);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            osw.write(existingEntries.toString(4)); // Use 4 for indentation for readability
            osw.flush();
            osw.close();
            Log.d(TAG, "Diary file written successfully. Total entries: " + existingEntries.length());

            Toast.makeText(this, "Entry saved successfully!", Toast.LENGTH_SHORT).show();
            newEntryInput.setText(""); // Clear text input
            selectedImageView.setVisibility(View.GONE); // Hide image preview
            selectedImageView.setImageURI(null); // Clear image display
            selectedImageUri = null; // Clear selected URI

            loadDiaryEntries(); // Reload entries to show the newly saved one in the list
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Error saving diary entry: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving entry: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Loads and displays all existing diary entries from the JSON file.
     * Dynamically creates and adds LinearLayouts for each entry to pastEntriesContainer.
     */
    private void loadDiaryEntries() {
        Log.d(TAG, "Attempting to load diary entries.");
        pastEntriesContainer.removeAllViews(); // Clear existing views before loading new ones

        try {
            FileInputStream fis = openFileInput(DIARY_FILE_NAME);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
            Log.d(TAG, "Raw JSON string read: " + jsonString.toString());

            if (jsonString.length() > 0) {
                JSONArray entriesArray = new JSONArray(jsonString.toString());
                Log.d(TAG, "Parsed JSON array. Number of entries: " + entriesArray.length());

                if (entriesArray.length() == 0) { // If array is empty after parsing (e.g., all deleted)
                    displayNoEntriesMessage();
                    return; // Exit if no entries to display
                }

                // Iterate through entries in reverse to show latest first
                for (int i = entriesArray.length() - 1; i >= 0; i--) {
                    JSONObject entry = entriesArray.getJSONObject(i);
                    long uniqueId = entry.getLong("uniqueId"); // Retrieve unique ID
                    String timestamp = entry.getString("timestamp");
                    String text = entry.getString("text");
                    String imageUriString = entry.optString("imageUri", null); // Get image URI if it exists
                    Log.d(TAG, "Processing entry (ID: " + uniqueId + "): Text='" + text + "', ImageURI='" + (imageUriString != null ? "present" : "absent") + "'");

                    // Create a new LinearLayout for each entry
                    LinearLayout entryLayout = new LinearLayout(this);
                    entryLayout.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    layoutParams.setMargins(0, 0, 0, 24); // Add margin between entries
                    entryLayout.setLayoutParams(layoutParams);
                    entryLayout.setBackgroundResource(R.drawable.rounded_entry_background); // Custom drawable for entry background
                    entryLayout.setPadding(16, 16, 16, 16);
                    entryLayout.setClickable(true); // Make the entry clickable
                    entryLayout.setFocusable(true); // Make the entry focusable

                    // Resolve selectableItemBackground attribute to get the actual drawable ID
                    TypedValue outValue = new TypedValue();
                    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                    entryLayout.setForeground(ContextCompat.getDrawable(this, outValue.resourceId)); // Corrected line

                    entryLayout.setTag(entry.toString()); // Store the entry's JSON string in the tag for easy retrieval when clicked

                    // Set click listener to open EntryDetailActivity
                    entryLayout.setOnClickListener(v -> {
                        String entryJsonStringClicked = (String) v.getTag();
                        Intent intent = new Intent(DiaryActivity.this, EntryDetailActivity.class);
                        intent.putExtra("entryJson", entryJsonStringClicked); // Pass the entry's JSON string
                        editEntryLauncher.launch(intent); // Use the launcher to start activity and get a result
                    });

                    // Add timestamp TextView
                    TextView timestampTextView = new TextView(this);
                    timestampTextView.setText(timestamp);
                    timestampTextView.setTextSize(14f);
                    timestampTextView.setTextColor(ContextCompat.getColor(this, R.color.dark_gray_text));
                    timestampTextView.setPadding(0, 0, 0, 8);
                    entryLayout.addView(timestampTextView);

                    // Add text content TextView if not empty
                    if (!TextUtils.isEmpty(text)) {
                        TextView entryTextView = new TextView(this);
                        entryTextView.setText(text);
                        entryTextView.setTextSize(16f);
                        entryTextView.setTextColor(ContextCompat.getColor(this, R.color.black_text));
                        entryTextView.setPadding(0, 0, 0, 8);
                        entryLayout.addView(entryTextView);
                    }

                    // Add ImageView if an image URI exists
                    if (imageUriString != null && !imageUriString.isEmpty()) {
                        ImageView entryImageView = new ImageView(this);
                        LinearLayout.LayoutParams imageLayoutParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                300 // Fixed height for displayed images in the list view
                        );
                        imageLayoutParams.setMargins(0, 8, 0, 0); // Margin above image
                        entryImageView.setLayoutParams(imageLayoutParams);
                        entryImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        try {
                            entryImageView.setImageURI(Uri.parse(imageUriString)); // Load image from URI
                            Log.d(TAG, "Image loaded for entry ID: " + uniqueId);
                        } catch (SecurityException e) {
                            // This can happen if URI permission was not persisted or revoked
                            Log.e(TAG, "SecurityException loading image for URI: " + imageUriString + ". Error: " + e.getMessage());
                            entryImageView.setImageDrawable(null); // Clear image if permission denied
                            Toast.makeText(DiaryActivity.this, "Failed to load image (permission issue).", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error loading image for URI: " + imageUriString + ", " + e.getMessage());
                            entryImageView.setImageDrawable(null); // Clear image if other error
                            Toast.makeText(DiaryActivity.this, "Failed to load image for an entry.", Toast.LENGTH_SHORT).show();
                        }
                        entryLayout.addView(entryImageView);
                    }

                    pastEntriesContainer.addView(entryLayout); // Add the created entry layout to the container
                    Log.d(TAG, "Added entry layout to container for ID: " + uniqueId);
                }
            } else {
                Log.d(TAG, "JSON string is empty or no data read. Displaying no entries message.");
                displayNoEntriesMessage(); // If file is empty or contains no valid JSON, display "No entries yet"
            }
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Error loading diary entries: " + e.getMessage(), e);
            displayNoEntriesMessage(); // If any parsing or file reading error, display "No entries yet"
        }
    }

    /**
     * Displays a message when no entries are found or when an error occurs during loading.
     */
    private void displayNoEntriesMessage() {
        Log.d(TAG, "Displaying 'No entries yet' message.");
        TextView noEntriesTextView = new TextView(this);
        noEntriesTextView.setText("No entries yet.");
        noEntriesTextView.setTextSize(16f);
        noEntriesTextView.setTextColor(ContextCompat.getColor(this, R.color.medium_gray_text));
        noEntriesTextView.setGravity(Gravity.CENTER_HORIZONTAL); // Center horizontally
        pastEntriesContainer.removeAllViews(); // Ensure only this message is displayed
        pastEntriesContainer.addView(noEntriesTextView);
    }
}
