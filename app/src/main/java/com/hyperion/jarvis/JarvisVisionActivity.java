package com.hyperion.jarvis;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class JarvisVisionActivity extends Activity implements View.OnClickListener {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_LABEL = "label";
    public static final String MODE_OBJECT = "object";
    public static final String MODE_PRODUCT = "product";
    public static final String MODE_BARCODE = "barcode";
    public static final String MODE_FACE_RECOGNISE = "face_recognise";
    public static final String MODE_FACE_LEARN = "face_learn";
    private static final int REQUEST_CAMERA = 5521;
    private static final int REQUEST_BARCODE = 5522;

    private TextView resultView;
    private ImageView imageView;
    private Button captureButton;
    private Button lensButton;
    private Button productButton;
    private Button scannerButton;
    private String mode;
    private String label;
    private String lastProductQuery;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent() == null ? MODE_OBJECT : getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null || mode.length() == 0) {
            mode = MODE_OBJECT;
        }
        label = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_LABEL);
        buildUi();
        if (MODE_BARCODE.equals(mode)) {
            launchBarcodeScanner();
        } else {
            launchCamera();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(2, 7, 11));

        TextView title = new TextView(this);
        title.setText(getTitleForMode());
        title.setTextColor(Color.rgb(0, 216, 255));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setBackgroundColor(Color.rgb(0, 20, 28));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        imageParams.topMargin = 18;
        root.addView(imageView, imageParams);

        ScrollView scrollView = new ScrollView(this);
        resultView = new TextView(this);
        resultView.setTextColor(Color.rgb(210, 250, 255));
        resultView.setTextSize(15);
        resultView.setPadding(10, 10, 10, 10);
        resultView.setText("Camera launching...");
        scrollView.addView(resultView);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 190);
        resultParams.topMargin = 12;
        root.addView(scrollView, resultParams);

        captureButton = makeButton(MODE_BARCODE.equals(mode) ? "Scan Again" : "Capture Again");
        captureButton.setId(1);
        root.addView(captureButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        lensButton = makeButton("Open Google Lens / Visual Search");
        lensButton.setId(2);
        root.addView(lensButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        productButton = makeButton("Search Product Web");
        productButton.setId(3);
        root.addView(productButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scannerButton = makeButton("QR / Barcode Scanner");
        scannerButton.setId(4);
        root.addView(scannerButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setOnClickListener(this);
        return button;
    }

    private String getTitleForMode() {
        if (MODE_FACE_LEARN.equals(mode)) {
            return "J.A.R.V.I.S Face Enrolment";
        }
        if (MODE_FACE_RECOGNISE.equals(mode)) {
            return "J.A.R.V.I.S Face Recognition";
        }
        if (MODE_PRODUCT.equals(mode)) {
            return "J.A.R.V.I.S Product Vision";
        }
        if (MODE_BARCODE.equals(mode)) {
            return "J.A.R.V.I.S QR / Barcode";
        }
        return "J.A.R.V.I.S Vision Core";
    }

    public void onClick(View view) {
        int id = view.getId();
        if (id == 1) {
            if (MODE_BARCODE.equals(mode)) {
                launchBarcodeScanner();
            } else {
                launchCamera();
            }
        } else if (id == 2) {
            boolean opened = JarvisVisionEngine.openGoogleLens(this);
            if (!opened) {
                Toast.makeText(this, "Google Lens could not be opened", Toast.LENGTH_SHORT).show();
            }
        } else if (id == 3) {
            if (lastProductQuery == null || lastProductQuery.length() == 0) {
                lastProductQuery = "unknown product from camera image";
            }
            JarvisVisionEngine.openProductSearch(this, lastProductQuery);
        } else if (id == 4) {
            mode = MODE_BARCODE;
            launchBarcodeScanner();
        }
    }

    private void launchCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (Exception error) {
            resultView.setText("Camera app could not be opened: " + error.getMessage());
        }
    }

    private void launchBarcodeScanner() {
        resultView.setText("Opening QR/barcode scanner. If no scanner app is installed, I will open Google Lens instead.");
        try {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE,PRODUCT_MODE");
            startActivityForResult(intent, REQUEST_BARCODE);
        } catch (ActivityNotFoundException notFound) {
            boolean opened = JarvisVisionEngine.openGoogleLens(this);
            if (opened) {
                resultView.setText("No ZXing-compatible scanner was found, so I opened Google Lens. Use Lens to scan the QR code or barcode.");
            } else {
                resultView.setText("No scanner app was found. Install a ZXing-compatible barcode scanner or Google Lens, then try again.");
            }
        } catch (Exception error) {
            resultView.setText("Barcode scanner could not be opened: " + error.getMessage());
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BARCODE) {
            handleBarcodeResult(resultCode, data);
            return;
        }
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Object object = data.getExtras().get("data");
            if (object instanceof Bitmap) {
                Bitmap bitmap = (Bitmap) object;
                imageView.setImageBitmap(bitmap);
                String result = analyseBitmap(bitmap);
                resultView.setText(result);
                Toast.makeText(this, "Jarvis vision complete", Toast.LENGTH_SHORT).show();
            } else {
                resultView.setText("The camera did not return an image thumbnail.");
            }
        } else {
            resultView.setText("Camera capture cancelled.");
        }
    }

    private void handleBarcodeResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            String contents = data.getStringExtra("SCAN_RESULT");
            String format = data.getStringExtra("SCAN_RESULT_FORMAT");
            if (contents == null) contents = "";
            if (format == null) format = "unknown";
            StringBuilder builder = new StringBuilder();
            builder.append("Scan result\nFormat: ").append(format).append("\nValue: ").append(contents);
            if (isWebAddress(contents)) {
                builder.append("\nOpening link.");
                openUri(contents);
            } else if (contents.length() > 0) {
                builder.append("\nOpening product/web search for this code.");
                JarvisVisionEngine.openProductSearch(this, contents);
            }
            resultView.setText(builder.toString());
        } else {
            resultView.setText("QR/barcode scan cancelled.");
        }
    }

    private boolean isWebAddress(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.");
    }

    private void openUri(String value) {
        if (value == null || value.length() == 0) {
            return;
        }
        try {
            String uriText = value.startsWith("www.") ? "https://" + value : value;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriText));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private String analyseBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return "No image available.";
        }
        int faceCount = detectFaces(bitmap);
        float[] signature = JarvisVisionEngine.buildAdvancedFaceEmbedding(bitmap);
        if (MODE_FACE_LEARN.equals(mode)) {
            if (faceCount <= 0) {
                return "I could not detect a face to learn. Try again in better light, facing the camera directly.";
            }
            String faceLabel = label == null || label.trim().length() == 0 ? "Unknown person" : label.trim();
            JarvisFaceStore.saveFace(this, faceLabel, signature);
            return "Advanced face profile stored for " + faceLabel + ". This uses a stronger local embedding with texture and gradient matching. Enrol two or three samples from slightly different angles for best results.";
        }
        if (MODE_FACE_RECOGNISE.equals(mode)) {
            if (faceCount <= 0) {
                return "I could not detect a face in the camera image.";
            }
            return JarvisFaceStore.recognise(this, signature);
        }
        if (MODE_PRODUCT.equals(mode)) {
            lastProductQuery = JarvisVisionEngine.buildProductSearchQuery(bitmap);
            boolean opened = JarvisVisionEngine.openGoogleLens(this);
            String result = JarvisVisionEngine.buildSceneDescription(bitmap, faceCount);
            result += "\n\nProduct search hint: " + lastProductQuery + ".";
            if (opened) {
                result += "\n\nI opened Google Lens/visual search for stronger product identification.";
            } else {
                result += "\n\nGoogle Lens was not available. Tap Search Product Web to search from the extracted visual hint.";
            }
            return result;
        }
        return JarvisVisionEngine.buildSceneDescription(bitmap, faceCount);
    }

    private int detectFaces(Bitmap source) {
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            if ((width & 1) == 1) {
                width--;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, false);
            Bitmap rgb565 = scaled.copy(Bitmap.Config.RGB_565, true);
            FaceDetector detector = new FaceDetector(rgb565.getWidth(), rgb565.getHeight(), 8);
            FaceDetector.Face[] faces = new FaceDetector.Face[8];
            return detector.findFaces(rgb565, faces);
        } catch (Exception ignored) {
            return 0;
        }
    }
}
