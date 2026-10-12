# Quickstart: probar hábitos globales por rol

## Backend

```sh
cd ~/Documentos/Renaser/Renaser-90-dias-backend
./mvnw clean verify          # o ./scripts/test-cloud.sh con token; leer las dos líneas "Tests run:"
```

Escenario manual (backend local en `:8080`, sesión de un ADMIN en `$TOKEN`):

1. `GET /api/v1/admin/habits/roles` → los cinco roles con nombre.
2. `POST /api/v1/admin/habits` con `targetRoles: ["MENTOR"]` → 201. Un mentor con programa activo
   que aún no generó su día lo recibe hoy (audiencia inicial); un aprendiz no.
3. `POST /api/v1/admin/habits/{id}` con `targetRoles: []` → el aprendiz lo recibe **mañana**, no hoy.
4. `GET /api/v1/admin/habits?role=TRAINEE` → incluye los "para todos" y no el de mentores.
5. `DELETE` de un hábito con registros → 409 con el motivo.
6. Cambiar el rol de una persona (Equipo y roles) → hoy conserva lo suyo; mañana recibe los del
   rol nuevo.

## App

```sh
cd ~/Documentos/Renaser/Renaser-90-dias-frontend-
node node_modules/typescript/bin/tsc --noEmit
node node_modules/jest/bin/jest.js --silent
npx expo start --web --port 8081   # en web, puerto 8081 por CORS
```

Con una cuenta ADMIN: Yo/Hoy → Administración → Más opciones → «Hábitos del programa». Crear uno
con el interruptor apagado y solo «Mentor», filtrar por Mentor, editar, desactivar, reactivar,
intentar eliminar uno con historial (debe ofrecer desactivar). Con una cuenta MENTOR la entrada no
aparece. Probar en claro y oscuro, a 360 px y con el gesto atrás.
