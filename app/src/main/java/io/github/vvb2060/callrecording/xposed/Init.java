package io.github.vvb2060.callrecording.xposed;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Init implements IXposedHookLoadPackage {
    private static final String TAG = "CallRecording";
    private static final byte[] wav = {
            82, 73, 70, 70, 36, 0, 0, 0, 87, 65, 86,
            69, 102, 109, 116, 32, 16, 0, 0, 0, 1, 0,
            1, 0, -128, 62, 0, 0, 0, 125, 0, 0, 2,
            0, 16, 0, 100, 97, 116, 97, 0, 0, 0, 0};

    private static void hookCanRecordCall(DexHelper dex) {
        var canRecordCall = Arrays.stream(
                        dex.findMethodUsingString("canRecordCall",
                                false,
                                -1,
                                (short) 0,
                                "Z",
                                -1,
                                null,
                                null,
                                null,
                                true))
                .mapToObj(dex::decodeMethodIndex)
                .filter(Objects::nonNull)
                .findFirst();
        if (canRecordCall.isPresent()) {
            var method = canRecordCall.get();
            Log.d(TAG, "canRecordCall: " + method);
            XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "canRecordCall: true");
                    return true;
                }
            });
        } else {
            Log.e(TAG, "canRecordCall method not found");
        }
    }

    private static void hookWithinCrosbyGeoFence(DexHelper dex) {
        var withinCrosbyGeoFence = Arrays.stream(
                        dex.findMethodUsingString("withinCrosbyGeoFence",
                                false,
                                -1,
                                (short) 0,
                                "Z",
                                -1,
                                null,
                                null,
                                null,
                                true))
                .mapToObj(dex::decodeMethodIndex)
                .filter(Objects::nonNull)
                .findFirst();
        if (withinCrosbyGeoFence.isPresent()) {
            var method = withinCrosbyGeoFence.get();
            Log.d(TAG, "withinCrosbyGeoFence: " + method);
            XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "withinCrosbyGeoFence: true");
                    return true;
                }
            });
        } else {
            Log.e(TAG, "withinCrosbyGeoFence method not found");
        }
    }

    private static void hookGetCurrentLocaleForBuiltInAudioFile(DexHelper dex) {
        var getCurrentLocaleForBuiltInAudioFile = Arrays.stream(
                        dex.findMethodUsingString("getCurrentLocaleForBuiltInAudioFile",
                                false,
                                -1,
                                (short) 0,
                                null,
                                -1,
                                null,
                                null,
                                null,
                                true))
                .mapToObj(dex::decodeMethodIndex)
                .filter(Objects::nonNull)
                .findFirst();
        if (getCurrentLocaleForBuiltInAudioFile.isPresent()) {
            var method = getCurrentLocaleForBuiltInAudioFile.get();
            Log.d(TAG, "getCurrentLocaleForBuiltInAudioFile: " + method);
            XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "getCurrentLocaleForBuiltInAudioFile: Optional.empty()");
                    return Optional.empty();
                }
            });
        } else {
            Log.e(TAG, "getCurrentLocaleForBuiltInAudioFile method not found");
        }
    }

    private static void hookSynthesizeToFile() {
        try {
            Method synthesizeToFile = TextToSpeech.class.getDeclaredMethod("synthesizeToFile",
                    CharSequence.class, Bundle.class, File.class, String.class);
            XposedBridge.hookMethod(synthesizeToFile, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "synthesizeToFile: " + Arrays.toString(param.args));
                    param.args[0] = "";
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!Objects.equals(param.getResult(), TextToSpeech.SUCCESS)) {
                        Log.w(TAG, "synthesizeToFile: TTS failed, use built-in wav");
                        var file = (File) param.args[2];
                        try (var out = new FileOutputStream(file)) {
                            out.write(wav);
                            param.setResult(TextToSpeech.SUCCESS);
                        } catch (IOException e) {
                            Log.e(TAG, "synthesizeToFile: cannot write " + file, e);
                        }
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "synthesizeToFile method not found", e);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.google.android.dialer".equals(lpparam.packageName)) return;
        new Thread(() -> {
            try (var dex = new DexHelper(lpparam.classLoader)) {
                hookCanRecordCall(dex);
                hookWithinCrosbyGeoFence(dex);
                hookGetCurrentLocaleForBuiltInAudioFile(dex);
                hookSynthesizeToFile();
                Log.d(TAG, "hook done");
            }
        }).start();
        Log.d(TAG, "handleLoadPackage done");
    }
}
