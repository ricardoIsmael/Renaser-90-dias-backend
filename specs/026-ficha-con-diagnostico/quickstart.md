# Quickstart de validación (spec 026)

## Backend
```bash
flock ~/.config/renaser-e2e/locks/maquina.lock ./mvnw -B -o test -Dtest='*Diagnostico*Test,*Ficha*Test,PlantillaDeImportacionTest,RocaMaestraServiceTest,ArchitectureTest,EndpointAuthorizationDeclarationTest,FuncionalidadDeclaradaTest'
TZ=UTC flock ~/.config/renaser-e2e/locks/maquina.lock ./mvnw -B clean verify
```
## Datos de prueba (base clonada, nunca producción)
1 aprendiz de cada formación; 1 mentor; 1 líder; 1 admin. Con el admin: abrir `GET /admin/diagnostico`, cambiar el texto de la declaración, `POST /publicar`; asignar `CON_DIAGNOSTICO` a un aprendiz; iniciar sesión con él y recorrer `/diagnostico/mio`.

## App
```bash
node node_modules/typescript/bin/tsc --noEmit
flock ~/.config/renaser-e2e/locks/maquina.lock node node_modules/jest/bin/jest.js
```
## E2E (lo corre el agente del E2E)
`.maestro/ficha-con-diagnostico/*.yaml`: la persona completa el diagnóstico; Administración edita, importa, publica y asigna; Plan con objetivos por confirmar; sin el Mapa en Hoy, Plan ni Yo.
