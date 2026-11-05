package com.ryn.keyflaw.effects.loader;

import com.google.mediapipe.framework.AssetCacheDbHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes2.dex */
public class SuiteParser {
    public static InstalledSuite parseSuiteDir(File file) {
        SuiteEffect effectDir;
        SuiteEffect effectDir2;
        if (file == null || !file.isDirectory()) {
            return null;
        }
        try {
            File file2 = new File(file, "suite.json");
            if (file2.exists() && file2.isFile()) {
                JSONObject jSONObject = new JSONObject(readFileToString(file2));
                InstalledSuite installedSuite = new InstalledSuite();
                installedSuite.dirPath = file.getAbsolutePath();
                installedSuite.id = jSONObject.getString("id");
                String string = jSONObject.getString("name");
                if ("Keyflaw Ultimate 520 Pro FX Suite".equals(string)) {
                    string = "520 Pro FX";
                }
                installedSuite.name = string;
                installedSuite.author = jSONObject.optString("author", "Community Creator");
                installedSuite.version = jSONObject.optString(AssetCacheDbHelper.AssetCacheEntry.COLUMN_NAME_VERSION, "1.0.0");
                installedSuite.description = jSONObject.optString("description", "");
                installedSuite.effects = new ArrayList();
                File file3 = new File(file, "effects");
                if (file3.exists() && file3.isDirectory()) {
                    JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("effects");
                    if (jSONArrayOptJSONArray != null) {
                        int length = jSONArrayOptJSONArray.length();
                        for (int i = 0; i < length; i++) {
                            String strOptString = jSONArrayOptJSONArray.optString(i);
                            if (strOptString != null) {
                                File file4 = new File(file3, strOptString);
                                if (file4.isDirectory() && (effectDir2 = parseEffectDir(file4)) != null) {
                                    installedSuite.effects.add(effectDir2);
                                }
                            }
                        }
                    } else {
                        File[] fileArrListFiles = file3.listFiles();
                        if (fileArrListFiles != null) {
                            for (File file5 : fileArrListFiles) {
                                if (file5.isDirectory() && (effectDir = parseEffectDir(file5)) != null) {
                                    installedSuite.effects.add(effectDir);
                                }
                            }
                        }
                    }
                }
                return installedSuite;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    private static SuiteEffect parseEffectDir(File file) {
        String fileToString;
        try {
            File file2 = new File(file, "effect.json");
            File file3 = new File(file, "shader.glsl");
            if (file2.exists() && file3.exists()) {
                JSONObject jSONObject = new JSONObject(readFileToString(file2));
                SuiteEffect suiteEffect = new SuiteEffect();
                suiteEffect.id = jSONObject.getString("id");
                suiteEffect.name = jSONObject.getString("name");
                suiteEffect.category = jSONObject.optString("category", "Custom");
                suiteEffect.usesNativeMotionBlur = jSONObject.optBoolean("usesNativeMotionBlur", false);
                suiteEffect.expandBounds = (float) jSONObject.optDouble("expandBounds", 0.0d);
                File parentFile = file3.getParentFile();
                suiteEffect.glslSource = readFileToString(file3);
                JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("passes");
                if (jSONArrayOptJSONArray != null && parentFile != null) {
                    for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                        String strOptString = jSONArrayOptJSONArray.optString(i);
                        if (strOptString != null) {
                            File file4 = new File(parentFile, strOptString);
                            if (file4.exists() && (fileToString = readFileToString(file4)) != null) {
                                suiteEffect.passes.add(fileToString);
                            }
                        }
                    }
                }
                suiteEffect.parameters = new ArrayList();
                JSONArray jSONArrayOptJSONArray2 = jSONObject.optJSONArray("parameters");
                if (jSONArrayOptJSONArray2 != null) {
                    for (int i2 = 0; i2 < jSONArrayOptJSONArray2.length(); i2++) {
                        JSONObject jSONObject2 = jSONArrayOptJSONArray2.getJSONObject(i2);
                        SuiteParameter suiteParameter = new SuiteParameter();
                        suiteParameter.key = jSONObject2.getString("key");
                        suiteParameter.label = jSONObject2.optString("label", suiteParameter.key);
                        suiteParameter.defaultVal = (float) jSONObject2.optDouble("default", 0.0d);
                        suiteParameter.min = (float) jSONObject2.optDouble("min", 0.0d);
                        suiteParameter.max = (float) jSONObject2.optDouble("max", 1.0d);
                        suiteParameter.step = (float) jSONObject2.optDouble("step", 1.0d);
                        JSONObject jSONObject3 = jSONArrayOptJSONArray2.getJSONObject(i2);
                        suiteParameter.type = jSONObject3.optString("type", "SCALAR");
                        JSONArray jSONArrayOptJSONArray3 = jSONObject3.optJSONArray("options");
                        if (jSONArrayOptJSONArray3 != null) {
                            ArrayList arrayList = new ArrayList();
                            for (int i3 = 0; i3 < jSONArrayOptJSONArray3.length(); i3++) {
                                arrayList.add(jSONArrayOptJSONArray3.optString(i3));
                            }
                            suiteParameter.options = arrayList;
                            if (arrayList.size() >= 2) {
                                suiteParameter.type = "CHOICE";
                            }
                        }
                        String strOptString2 = jSONObject3.optString("group", null);
                        if (strOptString2 != null) {
                            strOptString2 = strOptString2.trim();
                            if (strOptString2.length() == 0) {
                                strOptString2 = null;
                            }
                        }
                        suiteParameter.group = strOptString2;
                        suiteEffect.parameters.add(suiteParameter);
                    }
                }
                return suiteEffect;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    public static String readFileToString(File file) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = bufferedReader.readLine();
            if (line != null) {
                sb.append(line).append('\n');
            } else {
                bufferedReader.close();
                return sb.toString();
            }
        }
    }
}
