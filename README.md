# 一步一步手动实现Android热更新

在[Android热更新实现原理浅析](https://www.jianshu.com/p/8dcf750acdfe)一文中，我们简单分析了Android热更新的实现原理，那么赶紧趁热打铁，一步一步手动实现热更新，莱茨狗。所见即所得，先看一下最终要达成的效果。

![热更新效果演示.gif](https://upload-images.jianshu.io/upload_images/5519943-dc0c372006757f34.gif?imageMogr2/auto-orient/strip)
### 一、热更新代码实现
基于之前的分析，我们知道实现热更新可以分为以下几个步骤：
* 通过构造一个DexClassLoader对象来加载我们的热更新dex文件；
* 通过反射获取系统默认的PathClassLoader.pathList.dexElements；
* 将我们的热更新dex与系统默认的Elements数组合并，同时保证热更新dex在系统默认Elements数组之前；
* 将合并完成后的数组设置回PathClassLoader.pathList.dexElements。

### Talk is cheap, show me the code.
```java
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
```
相信代码中的注释已经非常清楚了，这里就不再过多赘述。

不过，有一点需要注意一下，就是不要忘记打开读写手机存储权限：
```java
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

### 二、测试验证
#### 2.1 将java文件编译成class文件
完成修复bug之后，使用Android Studio的Rebuild Project功能将代码进行编译，然后从build目录下找到对应的class文件。

![](https://upload-images.jianshu.io/upload_images/5519943-a2fe4e3048ef452f.jpeg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
#### 2.2 将class文件打包成dex文件
##### step1:
将修复好的class文件复制到其他任意地方，我这边是选择复制到桌面。注意，在复制这个class文件时，需要把它所在的完整包目录一起复制。上图中修复好的class文件是BugTest.class，其复制出来的目录结构如下图所示：

![](https://upload-images.jianshu.io/upload_images/5519943-52c4baf6693ec0a7.jpeg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
##### step2:
通过dx命令生成dex文件
dx命令的使用有2种选择：
* 配置环境变量（添加到classpath），然后命令行窗口（终端）可以在任意位置使用。
* 不配环境变量，直接在build-tools/Android版本目录下使用命令行窗口（终端）使用。
这里直接使用第2种方式，命令如下：
dx --dex --output=输出的dex文件完整路径 (空格) 要打包的完整class文件所在目录

![](https://upload-images.jianshu.io/upload_images/5519943-41d89e2645e04f10.jpeg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

完成之后，我们可以进到桌面文件夹下查看dex文件是否已经生成，我这边是成功生成了patch.dex文件。

![](https://upload-images.jianshu.io/upload_images/5519943-dc3005464bc90702.jpeg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
#### 2.3 将dex文件push至手机
通过adb命令adb push <local> <remote>，可以将dex文件推到手机指定目录。在上面的demo中，我设置的热更新dex文件的存放路径是/storage/emulated/0/Android/data/com.lxbnjupt.hotfixdemo/files/patch

![](https://upload-images.jianshu.io/upload_images/5519943-a27a5f120df14d14.jpeg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
#### 2.4 真机运行测试

**补充：
(1) 因为 CLASS_ISPREVERIFIED 问题，请使用 ART 机型，即 5.0 及以上
(2) 注意 Android 系统隐藏 API 的原因，请使用 9.0 以下的机型**

MainActivity.java
```java
public class MainActivity extends AppCompatActivity {

    private static final String DEX_DIR = "patch";
    private Button btnRun;
    private Button btnRepair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRun = (Button) findViewById(R.id.btn_run);
        btnRepair = (Button) findViewById(R.id.btn_repair);

        init();
        setOnClickListener();
    }

    private void init() {
        // 补丁存放目录为 /storage/emulated/0/Android/data/com.lxbnjupt.hotfixdemo/files/patch
        File patchDir = new File(this.getExternalFilesDir(null), DEX_DIR);
        if (!patchDir.exists()) {
            patchDir.mkdirs();
        }
    }

    private void setOnClickListener() {
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new BugTest().getBug();
            }
        });

        btnRepair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    new HotFixUtils().doHotFix(MainActivity.this);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
```
如文章开始的gif图所示，打开App，点击「运行」按钮，应用程序会因为NullPointerException直接crash。再次打开App，先点击「修复」按钮，再点击「运行」按钮，此时应用程序不会再出现crash，表示补丁加载成功，bug成功修复。至此，大功告成！
