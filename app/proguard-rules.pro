# Reglas Proguard básicas para mantener modelos y serialización JSON
-keepclassmembers class * {
    @org.json.* <methods>;
}

-keep class com.alarma.whatsapp.AlarmaModel { *; }
