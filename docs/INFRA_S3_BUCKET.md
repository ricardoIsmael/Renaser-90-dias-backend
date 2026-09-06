# Estructura del bucket S3 y migración a la cuenta nueva

**Fecha:** 2026-09-05
**Estado:** estructura verificada contra el código y la base. La migración **no se ejecutó**: falta
que las credenciales de la cuenta nueva estén disponibles (ver §5).

> Documento hermano: [`CLAUDE.md`](../CLAUDE.md) §11 (decisión de S3 sobre Google Drive) y
> [`BITACORA_ERRORES.md`](BITACORA_ERRORES.md).

---

## 1. Cómo está hoy, de verdad

El backend **nunca toca los bytes**: todo sube y baja por URL prefirmada, directo entre el cliente
y S3. El puerto es `AlmacenamientoPort` (4 métodos: `firmarSubida`, `firmarLectura`, `urlPublica`,
`borrar`) y su implementación real es `S3AlmacenamientoAdapter`.

**Ojo con esto antes que nada:** el adaptador real **solo se activa con `STORAGE_PROVEEDOR=s3`**.
Sin esa variable manda `NoOpAlmacenamientoAdapter` y toda URL sale como
`about:blank#pendiente-s3/...`. Es lo primero a verificar cuando un archivo "no se ve".

### 1.1 Prefijos que genera la aplicación

Verificado sobre los 12 lugares que llaman a `firmarSubida`:

| Prefijo | Qué guarda | Quién lo escribe |
|---|---|---|
| `avatares/` | Foto de perfil | `AvatarService` |
| `evidencia-habitos/` | Evidencia de hábito diario | `EvidenciaRegistroService` |
| `muro/` | Fotos y video del Muro | `PublicacionMuroService` |
| `chat/` | Adjuntos y notas de voz | `MensajeService` |
| `onboarding/` | Media del onboarding | `MediaService` |
| `rocas/` | Evidencia de roca diaria | `RocaDiariaService` |
| `dia-sin-celular/` | Evidencia del Santuario | `RachaService` |
| `firmas/` | Firma del Pacto de Sangre | `ContratoService` |
| `calendar/` | Portadas de evento | `EventoService` |
| `soporte/` | Adjuntos de ticket | `TicketSoporteService` |

Dentro de cada prefijo la ruta lleva el id del usuario y el de la entidad; por ejemplo
`evidencia-habitos/{actorId}/{registroId}/{idGenerado}`. Eso ya está bien: **particiona por
persona**, así que borrar todo lo de un usuario (baja de cuenta, GDPR) es un prefijo, no un barrido.

### 1.2 Contenido cargado a mano (no lo sube la app)

Esto entró por fuera y **no sigue ninguna convención**: nombres de archivo crudos en la raíz del
bucket.

| Qué | Cantidad | Estado |
|---|---|---|
| Objetos de cursos | 37 | Subidos |
| Audioterapias (`audioterapias.ruta_storage`) | 13 | Subidos, semanas 1-13 |
| Pastilla Renacer (`audios_espiritu.ruta_storage`) | 0 de 43 | **Nunca se subieron** |

**El agujero real de contenido:** las 43 filas de `audios_espiritu` tienen `drive_file_id` pero
`ruta_storage` en `NULL`, y `EspirituService` lee **solo** `ruta_storage`. Los audios están
catalogados y no suenan ninguno. Además el dueño menciona **44** audios, no 43: falta uno.

---

## 2. Estructura para la cuenta nueva

**El nombre del bucket no cambia** (`s3-renaser90dias`), por pedido explícito: así ninguna ruta ya
guardada en la base queda inválida. Los nombres de bucket son globales en todo S3, con lo cual esto
solo funciona si el bucket viejo se libera antes, o si se acepta uno distinto — ver §5.

Lo único que se ordena es el contenido de catálogo, que hoy está suelto en la raíz:

```
s3-renaser90dias/
├── contenido/                 <- catálogo. Lo sube el equipo, nunca la app.
│   ├── cursos/
│   ├── audioterapias/         <- 13 mp3, uno por semana (1-13)
│   └── pastilla-renacer/      <- 43-44 mp3, uno por día de audio
│
├── avatares/                  <- de acá para abajo lo genera la app.
├── evidencia-habitos/{userId}/{registroId}/{id}
├── muro/
├── chat/
├── onboarding/
├── rocas/
├── dia-sin-celular/
├── firmas/
├── calendar/
└── soporte/
```

**Por qué separar `contenido/` del resto, y no es cosmético:** son dos cosas con ciclo de vida y
permisos opuestos. El catálogo es de lectura, lo comparten todos los aprendices, casi nunca cambia
y conviene servirlo con caché larga. Lo de los usuarios es privado, se escribe todo el tiempo, y
tiene que poder borrarse por persona. Mezclarlos en la raíz obliga a que cualquier regla —una
política de acceso, una de ciclo de vida, un cálculo de costo— se escriba archivo por archivo en
vez de por prefijo.

---

## 3. Acceso: qué es público y qué no

Regla: **nada es público salvo `avatares/`.** Todo lo demás se sirve con URL prefirmada, que caduca.

- **Evidencias, muro, chat, onboarding, rocas, firmas, soporte:** privados. Son datos personales de
  aprendices — fotos, notas de voz, la firma del Pacto. Una URL prefirmada con vencimiento corto es
  lo correcto y es lo que el código ya hace.
- **`contenido/`:** privado también, servido por URL prefirmada. Es material pago del programa.
- **`avatares/`:** lectura pública anónima. Es lo que exige **D-55**, y es la única pieza que hoy
  está rota: la URL que arma el backend es correcta pero S3 responde **403** porque falta la
  bucket policy. Se resuelve con esto y nada más:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "LecturaPublicaDeAvatares",
    "Effect": "Allow",
    "Principal": "*",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::s3-renaser90dias/avatares/*"
  }]
}
```

Con `Block Public Access` hay que desactivar **solo** `BlockPublicPolicy` y `RestrictPublicBuckets`
en ese bucket. Los otros dos flags se dejan encendidos.

### 3.1 CORS

Necesario porque el navegador (build web de Expo) sube directo contra S3 con la URL prefirmada:

```json
[{
  "AllowedOrigins": ["https://TODO-dominio-del-frontend"],
  "AllowedMethods": ["GET", "PUT", "HEAD"],
  "AllowedHeaders": ["*"],
  "ExposeHeaders": ["ETag"],
  "MaxAgeSeconds": 3000
}]
```

`AllowedOrigins` tiene que ser la misma lista que `renaser.web.cors.origenes` del backend. **Nunca
`*`**: con `*` cualquier página de internet puede subir a tu bucket usando una URL prefirmada que
haya conseguido.

---

## 4. Costo

S3 no es donde se va el dinero de este proyecto, y conviene decirlo para no optimizar lo que no
importa. Con el volumen actual (37 + 13 + 43 objetos de catálogo, más lo que suban los aprendices):

- **Almacenamiento:** unos pocos GB. `S3 Standard` a ~$0.023/GB-mes. Menos de $1 al mes.
- **Lo que sí puede crecer:** las peticiones GET de audio y video, y la **transferencia de salida**.
  Un mp3 de 20 MB escuchado por 100 aprendices todos los días son ~60 GB/mes de salida.

Dos medidas que valen la pena y una que no:

1. **Sí — CloudFront delante del prefijo `contenido/`.** La salida por CloudFront es más barata que
   la de S3 directa, y el catálogo es el caso ideal de caché: mismos archivos, muchas personas, casi
   nunca cambian.
2. **Sí — regla de ciclo de vida** que mueva la evidencia de más de 90 días a `S3 Standard-IA`. Una
   evidencia del día 3 no se vuelve a mirar, pero hay que conservarla.
3. **No — Glacier.** Nada de esto se archiva: la evidencia vieja se sigue consultando de vez en
   cuando y los tiempos de restauración no valen el ahorro a este volumen.

---

## 5. Migración: qué falta y cómo se hace

**No ejecutada.** Falta que las credenciales de la cuenta nueva estén disponibles para el CLI.
**Las claves no van pegadas en un chat ni en un archivo versionado**: van en `aws configure` o en el
`.env` de la raíz, que ya está en `.gitignore`.

Además, el nombre de bucket es global en todo S3: si la cuenta vieja todavía tiene
`s3-renaser90dias`, la nueva no puede crearlo hasta que se libere. Hay que decidir entre vaciar y
borrar el viejo primero (hay una ventana sin servicio), o aceptar un nombre nuevo y actualizar las
rutas guardadas.

Con dos perfiles configurados (`viejo` y `nuevo`):

```bash
# 1. Crear el bucket en la cuenta nueva
aws s3api create-bucket --bucket s3-renaser90dias --region us-east-1 --profile nuevo

# 2. Copiar todo lo que ya existe
aws s3 sync s3://s3-renaser90dias s3://s3-renaser90dias \
    --source-region us-east-1 --region us-east-1 \
    --profile viejo --acl bucket-owner-full-control

# 3. Aplicar politica de avatares y CORS (los JSON de la seccion 3)
aws s3api put-bucket-policy --bucket s3-renaser90dias --policy file://politica-avatares.json --profile nuevo
aws s3api put-bucket-cors   --bucket s3-renaser90dias --cors-configuration file://cors.json --profile nuevo

# 4. Verificar que el conteo coincide antes de tocar nada del viejo
aws s3 ls s3://s3-renaser90dias --recursive --summarize --profile viejo | tail -2
aws s3 ls s3://s3-renaser90dias --recursive --summarize --profile nuevo | tail -2
```

El paso 4 no es opcional: es lo único que distingue "migrado" de "creí que migré".

### 5.1 Lo que queda pendiente después de migrar

- **Subir los 43-44 mp3 de Pastilla Renacer** a `contenido/pastilla-renacer/` y completar
  `audios_espiritu.ruta_storage` en las 43 filas. Hasta que eso pase, ningún audio de Espíritu suena.
- **Confirmar si son 43 o 44**, y si el catálogo debería llegar al día 90 (hoy cubre audios 1-43,
  que con desbloqueo en el día 8 son los días 8 a 50).
- **Mover el contenido de catálogo** de la raíz a `contenido/`, y actualizar las rutas guardadas en
  `audioterapias.ruta_storage`, `cursos.portada_ruta` y `recursos_leccion.url`. Es una migración
  Flyway de datos, no un `UPDATE` a mano.
- **Rotar las credenciales viejas.** Están en texto plano en `.run/RenaserOsApplication.run.xml`,
  dentro de una carpeta sincronizada con OneDrive.
