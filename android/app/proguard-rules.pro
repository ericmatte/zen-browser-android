-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**
# GeckoView reads geckoview-config.yaml (Gecko prefs) through SnakeYAML, which
# relies on reflection and package names that R8 would otherwise strip.
-keep class org.mozilla.gecko.util.DebugConfig** { *; }
-keep class org.yaml.snakeyaml.** { *; }
-keeppackagenames org.yaml.snakeyaml.**
-dontwarn org.yaml.snakeyaml.**
