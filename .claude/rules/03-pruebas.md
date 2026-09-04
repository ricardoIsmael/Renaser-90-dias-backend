# Pruebas

## La regla que no se negocia

**Toda tarea que toque código termina con `./mvnw clean test` ejecutado y en verde.** No se reporta
nada como terminado sin haber corrido las pruebas.

- Si una prueba falla, **se reporta el fallo con su salida**. Nunca se declara terminado algo que no
  pasó, ni se omite mencionar un test roto.
- Si algo quedó sin probar (faltan credenciales, Docker, un dato), **se dice explícitamente qué
  quedó sin verificar y por qué**.
- `JAVA_HOME` debe apuntar al JDK 25 (`C:\Program Files\Java\jdk-25.0.2`). Si Maven dice
  `release version 25 not supported`, es eso — no el código.

## Qué se prueba y dónde

| Tipo | Cuándo es obligatorio | Cómo |
|---|---|---|
| Unitaria de dominio | Toda regla de negocio nueva | Sin Spring, sin base de datos, `new Agregado(...)` a secas |
| Integración | Todo adaptador nuevo (persistencia, web, IA) | Testcontainers con Postgres real |
| `ArchitectureTest` | Siempre | Debe pasar. Si una regla estorba, **se discute la regla** — no se borra el test ni se le agrega una excepción sin documentarla |

## Pruebas de seguridad, para todo endpoint nuevo

- **Autorización negativa**: un rol sin permiso recibe **403**, y un usuario `SUSPENDED` recibe 403
  aunque su token sea válido.
- **El rol no se puede inyectar**: mandar `role` en el body de un alta pública no lo cambia.
- `EndpointAuthorizationDeclarationTest` falla si un endpoint no declara `@RequiresPermission` ni
  `@PublicEndpoint`.

## Escribir el test que hubiera atrapado el bug

Cuando se arregla un bug, el test nuevo tiene que **fallar contra el código viejo**. Si pasa con y
sin el arreglo, no es un test de regresión — es decoración.

En particular, revisar si el bug se escondía en el **fixture**: el bug del reloj (E-91) pasó CI
durante días porque todos los tests fijaban el reloj a las 10:00 UTC, una hora que cae en el mismo
día calendario en Lima. El código estaba mal; el fixture lo tapaba.

## Fixtures

Un fixture tiene que ser **internamente coherente**. Una fila con `dia_programa = 10` y
`fecha_inicio = hoy − 10` es contradictoria (son 11 días transcurridos): en cuanto el modelo pase de
incremental a derivado, ese test miente sobre lo que verifica.
