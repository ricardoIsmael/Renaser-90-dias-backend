# Quickstart de validación (spec 022)

## Backend
```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem MAVEN_OPTS=-Xmx1500m
./mvnw -o -q test -Dtest='DuracionDelProgramaTest,FasesDelProgramaTest,ParticipacionProgramaFormacionTest,ReglasDeTrasladoTest,AlcanceDeFormacionTest,*Formacion*Test'
./mvnw -o -q test -Dtest='ArchitectureTest,EndpointAuthorizationDeclarationTest,FuncionalidadDeclaradaTest'
./mvnw -o -q verify -Dit.test='AislamientoDeFormacionesIT,MigracionAFormacionesIT' -DskipUTs   # Testcontainers
```
Esperado: sin fallos; el IT de aislamiento muestra que una persona del Diplomado no aparece en ninguna lectura de la Escuela y viceversa (las 6 combinaciones).

## Datos de prueba
Una base clonada (nunca producción): 1 aprendiz por formación, 1 mentor solo de la Escuela, 1 mentora de Escuela + Diplomado, 1 admin.

## App
```bash
node node_modules/typescript/bin/tsc --noEmit
node node_modules/jest/bin/jest.js src/features/formaciones src/features/auth src/features/home/utils
```
## E2E (después, con el emulador)
`.maestro/formaciones/` (lo corre el dueño): aprendiz de la Escuela vs aprendiz del Diplomado; mentora con dos formaciones que cambia la activa; Administración abre «Formaciones».
