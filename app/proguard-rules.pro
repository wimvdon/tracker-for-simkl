# API responses are parsed into kotlinx.serialization JsonElements and mapped by hand, and the
# on-disk cache uses @Serializable classes whose generated serializers ship their own keep rules.
# Library consumer rules (AndroidX, Compose, OkHttp, Coil, kotlinx.serialization) apply automatically.
-dontwarn org.slf4j.**

# WorkManager (new-episode notifications). Room creates the generated database
# (androidx.work.impl.WorkDatabase_Impl) and WorkManager creates workers and input mergers
# by reflection. R8 full mode removed WorkDatabase_Impl's no-argument constructor in 0.0.4
# (NoSuchMethodException when turning notifications on), so keep these constructors explicitly.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger { public <init>(); }

# Written next to the mapping file so CI can check the rules above are applied.
-printseeds build/outputs/r8-seeds.txt
