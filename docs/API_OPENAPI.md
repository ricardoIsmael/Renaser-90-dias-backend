# Documentación de la API — Swagger (OpenAPI) y MCP

> D-336 (2026-11-06). Configuración: `shared.web.OpenApiConfig` y `shared.web.McpDeLaApiConfig`;
> propiedades en `application.yaml` (bloque `springdoc`) y `application-prod.yaml`.

## Qué hay

| Ruta | Qué es | Local | Prod |
|---|---|---|---|
| `/swagger-ui.html` | La API navegable: cada ruta, qué recibe, qué devuelve, y «Try it out» | encendida | **apagada** |
| `/v3/api-docs` | El documento OpenAPI 3.1 en JSON (para otro equipo, Postman o generar tipos) | encendida | **apagada** |
| `/v3/api-docs.yaml` | Lo mismo en YAML | encendida | **apagada** |
| `/mcp` | Servidor MCP: la API como herramientas para un agente de IA | encendido | **apagado** |
| `/mcp-ui` | Panel para ver y probar esas herramientas | encendido | **apagado** |

Se documenta **todo** `/api/v1/**` sin anotar nada en los controllers (el 2026-11-06: 431 rutas,
517 operaciones, 171 grupos). Cada operación lleva:

- la etiqueta `<módulo> · <controller>` (por ejemplo `habits · HabitTrack`), que ordena Swagger por módulo;
- la sesión `X-Auth-Token` exigida, salvo las rutas `@PublicEndpoint` (dicen «Pública» y por qué);
- el permiso del rol en la descripción y en la extensión `x-permiso` (de `@RequiresPermission`);
- sin el actor como parámetro: `@ActorAutenticado` sale de la sesión, no lo manda el cliente.

## Cómo probar con sesión en Swagger

1. `POST /api/v1/auth/login` con correo y contraseña de una cuenta **local**.
2. Copiar la cabecera `X-Auth-Token` de la respuesta.
3. Botón **Authorize** → pegarla en `sesion`. Queda guardada mientras dure la pestaña.

Nunca contra producción.

## Pasar el contrato a otro equipo

Con el backend local corriendo: `curl -s http://localhost:8080/v3/api-docs > renaser-api.json`.
Ese archivo se abre en Swagger Editor, Postman o Insomnia, o genera tipos
(`npx openapi-typescript renaser-api.json -o api.d.ts`).

## MCP: que la IA conozca la API

Es para que un agente (Claude Code, Cursor) **consulte** qué endpoints hay y cómo se usan, en vez de
leer los ~170 controllers. **Solo lectura:** se publican como herramientas solo las operaciones `GET`
(225); las escrituras ni aparecen (`McpDeLaApiConfig`). Si el agente llama una herramienta sin
sesión, el servidor responde 401 como a cualquiera.

Conectarlo en Claude Code, con el backend local corriendo:

```bash
claude mcp add --transport http renaser-api http://localhost:8080/mcp
```

## Por qué está apagado en prod

`/swagger-ui`, `/v3/api-docs`, `/mcp` y `/mcp-ui` viven **fuera** de `/api/v1/**`, así que el
filtro de `SecurityConfig` no los protege, y springdoc avisa que el MCP no trae autenticación propia.
Publicarlos es publicar el mapa entero de la API. Por eso `application-prod.yaml` pone
`OPENAPI_HABILITADO: false` y `MCP_HABILITADO: false` (lo vigila `DocumentacionApagadaEnProdTest`).

Para verlo en un entorno desplegado **a propósito**: el parámetro `OPENAPI_HABILITADO=true` en
Parameter Store (pisa el valor del archivo). El MCP no debería encenderse fuera de local.

## Pruebas

- `OpenApiConfigTest`: etiqueta, pública, permiso.
- `McpDeLaApiConfigTest`: solo `GET` como herramienta.
- `DocumentacionDeLaApiIT`: la app real genera el documento entero (más de 300 rutas, sesión por
  `X-Auth-Token`, ningún actor obligatorio) y sirve Swagger UI. Si un controller nuevo rompe la
  generación, esta prueba lo dice.
- `DocumentacionApagadaEnProdTest`: prod apaga Swagger y MCP.
