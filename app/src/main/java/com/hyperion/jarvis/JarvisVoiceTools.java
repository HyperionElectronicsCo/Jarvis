package com.hyperion.jarvis;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

public final class JarvisVoiceTools {
    private JarvisVoiceTools() {
    }

    public static void configureJarvisVoice(TextToSpeech textToSpeech) {
        if (textToSpeech == null) {
            return;
        }
        int languageStatus = textToSpeech.setLanguage(Locale.UK);
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech.setLanguage(Locale.getDefault());
        }

        if (Build.VERSION.SDK_INT >= 21) {
            chooseBestVoice(textToSpeech);
        }

        textToSpeech.setSpeechRate(0.86f);
        textToSpeech.setPitch(0.72f);
    }

    private static void chooseBestVoice(TextToSpeech textToSpeech) {
        try {
            Set<Voice> voices = textToSpeech.getVoices();
            if (voices == null) {
                return;
            }

            Voice firstEnglishUk = null;
            Voice bestNamedVoice = null;
            Iterator<Voice> iterator = voices.iterator();
            while (iterator.hasNext()) {
                Voice voice = iterator.next();
                if (voice == null || voice.getLocale() == null) {
                    continue;
                }
                String language = voice.getLocale().getLanguage();
                String country = voice.getLocale().getCountry();
                String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.UK);
                boolean english = "en".equalsIgnoreCase(language);
                boolean uk = "gb".equalsIgnoreCase(country) || "uk".equalsIgnoreCase(country);

                if (english && uk && firstEnglishUk == null) {
                    firstEnglishUk = voice;
                }
                if (english && (name.indexOf("gb") >= 0 || name.indexOf("uk") >= 0 || name.indexOf("male") >= 0 || name.indexOf("rjs") >= 0)) {
                    bestNamedVoice = voice;
                    if (name.indexOf("male") >= 0 || name.indexOf("rjs") >= 0) {
                        break;
                    }
                }
            }

            if (bestNamedVoice != null) {
                textToSpeech.setVoice(bestNamedVoice);
            } else if (firstEnglishUk != null) {
                textToSpeech.setVoice(firstEnglishUk);
            }
        } catch (Exception ignored) {
        }
    }
}
