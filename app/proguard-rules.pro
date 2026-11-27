# R8 / ProGuard rules for FoodTracker.
#
# Most libraries (Room, Compose, CameraX, WorkManager, DataStore, Glance, and
# kotlinx.serialization) ship their own consumer rules inside their artifacts,
# so we don't repeat them here. We only declare rules for:
#   - ML Kit barcode scanning (the play-services flavour ships NO consumer
#     rules; classes are loaded reflectively from the AndroidManifest
#     com.google.mlkit.vision.DEPENDENCIES meta-data and stripping them
#     crashes BarcodeScanning.getClient() on release builds)
#   - stack-trace ergonomics — the default proguard-android-optimize.txt does
#     NOT keep SourceFile/LineNumberTable, so symbolicated crashes would lose
#     line numbers without this.

# Keep file/line info in crash reports — symbolicate via mapping.txt produced
# at build/outputs/mapping/release/.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ───────────────────── ML Kit barcode scanning ─────────────────────
# The play-services flavour ships no consumer rules. At init ML Kit reflectively
# instantiates the registrar classes named in the MlKitComponentDiscoveryService
# <meta-data> of the merged manifest (BarcodeRegistrar, VisionCommonRegistrar,
# CommonComponentRegistrar). R8 does NOT auto-keep classes named in meta-data
# values, so stripping/renaming them crashes BarcodeScanning.getClient() on
# release builds. We keep them by their common ancestor (ComponentRegistrar)
# rather than the whole com.google.mlkit package — this is a handful of classes,
# so R8 can still shrink/optimise the rest of ML Kit (and it clears the
# "overly broad keep rule" warning). Everything else is reached normally through
# the kept registrars, or kept via @UsedByNative by ML Kit's own consumer rules.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
