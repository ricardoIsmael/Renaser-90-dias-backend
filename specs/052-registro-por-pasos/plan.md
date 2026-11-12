# Plan — spec 052

Solo app (`Renaser-90-dias-frontend-`), sin tab de los cinco: es el ingreso. Sin backend ni migraciones.

- `src/features/auth/utils/registroPorPasos.ts`: pasos, validación por paso, fuerza de la contraseña (lógica pura, con pruebas).
- `src/features/auth/components/`: `RegistroPorPasos` (un paso por pantalla), `EncabezadoDePaso` (volver, avance, «N de M»),
  `ListoDelRegistro`; hook `useTecladoAbierto`.
- `useRegistroConOtp`: `confirmarYRegistrar` se parte en `confirmarCodigo` (token) y `crearSolicitud` (mismas llamadas, sin cambio de contrato).
- `src/screens/LoginScreen.tsx`: sigue siendo la raíz del flujo (guarda los datos y el paso); el login y la recuperación no cambian.
  El paso del código (antes de la contraseña) va con fondo liso y la fila de avance; `solicitud_enviada` pasa a `ListoDelRegistro`.
- Teclado: `KeyboardAvoidingView` (padding) + botón fuera del scroll.
- Movimiento: solo la entrada del paso (`EntradaEscalonada`) y la sacudida del error, las que ya usa el ingreso.
- Pruebas Jest con marcas `// spec 052 FR-NNN`; Maestro `.maestro/registro-por-pasos/`.
