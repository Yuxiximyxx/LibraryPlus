# Xposed 入口与所有 hook 类（通过 assets/xposed_init 反射加载）
-keep class io.github.yuxiximyxx.libraryplus.hook.** { *; }
-keep class io.github.yuxiximyxx.libraryplus.ConfigProvider { *; }
-keep class io.github.yuxiximyxx.libraryplus.LpConfig { *; }
-keep class io.github.yuxiximyxx.libraryplus.Const { *; }
# DexKit 使用 JNI
-keep class org.luckypray.dexkit.** { *; }
-dontwarn de.robv.android.xposed.**
-dontwarn org.luckypray.dexkit.**
