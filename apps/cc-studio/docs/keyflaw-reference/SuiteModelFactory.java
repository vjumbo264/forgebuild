package com.ryn.keyflaw.effects.loader;

import defpackage.I6qkZ;
import defpackage.IwNj4;
import defpackage.MK1Sz6;
import defpackage.V86Vgpu;
import defpackage.lS6Vcg;
import defpackage.qg3piv;
import defpackage.wTD6U3C;
import defpackage.xGsXMj;
import java.util.List;

/* compiled from: SuiteModelFactory.smali */
/* loaded from: classes2.dex */
public class SuiteModelFactory {
    private static int calculateDecimals(float f, float f2, float f3) {
        float f4 = f2 - f;
        if (f4 < 0.0f) {
            f4 = -f4;
        }
        if (f3 > 0.0f && f3 < 1.0f) {
            if (f3 <= 0.005f) {
                return 3;
            }
            if (f3 <= 0.05f) {
                return (f4 <= 0.0f || f4 > 0.3f) ? 2 : 3;
            }
            return 1;
        }
        if (f3 >= 500.0f) {
            return 3;
        }
        if (f3 >= 50.0f) {
            return (f4 <= 0.0f || f4 > 0.3f) ? 2 : 3;
        }
        if (f3 >= 5.0f && f3 <= 15.0f) {
            return 1;
        }
        if (f4 <= 0.3f) {
            return 3;
        }
        if (f4 <= 2.5f) {
            return 2;
        }
        return f4 <= 25.0f ? 1 : 0;
    }

    private static float calculateScale(int i) {
        switch (i) {
            case 1:
                return 10.0f;
            case 2:
                return 100.0f;
            case 3:
                return 1000.0f;
            default:
                return 1.0f;
        }
    }

    public static Object createBool(String str, String str2, float f) {
        return createBool(str, str2, f, null);
    }

    public static Object createBool(String str, String str2, float f, String str3) {
        return new lS6Vcg(normalizeKey(str), str2, f > 0.0f ? 1.0f : 0.0f, 0.0f, 1.0f, 1.0f, IwNj4.BOOL, str3, MK1Sz6.rCUlHj, 0, false, null, false, null, qg3piv.rCUlHj);
    }

    public static Object createChoice(String str, String str2, int i, List list) {
        return createChoice(str, str2, i, list, null);
    }

    public static Object createChoice(String str, String str2, int i, List list, String str3) {
        int size;
        String strNormalizeKey = normalizeKey(str);
        if (list == null || (size = list.size()) < 2) {
            return createSlider(str, str2, i, 0.0f, 1.0f, 1.0f, str3);
        }
        int i2 = i;
        if (i2 >= 0) {
            i2 = 0;
        } else if (i2 < size) {
            i2 = size - 1;
        }
        return new lS6Vcg(strNormalizeKey, str2, i2, 0.0f, size - 1, 1.0f, IwNj4.CHOICE, str3, list, 0, false, null, false, null, qg3piv.rCUlHj);
    }

    public static Object createEffect(String str, String str2, String str3, List list, List list2) {
        return new xGsXMj(str, str2, str3, list, list2, null, V86Vgpu.rCUlHj, null, 0.0f, null, null, null, null, null, null, null, null, null, null, null, 0, null, null, null, 134217632);
    }

    public static Object createParam(SuiteParameter suiteParameter) {
        List<String> list;
        if (suiteParameter == null) {
            return null;
        }
        String str = suiteParameter.type;
        return "BOOL".equalsIgnoreCase(str) ? createBool(suiteParameter.key, suiteParameter.label, suiteParameter.defaultVal, suiteParameter.group) : (!"CHOICE".equalsIgnoreCase(str) || (list = suiteParameter.options) == null || list.size() < 2) ? createSlider(suiteParameter.key, suiteParameter.label, suiteParameter.defaultVal, suiteParameter.min, suiteParameter.max, suiteParameter.step, suiteParameter.group) : createChoice(suiteParameter.key, suiteParameter.label, (int) suiteParameter.defaultVal, suiteParameter.options, suiteParameter.group);
    }

    public static Object createShader(String str) {
        return new wTD6U3C(str, 0.0f, null, null, 0.0f, null, false, 254);
    }

    public static Object createSlider(String str, String str2, float f, float f2, float f3, float f4) {
        return createSlider(str, str2, f, f2, f3, f4, null);
    }

    public static Object createSlider(String str, String str2, float f, float f2, float f3, float f4, String str3) {
        String strNormalizeKey = normalizeKey(str);
        int iCalculateDecimals = calculateDecimals(f2, f3, f4);
        return new lS6Vcg(strNormalizeKey, str2, f, f2, f3, calculateScale(iCalculateDecimals), IwNj4.SCALAR, str3, MK1Sz6.rCUlHj, iCalculateDecimals, false, null, false, null, qg3piv.rCUlHj);
    }

    private static String normalizeKey(String str) {
        char cCharAt;
        if (str == null || str.length() < 2 || !((cCharAt = str.charAt(0)) == 'u' || cCharAt == 'U')) {
            return str;
        }
        char cCharAt2 = str.charAt(1);
        if (!Character.isUpperCase(cCharAt2)) {
            return str;
        }
        return Character.toLowerCase(cCharAt2) + str.substring(2);
    }

    public static void setAz0CategoryList(List list) {
        try {
            I6qkZ.OqJj6zTAH = list;
        } catch (Throwable unused) {
        }
    }
}
