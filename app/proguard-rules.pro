# API responses are parsed into kotlinx.serialization JsonElements and mapped by hand, and the
# on-disk cache uses @Serializable classes whose generated serializers ship their own keep rules.
# Library consumer rules (AndroidX, Compose, OkHttp, Coil, kotlinx.serialization) apply automatically.
-dontwarn org.slf4j.**
