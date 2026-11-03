# Cómo probarlo (spec 037)

## Backend
`TZ=UTC ./mvnw -B clean verify` (por `pesado.sh` si hay un E2E corriendo). Pruebas nuevas: `PoliticaKilometrosAutomaticaTest`, `MedicionDiariaTest`, `MedicionAutomaticaServiceTest`, `MetaDeMedicionAdminServiceTest`, `MedicionAutomaticaIT`.

## App (necesita un build nativo; Expo Go y el APK publicado no sirven)
1. Perfil de EAS preparado: `debug-pasos` (APK de depuración que carga el JS desde Metro, igual que `expo run:android`, **sin** `expo-dev-client`):
   `eas build --platform android --profile debug-pasos`
2. Instalarlo en un emulador o teléfono con **Android 14 o más** (en el emulador: imagen «Google APIs» API 34+, que trae Health Connect como módulo del sistema).
3. Metro del worktree + backend local; `adb reverse tcp:8081 tcp:8081` y `tcp:8080 tcp:8080`.

## Sembrar pasos en el emulador (Health Connect Toolbox)
1. Instalar **Health Connect Toolbox** (APK de Google: `developer.android.com/health-and-fitness/health-connect/test/health-connect-toolbox`).
2. En la Toolbox: *Insert records* → **Steps** (`count` = 7120, de 06:00 a 17:00 de hoy) y **Distance** (5400 m, mismo tramo). Método de registro «automático» (no «manual»).
3. Para el caso **«manual no cuenta»**: otra inserción de Steps con método **manual** desde la Toolbox: la app no debe sumarla.
4. En Training → «Kilómetros diarios» → «Cuenta tus pasos solo» → «Conectar» → Permitir. Deslizar para refrescar.
5. Con la meta en 5 km, «Registrar» aparece al pasar de 5 km. Bajar la meta o los pasos prueba la barra.
6. Maestro (escritos, sin correr): `.maestro/pasos-y-km/`.
