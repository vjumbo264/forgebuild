package com.ryn.keyflaw.effects.loader;

import defpackage.VvZjG;
import defpackage.nFcAr;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* loaded from: classes2.dex */
public class SuiteRegistrar {
    private static List<String> sCatalogCategoryList;
    private static ArrayList<Object> sCatalogEffectsList;
    private static Map<Object, Object> sCatalogEffectsMap;
    private static final Map<String, List<String>> sRegisteredEffects = new HashMap();
    private static final Map<String, SuiteEffect> sEffectMetaMap = new HashMap();

    public static float getExpandBounds(String str) {
        Map<String, SuiteEffect> map;
        SuiteEffect suiteEffect;
        if (str == null || (map = sEffectMetaMap) == null || (suiteEffect = map.get(str)) == null) {
            return 0.0f;
        }
        return suiteEffect.expandBounds;
    }

    public static boolean layerHasNativeMotionBlur(Object obj) {
        if (obj == null) {
            return false;
        }
        try {
            if (obj instanceof nFcAr) {
                obj = ((nFcAr) obj).rCUlHj;
            }
            if (obj != null && (obj instanceof Iterable)) {
                for (Object obj2 : (Iterable) obj) {
                    if (obj2 != null && (obj2 instanceof VvZjG)) {
                        VvZjG vvZjG = (VvZjG) obj2;
                        if (vvZjG.iNjVD7Jf && (usesNativeMotionBlur(vvZjG.aTKOCHw4M) || usesNativeMotionBlur(vvZjG.OqJj6zTAH))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Throwable th) {
            return false;
        }
    }

    public static boolean usesNativeMotionBlur(String str) {
        Map<String, SuiteEffect> map;
        SuiteEffect suiteEffect;
        if (str == null || (map = sEffectMetaMap) == null || (suiteEffect = map.get(str)) == null) {
            return false;
        }
        return suiteEffect.usesNativeMotionBlur;
    }

    public static synchronized void onCatalogReady(ArrayList<Object> arrayList, Map<Object, Object> map, List<String> list) {
        sCatalogEffectsList = arrayList;
        sCatalogEffectsMap = map;
        if (list != null && !(list instanceof ArrayList)) {
            sCatalogCategoryList = new ArrayList(list);
            SuiteModelFactory.setAz0CategoryList(sCatalogCategoryList);
        } else {
            sCatalogCategoryList = list;
        }
        SuiteManager.onCatalogReady();
    }

    /* JADX WARN: Removed duplicated region for block: B:11:0x0011  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static synchronized boolean isCatalogReady() {
        /*
            java.lang.Class<com.ryn.keyflaw.effects.loader.SuiteRegistrar> r0 = com.ryn.keyflaw.effects.loader.SuiteRegistrar.class
            monitor-enter(r0)
            java.util.ArrayList<java.lang.Object> r1 = com.ryn.keyflaw.effects.loader.SuiteRegistrar.sCatalogEffectsList     // Catch: java.lang.Throwable -> L14
            if (r1 == 0) goto L11
            java.util.Map<java.lang.Object, java.lang.Object> r1 = com.ryn.keyflaw.effects.loader.SuiteRegistrar.sCatalogEffectsMap     // Catch: java.lang.Throwable -> L14
            if (r1 == 0) goto L11
            java.util.List<java.lang.String> r1 = com.ryn.keyflaw.effects.loader.SuiteRegistrar.sCatalogCategoryList     // Catch: java.lang.Throwable -> L14
            if (r1 == 0) goto L11
            r1 = 1
            goto L12
        L11:
            r1 = 0
        L12:
            monitor-exit(r0)
            return r1
        L14:
            r1 = move-exception
            monitor-exit(r0)     // Catch: java.lang.Throwable -> L14
            throw r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.ryn.keyflaw.effects.loader.SuiteRegistrar.isCatalogReady():boolean");
    }

    public static synchronized void registerSuite(InstalledSuite installedSuite) {
        List listSingletonList;
        if (installedSuite != null) {
            if (installedSuite.effects != null && isCatalogReady()) {
                String str = installedSuite.name;
                if ("Keyflaw Ultimate 520 Pro FX Suite".equals(str) || "keyflaw_ultimate_520_pro_suite".equals(installedSuite.id)) {
                    str = "520 Pro FX";
                    installedSuite.name = "520 Pro FX";
                }
                if (!sCatalogCategoryList.contains(str)) {
                    sCatalogCategoryList.add(str);
                }
                ArrayList arrayList = new ArrayList();
                for (SuiteEffect suiteEffect : installedSuite.effects) {
                    String str2 = "suite_" + installedSuite.id + "_" + suiteEffect.id;
                    ArrayList arrayList2 = new ArrayList();
                    if (suiteEffect.parameters != null) {
                        Iterator<SuiteParameter> it = suiteEffect.parameters.iterator();
                        while (it.hasNext()) {
                            Object objCreateParam = SuiteModelFactory.createParam(it.next());
                            if (objCreateParam != null) {
                                arrayList2.add(objCreateParam);
                            }
                        }
                    }
                    List<String> list = suiteEffect.passes;
                    if (list == null || list.isEmpty()) {
                        listSingletonList = Collections.singletonList(SuiteModelFactory.createShader(suiteEffect.glslSource));
                    } else {
                        ArrayList arrayList3 = new ArrayList();
                        Iterator<String> it2 = list.iterator();
                        while (it2.hasNext()) {
                            arrayList3.add(SuiteModelFactory.createShader(it2.next()));
                        }
                        listSingletonList = arrayList3;
                    }
                    String str3 = suiteEffect.category;
                    if (str3 == null || str3.isEmpty() || str3.equals("Custom") || str3.equals("Warna") || str3.equals("Distorsi") || str3.equals("Stilisasi") || str3.equals("Blur") || str3.equals("Simulasi") || str3.equals("Generator") || str3.equals("Gerak")) {
                        str3 = str;
                    }
                    if (!sCatalogCategoryList.contains(str3)) {
                        sCatalogCategoryList.add(str3);
                    }
                    Object objCreateEffect = SuiteModelFactory.createEffect(str2, suiteEffect.name, str3, arrayList2, listSingletonList);
                    if (objCreateEffect != null) {
                        sCatalogEffectsList.add(objCreateEffect);
                        sCatalogEffectsMap.put(str2, objCreateEffect);
                        Map<String, SuiteEffect> map = sEffectMetaMap;
                        if (map != null) {
                            map.put(suiteEffect.id, suiteEffect);
                            map.put(str2, suiteEffect);
                        }
                        arrayList.add(str2);
                    }
                }
                sRegisteredEffects.put(installedSuite.id, arrayList);
            }
        }
    }

    public static synchronized void unregisterSuite(InstalledSuite installedSuite) {
        if (installedSuite != null) {
            if (isCatalogReady()) {
                List<String> listRemove = sRegisteredEffects.remove(installedSuite.id);
                if (listRemove != null) {
                    Iterator<String> it = listRemove.iterator();
                    while (it.hasNext()) {
                        sCatalogEffectsMap.remove(it.next());
                    }
                    Iterator<Object> it2 = sCatalogEffectsList.iterator();
                    while (it2.hasNext()) {
                        Object next = it2.next();
                        if (next != null) {
                            Iterator<String> it3 = listRemove.iterator();
                            while (true) {
                                if (it3.hasNext()) {
                                    if (next.toString().contains(it3.next())) {
                                        it2.remove();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (sCatalogCategoryList != null) {
                    List<String> list = sCatalogCategoryList;
                    list.remove(installedSuite.name);
                    list.remove("520 Pro FX");
                    list.remove("Keyflaw Ultimate 520 Pro FX Suite");
                }
            }
        }
    }
}
