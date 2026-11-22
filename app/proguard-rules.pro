# Regras R8 / ProGuard do CacauApp
-keepattributes Signature,InnerClasses

# TensorFlow Lite (CPU, XNNPACK & GPU Delegate), Task Vision & CameraX
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keep class androidx.camera.** { *; }

# OkHttp / Okio / TLS opcionais
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# Modelos, ViewModel, Rede e Serialização Local
-keep class br.gov.ceplac.cepec.cacauapp.data.** { *; }
-keep class br.gov.ceplac.cepec.cacauapp.viewmodel.** { *; }
-keep class br.gov.ceplac.cepec.cacauapp.network.** { *; }
-keep class br.gov.ceplac.cepec.cacauapp.userinterface.CaixaAuditavel { *; }
-keep class br.gov.ceplac.cepec.cacauapp.userinterface.util.PersistenciaLocalRegistros$* { *; }
