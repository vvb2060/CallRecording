package io.github.vvb2060.callrecording.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
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

    @SuppressWarnings({"SoonBlockedPrivateApi", "JavaReflectionMemberAccess"})
    private static void hookDispatchOnInit() {
        try {
            Method dispatchOnInit = TextToSpeech.class.getDeclaredMethod("dispatchOnInit", int.class);
            XposedBridge.hookMethod(dispatchOnInit, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "dispatchOnInit: " + Arrays.toString(param.args));
                    if (!Objects.equals(param.args[0], TextToSpeech.SUCCESS)) {
                        param.args[0] = TextToSpeech.SUCCESS;
                        Log.w(TAG, "TTS failed, ignore");
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "dispatchOnInit method not found", e);
        }
    }

    private static void hookIsLanguageAvailable() {
        try {
            Method isLanguageAvailable = TextToSpeech.class.getDeclaredMethod("isLanguageAvailable", Locale.class);
            XposedBridge.hookMethod(isLanguageAvailable, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "isLanguageAvailable: " + Arrays.toString(param.args) +
                            " -> " + param.getResult());
                    if ((int) param.getResult() < TextToSpeech.LANG_AVAILABLE) {
                        param.setResult(TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
                        Log.w(TAG, "TTS language not available, ignore");
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "isLanguageAvailable method not found", e);
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
                        try {
                            var field = param.thisObject.getClass().getDeclaredField("mUtteranceProgressListener");
                            field.setAccessible(true);
                            var listener = (UtteranceProgressListener) field.get(param.thisObject);
                            if (listener == null) return;
                            var onDone = UtteranceProgressListener.class.getDeclaredMethod("onDone", String.class);
                            onDone.invoke(listener, (String) param.args[3]);
                        } catch (ReflectiveOperationException e) {
                            Log.e(TAG, "synthesizeToFile: cannot invoke onDone", e);
                        }
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "synthesizeToFile method not found", e);
        }
    }

    private static void hookActivityOnResume() {
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    var context = ((Activity) param.thisObject).getApplicationContext();
                    checkUnsupportedVersion(context);
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "onResume method not found", e);
        }
    }

    private static void checkUnsupportedVersion(Context context) {
        try {
            var pm = context.getPackageManager();
            var info = pm.getPackageInfo(context.getPackageName(), 0);
            var versionName = info.versionName;
            if (versionName != null && versionName.endsWith("downloadable")) {
                Toast.makeText(context, "CallRecording: Unsupported version, "+
                        "please use full version.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Unsupported version detected: " + versionName);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Cannot get package info", e);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.google.android.dialer".equals(lpparam.packageName)) return;
        new Thread(() -> {
            try (var dex = new DexHelper(lpparam.classLoader)) {
                hookCanRecordCall(dex);
                hookWithinCrosbyGeoFence(dex);
            }
            hookSynthesizeToFile();
            hookDispatchOnInit();
            hookIsLanguageAvailable();
            hookActivityOnResume();
            Log.d(TAG, "hook done");
        }).start();
        Log.d(TAG, "handleLoadPackage done");
    }
}
