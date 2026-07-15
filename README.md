# Game Turbo (Shizuku)

Proyecto base de Android Studio (Kotlin) para una app "Game Turbo" que usa
la API de Shizuku para: cerrar procesos en segundo plano, intentar activar
modo de alto rendimiento de CPU, y bloquear notificaciones/llamadas.

## Requisitos previos

1. **Instalar Shizuku** en el dispositivo de prueba (Play Store o F-Droid).
2. **Activar el servicio Shizuku**, típicamente vía ADB en PC:
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```
   (el método exacto depende de la versión de Shizuku; revisa su app, tiene
   un botón "Iniciar" con instrucciones para tu dispositivo).

## Cómo abrir el proyecto (con PC)

1. Abre Android Studio → "Open" → selecciona la carpeta `GameTurbo`.
2. Deja que Gradle sincronice (bajará las dependencias de Shizuku).
3. Conecta tu dispositivo con USB debugging activado y dale a Run.

## Cómo compilar SIN PC (solo celular, con Termux + GitHub)

Este proyecto incluye `.github/workflows/build.yml`, que hace que GitHub
compile el APK por ti en la nube. Pasos:

1. Crea una cuenta gratis en https://github.com (desde el navegador del cel).
2. Crea un repositorio nuevo, público o privado, sin README (por ejemplo
   llamado `GameTurbo`).
3. Instala **Termux** (desde F-Droid, no la versión vieja de Play Store).
4. En Termux:
   ```
   pkg update && pkg install git termux-api -y
   termux-setup-storage
   cd /storage/emulated/0/GameTurbo
   git init
   git add .
   git commit -m "Primer commit"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/GameTurbo.git
   git push -u origin main
   ```
   Cuando pida usuario/contraseña: el usuario es tu usuario de GitHub, y
   como contraseña necesitas un **Personal Access Token** (no tu contraseña
   normal). Se genera en GitHub: Settings → Developer settings →
   Personal access tokens → Generate new token (marca el permiso "repo").
5. En cuanto hagas `git push`, GitHub compilará automáticamente. Ve a la
   pestaña **Actions** de tu repositorio en GitHub y espera a que termine
   (unos 3-5 minutos, verás una marca verde ✅).
6. Entra a esa ejecución terminada → sección **Artifacts** al final de la
   página → descarga `GameTurbo-debug-apk` (es un .zip con el .apk dentro).
7. Descomprime ese zip en el celular y toca el .apk para instalarlo
   (activa "Instalar apps de origen desconocido" si te lo pide).

## Sobre el "modo alto rendimiento" de CPU/GPU

La función `PerformanceBooster.setHighPerformanceMode()` escribe en:
```
/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
```
Esta ruta es genérica y **puede no funcionar en tu dispositivo**. Cada
fabricante (Qualcomm, MediaTek, Samsung Exynos...) expone rutas distintas,
y muchos OEMs las bloquean incluso con permisos de shell. Para encontrar
la ruta correcta en tu equipo:

```
adb shell
cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors
```

Si el archivo no existe o da "Permission denied" incluso vía Shizuku,
ese dispositivo probablemente no permite este ajuste sin root completo.

## Estructura

- `ShizukuManager.kt` — comprobación de servicio y permisos de Shizuku.
- `PerformanceBooster.kt` — mata procesos en segundo plano y ajusta CPU.
- `DoNotDisturbController.kt` — activa/desactiva modo No Molestar.
- `MainActivity.kt` — UI mínima para probar todo junto.

## Próximos pasos sugeridos

- Añadir una lista blanca configurable de apps que no se deben cerrar.
- Guardar el estado del modo turbo (activo/inactivo) con SharedPreferences.
- Detectar automáticamente cuándo se abre un juego (usando UsageStatsManager)
  para activar/desactivar el turbo sin intervención manual.
