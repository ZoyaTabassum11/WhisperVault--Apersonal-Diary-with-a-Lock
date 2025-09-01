// EntryDetailActivity.java
package com.example.madproject; // Adjust your package name accordingly

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.util.Objects;

public class EntryDetailActivity extends AppCompatActivity {

    private static final String TAG = "EntryDetailActivity";

    private TextView detailTimestamp;
    private EditText detailEntryText;
    private ImageView detailImageView;
    private Button changeImageButton;
    private Button updateEntryButton;
    private Button deleteEntryButton;

    private JSONObject currentEntry; // The JSON object of the entry being viewed/edited
    private Uri currentImageUri; // The URI of the image associated with this entry
    private long entryUniqueId; // The unique ID of the entry

    private static final String DIARY_FILE_NAME = "diary_entries.json";

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry_detail);

        // Initialize UI elements
        detailTimestamp = findViewById(R.id.detailTimestamp);
        detailEntryText = findViewById(R.id.detailEntryText);
        detailImageView = findViewById(R.id.detailImageView);
        changeImageButton = findViewById(R.id.changeImageButton);
        updateEntryButton = findViewById(R.id.updateEntryButton);
        deleteEntryButton = findViewById(R.id.deleteEntryButton);

        // Initialize pickImageLauncher for selecting new images when editing
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        currentImageUri = result.getData().getData();
                        if (currentImageUri != null) {
                            detailImageView.setImageURI(currentImageUri);
                            detailImageView.setVisibility(View.VISIBLE);
                            try {
                                getContentResolver().takePersistableUriPermission(currentImageUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                Log.e(TAG, "Failed to persist URI permission for changed image: " + e.getMessage());
                                Toast.makeText(this, "Could not get persistent URI permission.", Toast.LENGTH_LONG).show();
                            }
                            Toast.makeText(this, "Image changed!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // If image selection cancelled, keep the old image if any, or set to null
                        // No change to currentImageUri if cancelled unless user wants to remove it explicitly
                        Toast.makeText(this, "Image change cancelled.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Initialize requestPermissionLauncher for requesting storage permissions
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "Permission granted: Proceeding to pick image for edit.");
                        pickImageForEdit();
                    } else {
                        Log.w(TAG, "Permission denied for edit.");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                !shouldShowRequestPermissionRationale(getPermissionToRequest()) &&
                                !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !shouldShowRequestPermissionRationale(getPermissionToRequest()))) {
                            showPermissionDeniedDialog();
                        } else {
                            Toast.makeText(this, "Permission to access storage denied. Cannot change image.", Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );


        // Retrieve data from the intent
        String entryJsonString = getIntent().getStringExtra("entryJson");
        if (entryJsonString != null) {
            try {
                currentEntry = new JSONObject(entryJsonString);
                entryUniqueId = currentEntry.getLong("uniqueId"); // Get the unique ID

                detailTimestamp.setText(currentEntry.getString("timestamp"));
                detailEntryText.setText(currentEntry.getString("text"));

                String imageUriStr = currentEntry.optString("imageUri", null);
                if (imageUriStr != null && !imageUriStr.isEmpty()) {
                    currentImageUri = Uri.parse(imageUriStr);
                    detailImageView.setImageURI(currentImageUri);
                    detailImageView.setVisibility(View.VISIBLE);
                } else {
                    detailImageView.setVisibility(View.GONE);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing entry JSON: " + e.getMessage(), e);
                Toast.makeText(this, "Error loading entry details.", Toast.LENGTH_LONG).show();
                finish(); // Close activity if data is corrupted
            }
        } else {
            Toast.makeText(this, "No entry data provided.", Toast.LENGTH_SHORT).show();
            finish(); // Close activity if no data
        }

        // Set listeners
        changeImageButton.setOnClickListener(v -> checkAndRequestPermissionForEdit());
        updateEntryButton.setOnClickListener(v -> updateEntry());
        deleteEntryButton.setOnClickListener(v -> confirmDeleteEntry());
    }

    /**
     * Determines the correct storage permission to request based on Android version.
     */
    private String getPermissionToRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    private void checkAndRequestPermissionForEdit() {
        String permission = getPermissionToRequest();
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission already granted for edit: " + permission);
            pickImageForEdit();
        } else {
            Log.d(TAG, "Requesting permission for edit: " + permission);
            requestPermissionLauncher.launch(permission);
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Storage access is needed to change images. Please enable it in App Settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Image change unavailable without permission.", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    private void pickImageForEdit() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    /**
     * Updates the current diary entry in the JSON file.
     */
    private void updateEntry() {
        String updatedText = detailEntryText.getText().toString().trim();

        if (TextUtils.isEmpty(updatedText) && currentImageUri == null) {
            Toast.makeText(this, "Cannot save an empty entry. Please add text or an image.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray existingEntries = readDiaryFile();
            JSONArray updatedEntries = new JSONArray();
            boolean entryFound = false;

            for (int i = 0; i < existingEntries.length(); i++) {
                JSONObject entry = existingEntries.getJSONObject(i);
                if (entry.getLong("uniqueId") == entryUniqueId) {
                    // This is the entry to update
                    entry.put("text", updatedText);
                    if (currentImageUri != null) {
                        entry.put("imageUri", currentImageUri.toString());
                    } else {
                        // If image was removed or never existed
                        entry.remove("imageUri");
                    }
                    entryFound = true;
                }
                updatedEntries.put(entry);
            }

            if (entryFound) {
                writeDiaryFile(updatedEntries);
                Toast.makeText(this, "Entry updated successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK); // Indicate that an update occurred
                finish(); // Close this activity
            } else {
                Toast.makeText(this, "Error: Entry not found for update.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating entry: " + e.getMessage(), e);
            Toast.makeText(this, "Error updating entry: " + e.getMessage(), Toast.LENGTH_LONG).show(); // Corrected line
            setResult(RESULT_CANCELED);
        }
    }

    /**
     * Prompts the user to confirm deletion of the entry.
     */
    private void confirmDeleteEntry() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Entry")
                .setMessage("Are you sure you want to delete this diary entry? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteEntry())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Deletes the current diary entry from the JSON file.
     */
    private void deleteEntry() {
        try {
            JSONArray existingEntries = readDiaryFile();
            JSONArray remainingEntries = new JSONArray();
            boolean entryFound = false;

            for (int i = 0; i < existingEntries.length(); i++) {
                JSONObject entry = existingEntries.getJSONObject(i);
                if (entry.getLong("uniqueId") != entryUniqueId) {
                    // Add all entries except the one to be deleted
                    remainingEntries.put(entry);
                } else {
                    entryFound = true;
                }
            }

            if (entryFound) {
                writeDiaryFile(remainingEntries);
                Toast.makeText(this, "Entry deleted successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK); // Indicate that a deletion occurred
                finish(); // Close this activity
            } else {
                Toast.makeText(this, "Error: Entry not found for deletion.", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error deleting entry: " + e.getMessage(), e);
            Toast.makeText(this, "Error deleting entry: " + e.getMessage(), Toast.LENGTH_LONG).show(); // Corrected line
            setResult(RESULT_CANCELED);
        }
    }

    /**
     * Helper method to read the entire diary JSON file into a JSONArray.
     * @return JSONArray containing all diary entries.
     * @throws Exception if file cannot be read or parsed.
     */
    private JSONArray readDiaryFile() throws Exception {
        FileInputStream fis = null;
        try {
            fis = openFileInput(DIARY_FILE_NAME);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();
            if (jsonString.length() > 0) {
                return new JSONArray(jsonString.toString());
            } else {
                return new JSONArray(); // Return empty array if file is empty
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing FileInputStream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Helper method to write a JSONArray to the diary file.
     * @param jsonArray The JSONArray to write.
     * @throws Exception if file cannot be written.
     */
    private void writeDiaryFile(JSONArray jsonArray) throws Exception {
        FileOutputStream fos = null;
        try {
            fos = openFileOutput(DIARY_FILE_NAME, Context.MODE_PRIVATE);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            osw.write(jsonArray.toString(4)); // Indent for readability
            osw.flush();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing FileOutputStream: " + e.getMessage());
                }
            }
        }
    }
}
