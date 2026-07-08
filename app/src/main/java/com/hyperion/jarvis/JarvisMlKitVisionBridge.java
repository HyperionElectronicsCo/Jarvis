package com.hyperion.jarvis;

import android.content.Context;
import android.graphics.Bitmap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class JarvisMlKitVisionBridge implements JarvisVisionBridge {
    public JarvisVisionBridgeResult identify(Context context, Bitmap bitmap, boolean productMode) {
        if (bitmap == null) {
            return JarvisVisionBridgeResult.unavailable("ML Kit", "No bitmap was available for ML Kit.");
        }
        try {
            Object image = createInputImage(bitmap);
            StringBuilder details = new StringBuilder();

            String ocrText = runTextRecognition(image);
            if (ocrText != null && ocrText.trim().length() > 0) {
                appendDetail(details, "OCR text: " + compact(ocrText, 220));
                String product = productNameFromText(ocrText);
                if (product.length() > 0) {
                    return JarvisVisionBridgeResult.recognised("ML Kit OCR", product, details.toString(), 0.96f);
                }
            }

            String barcodeText = runBarcodeScanning(image);
            if (barcodeText != null && barcodeText.trim().length() > 0) {
                appendDetail(details, "Barcode/QR data: " + compact(barcodeText, 180));
                String product = productNameFromText(barcodeText);
                if (product.length() > 0) {
                    return JarvisVisionBridgeResult.recognised("ML Kit Barcode", product, details.toString(), 0.94f);
                }
            }

            RecognitionSummary objectSummary = runObjectDetection(image);
            if (objectSummary != null && objectSummary.label.length() > 0) {
                appendDetail(details, "Objects: " + objectSummary.details);
            }

            RecognitionSummary labelSummary = runImageLabeling(image);
            if (labelSummary != null && labelSummary.label.length() > 0) {
                appendDetail(details, "Labels: " + labelSummary.details);
            }

            String best = chooseBestVisualLabel(objectSummary, labelSummary, productMode);
            float confidence = 0.0f;
            if (objectSummary != null && best.equals(objectSummary.label)) {
                confidence = objectSummary.confidence;
            }
            if (labelSummary != null && best.equals(labelSummary.label) && labelSummary.confidence > confidence) {
                confidence = labelSummary.confidence;
            }
            if (best.length() > 0) {
                return JarvisVisionBridgeResult.recognised("ML Kit Vision", best, details.toString(), Math.max(confidence, 0.50f));
            }

            if (details.length() > 0) {
                return JarvisVisionBridgeResult.recognised("ML Kit Vision", "unknown object", details.toString(), 0.30f);
            }
            return JarvisVisionBridgeResult.unavailable("ML Kit", "ML Kit returned no OCR text, object labels, barcode data, or image labels.");
        } catch (ClassNotFoundException notInstalled) {
            JarvisVisionBridgeResult mobileVision = runMobileVisionOcr(context, bitmap, productMode);
            if (mobileVision != null && mobileVision.hasUsefulLabel()) {
                return mobileVision;
            }
            if (mobileVision != null && mobileVision.available) {
                return mobileVision;
            }
            return JarvisVisionBridgeResult.unavailable("On-device OCR", "ML Kit classes are not available, and the Mobile Vision OCR fallback is not ready yet. Rebuild after Gradle downloads play-services-vision.");
        } catch (Throwable error) {
            return JarvisVisionBridgeResult.unavailable("ML Kit", "ML Kit could not complete recognition: " + safeMessage(error));
        }
    }

    private Object createInputImage(Bitmap bitmap) throws Exception {
        Class inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage");
        Method fromBitmap = inputImageClass.getMethod("fromBitmap", new Class[] { Bitmap.class, Integer.TYPE });
        return fromBitmap.invoke(null, new Object[] { bitmap, Integer.valueOf(0) });
    }

    private Object awaitTask(Object task, long seconds) throws Exception {
        Class taskClass = Class.forName("com.google.android.gms.tasks.Task");
        Class tasksClass = Class.forName("com.google.android.gms.tasks.Tasks");
        Method await = tasksClass.getMethod("await", new Class[] { taskClass, Long.TYPE, TimeUnit.class });
        return await.invoke(null, new Object[] { task, Long.valueOf(seconds), TimeUnit.SECONDS });
    }

    private String runTextRecognition(Object image) throws Exception {
        Object recognizer = null;
        try {
            Class inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage");
            Class textRecognitionClass = Class.forName("com.google.mlkit.vision.text.TextRecognition");
            Class optionsClass = Class.forName("com.google.mlkit.vision.text.latin.TextRecognizerOptions");
            Field defaultOptionsField = optionsClass.getField("DEFAULT_OPTIONS");
            Object defaultOptions = defaultOptionsField.get(null);
            Method getClient = textRecognitionClass.getMethod("getClient", new Class[] { optionsClass });
            recognizer = getClient.invoke(null, new Object[] { defaultOptions });
            Method process = recognizer.getClass().getMethod("process", new Class[] { inputImageClass });
            Object task = process.invoke(recognizer, new Object[] { image });
            Object result = awaitTask(task, 10L);
            if (result == null) {
                return "";
            }
            Method getText = result.getClass().getMethod("getText", new Class[0]);
            Object text = getText.invoke(result, new Object[0]);
            return text == null ? "" : String.valueOf(text);
        } finally {
            closeIfPossible(recognizer);
        }
    }

    private String runBarcodeScanning(Object image) throws Exception {
        Object scanner = null;
        try {
            Class inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage");
            Class scanningClass = Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning");
            Method getClient = scanningClass.getMethod("getClient", new Class[0]);
            scanner = getClient.invoke(null, new Object[0]);
            Method process = scanner.getClass().getMethod("process", new Class[] { inputImageClass });
            Object task = process.invoke(scanner, new Object[] { image });
            Object result = awaitTask(task, 8L);
            if (!(result instanceof List)) {
                return "";
            }
            List list = (List) result;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < list.size() && i < 4; i++) {
                Object item = list.get(i);
                if (item == null) continue;
                String raw = callString(item, "getRawValue");
                String display = callString(item, "getDisplayValue");
                if (builder.length() > 0) builder.append(" | ");
                builder.append(raw.length() > 0 ? raw : display);
            }
            return builder.toString();
        } finally {
            closeIfPossible(scanner);
        }
    }

    private RecognitionSummary runImageLabeling(Object image) throws Exception {
        Object labeler = null;
        try {
            Class inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage");
            Class labelerOptionsClass = Class.forName("com.google.mlkit.vision.label.defaults.ImageLabelerOptions");
            Class imageLabelingClass = Class.forName("com.google.mlkit.vision.label.ImageLabeling");
            Field defaultOptionsField = labelerOptionsClass.getField("DEFAULT_OPTIONS");
            Object defaultOptions = defaultOptionsField.get(null);
            Method getClient = imageLabelingClass.getMethod("getClient", new Class[] { labelerOptionsClass });
            labeler = getClient.invoke(null, new Object[] { defaultOptions });
            Method process = labeler.getClass().getMethod("process", new Class[] { inputImageClass });
            Object task = process.invoke(labeler, new Object[] { image });
            Object labels = awaitTask(task, 9L);
            return parseLabelList(labels);
        } finally {
            closeIfPossible(labeler);
        }
    }

    private RecognitionSummary runObjectDetection(Object image) throws Exception {
        Object detector = null;
        try {
            Class inputImageClass = Class.forName("com.google.mlkit.vision.common.InputImage");
            Class optionsClass = Class.forName("com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions");
            Class builderClass = Class.forName("com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions$Builder");
            Object builder = builderClass.newInstance();
            Field singleImageField = optionsClass.getField("SINGLE_IMAGE_MODE");
            int singleImageMode = singleImageField.getInt(null);
            builderClass.getMethod("setDetectorMode", new Class[] { Integer.TYPE }).invoke(builder, new Object[] { Integer.valueOf(singleImageMode) });
            builderClass.getMethod("enableMultipleObjects", new Class[0]).invoke(builder, new Object[0]);
            builderClass.getMethod("enableClassification", new Class[0]).invoke(builder, new Object[0]);
            Object options = builderClass.getMethod("build", new Class[0]).invoke(builder, new Object[0]);
            Class objectDetectionClass = Class.forName("com.google.mlkit.vision.objects.ObjectDetection");
            Method getClient = objectDetectionClass.getMethod("getClient", new Class[] { optionsClass });
            detector = getClient.invoke(null, new Object[] { options });
            Method process = detector.getClass().getMethod("process", new Class[] { inputImageClass });
            Object task = process.invoke(detector, new Object[] { image });
            Object detected = awaitTask(task, 9L);
            return parseDetectedObjects(detected);
        } finally {
            closeIfPossible(detector);
        }
    }

    private RecognitionSummary parseDetectedObjects(Object objects) throws Exception {
        if (!(objects instanceof List)) {
            return null;
        }
        List list = (List) objects;
        StringBuilder details = new StringBuilder();
        String bestLabel = "";
        float bestConfidence = 0.0f;
        for (int i = 0; i < list.size() && i < 5; i++) {
            Object detected = list.get(i);
            if (detected == null) continue;
            Method getLabels = detected.getClass().getMethod("getLabels", new Class[0]);
            Object labels = getLabels.invoke(detected, new Object[0]);
            if (labels instanceof List) {
                List labelList = (List) labels;
                for (int j = 0; j < labelList.size(); j++) {
                    Object label = labelList.get(j);
                    String text = callString(label, "getText");
                    float conf = callFloat(label, "getConfidence");
                    if (text.length() == 0) {
                        text = "object";
                    }
                    if (details.length() > 0) details.append(", ");
                    details.append(text).append(" ").append(Math.round(conf * 100.0f)).append("%");
                    if (conf > bestConfidence) {
                        bestConfidence = conf;
                        bestLabel = text;
                    }
                }
            }
        }
        RecognitionSummary summary = new RecognitionSummary();
        summary.label = bestLabel;
        summary.confidence = bestConfidence;
        summary.details = details.toString();
        return summary;
    }

    private RecognitionSummary parseLabelList(Object labels) throws Exception {
        if (!(labels instanceof List)) {
            return null;
        }
        List list = (List) labels;
        String bestLabel = "";
        float bestConfidence = 0.0f;
        StringBuilder detail = new StringBuilder();
        int max = Math.min(list.size(), 8);
        for (int i = 0; i < max; i++) {
            Object item = list.get(i);
            if (item == null) continue;
            String text = callString(item, "getText");
            float confidence = callFloat(item, "getConfidence");
            if (detail.length() > 0) detail.append(", ");
            detail.append(text).append(" ").append(Math.round(confidence * 100.0f)).append("%");
            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestLabel = text;
            }
        }
        RecognitionSummary summary = new RecognitionSummary();
        summary.label = bestLabel;
        summary.confidence = bestConfidence;
        summary.details = detail.toString();
        return summary;
    }

    private String chooseBestVisualLabel(RecognitionSummary objectSummary, RecognitionSummary labelSummary, boolean productMode) {
        String objectLabel = objectSummary == null ? "" : safe(objectSummary.label);
        String label = labelSummary == null ? "" : safe(labelSummary.label);
        float objectConfidence = objectSummary == null ? 0.0f : objectSummary.confidence;
        float labelConfidence = labelSummary == null ? 0.0f : labelSummary.confidence;
        if (productMode) {
            if (isUsefulProductLabel(objectLabel) && objectConfidence >= 0.35f) {
                return objectLabel;
            }
            if (isUsefulProductLabel(label) && labelConfidence >= 0.35f) {
                return label;
            }
        }
        if (objectConfidence >= labelConfidence && objectLabel.length() > 0) {
            return objectLabel;
        }
        return label;
    }

    private boolean isUsefulProductLabel(String label) {
        String lower = label == null ? "" : label.toLowerCase(Locale.UK);
        if (lower.length() == 0 || lower.indexOf("unknown") >= 0) return false;
        return lower.indexOf("food") >= 0 || lower.indexOf("drink") >= 0 || lower.indexOf("bottle") >= 0
                || lower.indexOf("can") >= 0 || lower.indexOf("product") >= 0 || lower.indexOf("home") >= 0
                || lower.indexOf("goods") >= 0 || lower.indexOf("fashion") >= 0 || lower.indexOf("plant") >= 0
                || lower.indexOf("phone") >= 0 || lower.indexOf("electronic") >= 0 || lower.indexOf("object") >= 0;
    }


    private JarvisVisionBridgeResult runMobileVisionOcr(Context context, Bitmap bitmap, boolean productMode) {
        if (context == null || bitmap == null) {
            return JarvisVisionBridgeResult.unavailable("Mobile Vision OCR", "No image was available.");
        }
        Object recognizer = null;
        try {
            Class builderClass = Class.forName("com.google.android.gms.vision.text.TextRecognizer$Builder");
            Object builder = builderClass.getConstructor(new Class[] { Context.class }).newInstance(new Object[] { context.getApplicationContext() });
            Method build = builderClass.getMethod("build", new Class[0]);
            recognizer = build.invoke(builder, new Object[0]);

            try {
                Method operational = recognizer.getClass().getMethod("isOperational", new Class[0]);
                Object ok = operational.invoke(recognizer, new Object[0]);
                if (ok instanceof Boolean && !((Boolean) ok).booleanValue()) {
                    return JarvisVisionBridgeResult.unavailable("Mobile Vision OCR", "OCR engine is not operational yet. Open Product Vision again after Google Play services finishes preparing OCR.");
                }
            } catch (Exception ignored) {
            }

            Class frameBuilderClass = Class.forName("com.google.android.gms.vision.Frame$Builder");
            Object frameBuilder = frameBuilderClass.newInstance();
            Method setBitmap = frameBuilderClass.getMethod("setBitmap", new Class[] { Bitmap.class });
            setBitmap.invoke(frameBuilder, new Object[] { bitmap });
            Method buildFrame = frameBuilderClass.getMethod("build", new Class[0]);
            Object frame = buildFrame.invoke(frameBuilder, new Object[0]);

            Class frameClass = Class.forName("com.google.android.gms.vision.Frame");
            Method detect = recognizer.getClass().getMethod("detect", new Class[] { frameClass });
            Object sparseArray = detect.invoke(recognizer, new Object[] { frame });
            String text = sparseArrayToText(sparseArray);

            if (text != null && text.trim().length() > 0) {
                String product = productNameFromText(text);
                String details = "OCR text: " + compact(text, 260);
                if (product.length() > 0) {
                    return JarvisVisionBridgeResult.recognised("Mobile Vision OCR", product, details, 0.94f);
                }
                return JarvisVisionBridgeResult.recognised("Mobile Vision OCR", "text detected on product", details, 0.52f);
            }
            return JarvisVisionBridgeResult.unavailable("Mobile Vision OCR", "No readable packaging text was detected.");
        } catch (ClassNotFoundException missing) {
            return JarvisVisionBridgeResult.unavailable("Mobile Vision OCR", "play-services-vision is not available in this build.");
        } catch (Throwable error) {
            return JarvisVisionBridgeResult.unavailable("Mobile Vision OCR", "OCR fallback failed: " + safeMessage(error));
        } finally {
            closeIfPossible(recognizer);
        }
    }

    private String sparseArrayToText(Object sparseArray) {
        if (sparseArray == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try {
            Method sizeMethod = sparseArray.getClass().getMethod("size", new Class[0]);
            Method valueAtMethod = sparseArray.getClass().getMethod("valueAt", new Class[] { Integer.TYPE });
            Object sizeValue = sizeMethod.invoke(sparseArray, new Object[0]);
            int size = sizeValue instanceof Integer ? ((Integer) sizeValue).intValue() : 0;
            for (int i = 0; i < size; i++) {
                Object block = valueAtMethod.invoke(sparseArray, new Object[] { Integer.valueOf(i) });
                if (block == null) {
                    continue;
                }
                try {
                    Method getValue = block.getClass().getMethod("getValue", new Class[0]);
                    Object value = getValue.invoke(block, new Object[0]);
                    if (value != null) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(String.valueOf(value));
                    }
                } catch (Exception ignored) {
                    String value = String.valueOf(block);
                    if (value != null && value.length() > 0) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(value);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return builder.toString();
    }

    private String productNameFromText(String text) {
        if (text == null) return "";
        String cleaned = text.toUpperCase(Locale.UK).replace('-', ' ').replace('_', ' ');
        cleaned = cleaned.replaceAll("[^A-Z0-9]+", " ");
        String padded = " " + cleaned + " ";
        if ((padded.indexOf(" IRN ") >= 0 || padded.indexOf(" IRON ") >= 0) && (padded.indexOf(" BRU ") >= 0 || padded.indexOf(" 8RU ") >= 0 || padded.indexOf(" BRV ") >= 0)) {
            return "IRN-BRU soft drink can";
        }
        if (padded.indexOf(" PEPSI ") >= 0 && padded.indexOf(" MAX ") >= 0) return "Pepsi Max drink can";
        if (padded.indexOf(" PEPSI ") >= 0) return "Pepsi drink can";
        if ((padded.indexOf(" COCA ") >= 0 && padded.indexOf(" COLA ") >= 0) || padded.indexOf(" COKE ") >= 0) return "Coca-Cola drink can";
        if (padded.indexOf(" MONSTER ") >= 0) return "Monster Energy drink can";
        if (padded.indexOf(" RED ") >= 0 && padded.indexOf(" BULL ") >= 0) return "Red Bull energy drink can";
        if (padded.indexOf(" FANTA ") >= 0) return "Fanta soft drink bottle or can";
        if (padded.indexOf(" SPRITE ") >= 0) return "Sprite soft drink bottle or can";
        if (padded.indexOf(" DR ") >= 0 && padded.indexOf(" PEPPER ") >= 0) return "Dr Pepper drink can";
        if (padded.indexOf(" LUCOZADE ") >= 0) return "Lucozade drink bottle";
        if (padded.indexOf(" RIBENA ") >= 0) return "Ribena drink bottle";
        if (padded.indexOf(" NESCAFE ") >= 0) return "Nescafe product";
        if (padded.indexOf(" HEINZ ") >= 0) return "Heinz product";
        if (padded.indexOf(" SAMSUNG ") >= 0) return "Samsung product";
        if (padded.indexOf(" IPHONE ") >= 0 || padded.indexOf(" APPLE ") >= 0) return "Apple product";
        return "";
    }

    private String callString(Object target, String methodName) {
        if (target == null) return "";
        try {
            Method method = target.getClass().getMethod(methodName, new Class[0]);
            Object value = method.invoke(target, new Object[0]);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private float callFloat(Object target, String methodName) {
        if (target == null) return 0.0f;
        try {
            Method method = target.getClass().getMethod(methodName, new Class[0]);
            Object value = method.invoke(target, new Object[0]);
            return value instanceof Number ? ((Number) value).floatValue() : 0.0f;
        } catch (Exception ignored) {
            return 0.0f;
        }
    }

    private void closeIfPossible(Object object) {
        if (object == null) return;
        try {
            Method close = object.getClass().getMethod("close", new Class[0]);
            close.invoke(object, new Object[0]);
        } catch (Exception ignored) {
        }
    }

    private void appendDetail(StringBuilder builder, String value) {
        if (builder == null || value == null || value.length() == 0) return;
        if (builder.length() > 0) builder.append("\n");
        builder.append(value);
    }

    private String compact(String text, int max) {
        if (text == null) return "";
        String value = text.replace('\n', ' ').replace('\r', ' ').trim();
        value = value.replaceAll("\\s+", " ");
        if (value.length() > max) {
            return value.substring(0, max) + "...";
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && cause.getMessage().length() > 0) {
            return cause.getMessage();
        }
        String message = error.getMessage();
        if (message == null || message.length() == 0) message = error.getClass().getName();
        return message;
    }

    private static final class RecognitionSummary {
        String label = "";
        String details = "";
        float confidence = 0.0f;
    }
}
