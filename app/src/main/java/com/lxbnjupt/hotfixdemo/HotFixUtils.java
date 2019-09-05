package com.lxbnjupt.hotfixdemo;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by liuxiaobo on 2018/10/31.
 */

public class HotFixUtils {

    private static final String TAG = "lxbnjupt";
    private static final String NAME_BASE_DEX_CLASS_LOADER = "dalvik.system.BaseDexClassLoader";
    private static final String FIELD_DEX_ELEMENTS = "dexElements";
    private static final String FIELD_PATH_LIST = "pathList";
    private static final String DEX_SUFFIX = ".dex";
    private static final String APK_SUFFIX = ".apk";
    private static final String JAR_SUFFIX = ".jar";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String DEX_DIR = "patch";
    private static final String OPTIMIZE_DEX_DIR = "odex";

    public void doHotFix(Context context) throws IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
        if (context == null) {
            return;
        }
        // 补丁存放目录为 /storage/emulated/0/Android/data/com.lxbnjupt.hotfixdemo/files/patch
        File dexFile = context.getExternalFilesDir(DEX_DIR);
        if (dexFile == null || !dexFile.exists()) {
            Log.e(TAG,"热更新补丁目录不存在");
            return;
        }
        // 获取应用原本的 dex 文件
        File odexFile = context.getDir(OPTIMIZE_DEX_DIR, Context.MODE_PRIVATE);
        if (!odexFile.exists()) {
            odexFile.mkdir();
        }
        File[] listFiles = dexFile.listFiles();
        if (listFiles == null || listFiles.length == 0) {
            return;
        }
        String dexPath = getPatchDexPath(listFiles);
        String odexPath = odexFile.getAbsolutePath();
        // 获取PathClassLoader
        PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
        // 构建DexClassLoader，用于加载补丁dex
        DexClassLoader dexClassLoader = new DexClassLoader(dexPath, odexPath, null, pathClassLoader);
        // 获取PathClassLoader的Element数组
        Object pathElements = getDexElements(pathClassLoader);
        // 获取构建的DexClassLoader的Element数组
        Object dexElements = getDexElements(dexClassLoader);
        // 合并Element数组
        Object combineElementArray = combineElementArray(pathElements, dexElements);
        // 通过反射，将合并后的Element数组赋值给PathClassLoader中pathList里面的dexElements变量
        setDexElements(pathClassLoader, combineElementArray);
    }

    /**
     * 获取补丁dex文件路径集合
     * @param listFiles
     * @return
     */
    private String getPatchDexPath(File[] listFiles) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listFiles.length; i++) {
            // 遍历查找文件中.dex .jar .apk .zip结尾的文件
            File file = listFiles[i];
            if (file.getName().endsWith(DEX_SUFFIX)
                    || file.getName().endsWith(APK_SUFFIX)
                    || file.getName().endsWith(JAR_SUFFIX)
                    || file.getName().endsWith(ZIP_SUFFIX)) {
                if (i != 0 && i != (listFiles.length - 1)) {
                    // 多个dex路径 添加默认的:分隔符
                    sb.append(File.pathSeparator);
                }
                sb.append(file.getAbsolutePath());
            }
        }
        return sb.toString();
    }

    /**
     * 合并Element数组，将补丁dex放在最前面
     * @param pathElements PathClassLoader中pathList里面的Element数组
     * @param dexElements 补丁dex数组
     * @return 合并之后的Element数组
     */
    private Object combineElementArray(Object pathElements, Object dexElements) {
        Class<?> componentType = pathElements.getClass().getComponentType();
        int i = Array.getLength(pathElements);// 原dex数组长度
        int j = Array.getLength(dexElements);// 补丁dex数组长度
        int k = i + j;// 总数组长度（原dex数组长度 + 补丁dex数组长度)
        Object result = Array.newInstance(componentType, k);// 创建一个类型为componentType，长度为k的新数组
        System.arraycopy(dexElements, 0, result, 0, j);// 补丁dex数组在前
        System.arraycopy(pathElements, 0, result, j, i);// 原dex数组在后
        return result;
    }

    /**
     * 获取Element数组
     * @param classLoader 类加载器
     * @return
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private Object getDexElements(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        // 获取BaseDexClassLoader，是PathClassLoader以及DexClassLoader的父类
        Class<?> BaseDexClassLoaderClazz = Class.forName(NAME_BASE_DEX_CLASS_LOADER);
        // 获取pathList字段，并设置为可以访问
        Field pathListField = BaseDexClassLoaderClazz.getDeclaredField(FIELD_PATH_LIST);
        pathListField.setAccessible(true);
        // 获取DexPathList对象
        Object dexPathList = pathListField.get(classLoader);
        // 获取dexElements字段，并设置为可以访问
        Field dexElementsField = dexPathList.getClass().getDeclaredField(FIELD_DEX_ELEMENTS);
        dexElementsField.setAccessible(true);
        // 获取Element数组，并返回
        return dexElementsField.get(dexPathList);
    }

    /**
     * 通过反射，将合并后的Element数组赋值给PathClassLoader中pathList里面的dexElements变量
     * @param classLoader PathClassLoader类加载器
     * @param value 合并后的Element数组
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private void setDexElements(ClassLoader classLoader, Object value) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        // 获取BaseDexClassLoader，是PathClassLoader以及DexClassLoader的父类
        Class<?> BaseDexClassLoaderClazz = Class.forName(NAME_BASE_DEX_CLASS_LOADER);
        // 获取pathList字段，并设置为可以访问
        Field pathListField = BaseDexClassLoaderClazz.getDeclaredField(FIELD_PATH_LIST);
        pathListField.setAccessible(true);
        // 获取DexPathList对象
        Object dexPathList = pathListField.get(classLoader);
        // 获取dexElements字段，并设置为可以访问
        Field dexElementsField = dexPathList.getClass().getDeclaredField(FIELD_DEX_ELEMENTS);
        dexElementsField.setAccessible(true);
        // 将合并后的Element数组赋值给dexElements变量
        dexElementsField.set(dexPathList, value);
    }
}
