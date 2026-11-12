# Plan — spec 052

Solo app (`Renaser-90-dias-frontend-`), sin tab de los cinco: es el ingreso. Sin backend ni migraciones.

- `src/features/auth/utils/registroPorPasos.ts`: pasos, validación por paso, fuerza de la contraseña (lógica pura, con pruebas).
- `src/features/auth/components/`: `RegistroPorPasos` (un paso por pantalla), `EncabezadoDePaso` (volver, avance, «N de M»),
  `ListoDelRegistro`; hook `useTecladoAbierto`.
- `src/screens/LoginScreen.tsx`: sigue siendo la raíz del flujo (guarda los datos y el paso); el login y la recuperación no cambian.
  El código del alta pasa a fondo liso con flecha de volver; `solicitud_enviada` pasa a `ListoDelRegistro`.
- Teclado: `KeyboardAvoidingView` (padding) + botón fuera del scroll.
- Movimiento: solo la entrada del paso (`EntradaEscalonada`) y la sacudida del error, las que ya usa el ingreso.
- Pruebas Jest con marcas `// spec 052 FR-NNN`; Maestro `.maestro/registro-por-pasos/`.
