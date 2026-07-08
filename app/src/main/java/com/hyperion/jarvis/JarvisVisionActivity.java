package com.hyperion.jarvis;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.AsyncTask;
import android.provider.MediaStore;
import java.io.File;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class JarvisVisionActivity extends Activity implements View.OnClickListener, JarvisOnlineBrain.VisionCallback {
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
    private String lastLocalResult;
    private Bitmap lastBitmap;
    private Uri pendingCameraUri;
    private File pendingCameraFile;
    private boolean autoLensLaunched;

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
            openLensForLastImage(true);
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
            pendingCameraFile = JarvisImageShareProvider.createImageFile(this, "jarvis_camera_capture");
            pendingCameraUri = pendingCameraFile == null ? null : JarvisImageShareProvider.getUriForFile(pendingCameraFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (pendingCameraUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                grantCameraWritePermissions(intent, pendingCameraUri);
                resultView.setText("Opening camera in full-resolution product mode. Take a clear photo filling the frame with the item label.");
            } else {
                resultView.setText("Opening camera in thumbnail mode. Full-resolution capture could not be prepared.");
            }
            startActivityForResult(intent, REQUEST_CAMERA);
        } catch (Exception error) {
            pendingCameraUri = null;
            pendingCameraFile = null;
            resultView.setText("Camera app could not be opened: " + error.getMessage());
        }
    }

    private void grantCameraWritePermissions(Intent intent, Uri uri) {
        if (intent == null || uri == null) {
            return;
        }
        try {
            java.util.List activities = getPackageManager().queryIntentActivities(intent, 0);
            for (int i = 0; activities != null && i < activities.size(); i++) {
                Object item = activities.get(i);
                if (item instanceof android.content.pm.ResolveInfo) {
                    android.content.pm.ResolveInfo info = (android.content.pm.ResolveInfo) item;
                    if (info.activityInfo != null && info.activityInfo.packageName != null) {
                        grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }
            }
        } catch (Exception ignored) {
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
        if (requestCode == REQUEST_CAMERA && resultCode == RESULT_OK) {
            Bitmap bitmap = loadCapturedBitmap(data);
            if (bitmap != null) {
                lastBitmap = bitmap;
                autoLensLaunched = false;
                imageView.setImageBitmap(bitmap);
                String result = analyseBitmap(bitmap);
                lastLocalResult = result;
                resultView.setText(result);
                requestBridgeVision(bitmap);
                requestAdvancedVision(bitmap);
                scheduleAutomaticLensIfNeeded(bitmap);
                Toast.makeText(this, "Jarvis vision analysis started", Toast.LENGTH_SHORT).show();
            } else {
                resultView.setText("The camera returned no usable image. Try Capture Again and keep the item label large in the frame.");
            }
        } else {
            resultView.setText("Camera capture cancelled.");
        }
    }

    private Bitmap loadCapturedBitmap(Intent data) {
        Bitmap full = decodePendingCameraFile();
        if (full != null) {
            return full;
        }
        if (data != null && data.getExtras() != null) {
            Object object = data.getExtras().get("data");
            if (object instanceof Bitmap) {
                return (Bitmap) object;
            }
        }
        return null;
    }

    private Bitmap decodePendingCameraFile() {
        if (pendingCameraFile == null || !pendingCameraFile.exists() || pendingCameraFile.length() <= 0) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(pendingCameraFile.getAbsolutePath(), bounds);
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / sample > 1600) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(pendingCameraFile.getAbsolutePath(), options);
        } catch (Exception ignored) {
            return null;
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
            String knownProduct = JarvisVisionEngine.guessKnownProduct(bitmap);
            lastProductQuery = JarvisVisionEngine.buildProductSearchQuery(bitmap);
            StringBuilder result = new StringBuilder();
            result.append("Product Vision captured.");
            result.append("\n\nLocal product hint:\n");
            result.append(JarvisVisionEngine.buildProductRecognitionHint(bitmap));
            result.append("\n\nSearch query: ").append(lastProductQuery).append('.');
            if (knownProduct != null && knownProduct.length() > 0) {
                result.append("\n\nLocal classification confidence: medium. AI vision and Lens will still be used for stronger identification.");
            } else {
                result.append("\n\nLocal classification confidence: low. AI vision and Lens will be used for stronger identification.");
            }
            result.append("\n\nRunning ML Kit OCR, ML Kit object/image recognition, optional Lens API, and AI image recognition now. Jarvis is using the full-resolution camera file where available, so product labels should be easier to identify.");
            result.append("\n\nIf the automatic recognisers are still weak, press OPEN GOOGLE LENS / VISUAL SEARCH to send this exact image to Lens, or SEARCH PRODUCT WEB for a strict product/image search.");
            return result.toString();
        }
        return JarvisVisionEngine.buildSceneDescription(bitmap, faceCount);
    }


    private void requestBridgeVision(final Bitmap bitmap) {
        if (bitmap == null || !MODE_PRODUCT.equals(mode)) {
            return;
        }
        new AsyncTask<Void, Void, JarvisVisionBridgeResult>() {
            protected JarvisVisionBridgeResult doInBackground(Void... params) {
                return JarvisVisionPipeline.identify(JarvisVisionActivity.this, bitmap, true);
            }

            protected void onPostExecute(JarvisVisionBridgeResult result) {
                if (result == null || resultView == null) {
                    return;
                }
                if (result.label != null && result.label.length() > 0 && !"unknown object".equals(result.label)) {
                    lastProductQuery = result.label + " product exact model brand photo";
                }
                String current = resultView.getText() == null ? "" : resultView.getText().toString();
                String text = result.toDisplayText();
                if (text == null || text.length() == 0) {
                    return;
                }
                appendToResult(current, "Vision pipeline:\n" + text);
            }
        }.execute();
    }

    private void requestAdvancedVision(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        boolean productMode = MODE_PRODUCT.equals(mode);
        JarvisOnlineBrain.requestVisionRecognition(this, bitmap, productMode, this);
    }

    private void scheduleAutomaticLensIfNeeded(final Bitmap bitmap) {
        if (!MODE_PRODUCT.equals(mode) || bitmap == null || resultView == null) {
            return;
        }
        resultView.postDelayed(new Runnable() {
            public void run() {
                if (!MODE_PRODUCT.equals(mode) || bitmap != lastBitmap || autoLensLaunched || resultView == null) {
                    return;
                }
                String current = resultView.getText() == null ? "" : resultView.getText().toString();
                appendToResult(current, "Full-resolution image is ready. Jarvis has run local/AI product recognition. Press OPEN GOOGLE LENS / VISUAL SEARCH if you want to hand this exact image to Lens.");
            }
        }, 2200);
    }

    private void openLensForLastImage(boolean showToast) {
        if (lastBitmap == null) {
            if (showToast) {
                Toast.makeText(this, "Capture an image first", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        autoLensLaunched = true;
        String query = lastProductQuery == null || lastProductQuery.length() == 0 ? "unknown product from camera image" : lastProductQuery;
        boolean opened = JarvisVisionEngine.openGoogleLens(this, lastBitmap, query);
        if (resultView != null) {
            String current = resultView.getText() == null ? "" : resultView.getText().toString();
            if (opened) {
                appendToResult(current, "Visual search launched with the captured full-resolution image. If Android shows a chooser, pick Google Lens or Google.");
            } else {
                appendToResult(current, "Google Lens could not be launched. Use SEARCH PRODUCT WEB for a strict fallback search.");
            }
        }
        if (showToast && !opened) {
            Toast.makeText(this, "Google Lens could not be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void appendToResult(String current, String message) {
        if (resultView == null) {
            return;
        }
        if (current == null) {
            current = "";
        }
        resultView.setText(current + "\n\n" + message);
    }

    public void onVisionResult(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                String value = text == null ? "" : text.trim();
                if (value.length() == 0) {
                    return;
                }
                StringBuilder builder = new StringBuilder();
                if (lastLocalResult != null && lastLocalResult.length() > 0) {
                    builder.append(lastLocalResult).append("\n\n");
                }
                if (MODE_PRODUCT.equals(mode) && isBadVisionFallbackResponse(value)) {
                    String current = resultView.getText() == null ? "" : resultView.getText().toString();
                    appendToResult(current, "AI recognition did not accept the captured image from this provider. Keeping the local/product-vision result and using Lens or product web search as fallback.");
                    return;
                }
                builder.append("AI recognition:\n").append(value);
                resultView.setText(builder.toString());
                if (MODE_PRODUCT.equals(mode) && value.toLowerCase().indexOf("likely product:") >= 0) {
                    String firstLine = value.split("\\n")[0].trim();
                    if (firstLine.toLowerCase().startsWith("likely product:")) {
                        lastProductQuery = firstLine.substring("likely product:".length()).trim();
                    }
                }
            }
        });
    }


    private boolean isBadVisionFallbackResponse(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.indexOf("no photo was provided") >= 0
                || lower.indexOf("no image was provided") >= 0
                || lower.indexOf("upload an image") >= 0
                || lower.indexOf("can't identify the item because no photo") >= 0
                || lower.indexOf("cannot identify the item because no photo") >= 0;
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
