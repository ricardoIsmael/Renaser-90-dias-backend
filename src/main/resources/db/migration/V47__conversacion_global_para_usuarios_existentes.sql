-- La conversacion GLOBAL para quienes ya estaban.
--
-- POR QUE HACE FALTA UNA MIGRACION Y NO BASTA EL CODIGO
--
-- GLOBAL se crea de forma perezosa, y solo por un camino: `UsuarioRegistradoChatListener` escucha
-- `UsuarioRegistradoEvent` y llama a `ConversacionService.unirse`, que hace busca-o-crea. Es
-- correcto para todo usuario que se registre a partir de ahora, e inutil para los que ya existen:
-- ellos nunca van a volver a registrarse, asi que nadie dispara ese evento y la fila nunca nace.
--
-- El sintoma no se parecia a la causa. `GET /chat/members` empieza con
-- `MiembroService.requireGlobal()`, de modo que sin GLOBAL responde
-- `404 La conversacion GLOBAL todavia no existe` -- y el listado de MENSAJES DIRECTOS se quedaba
-- sin nombres, mostrando "Conversacion directa" en cada fila, por culpa de una conversacion que no
-- tiene nada que ver con el.
--
-- Se resuelve aca y no con un bootstrap al arrancar: dos instancias arrancando a la vez pelearian
-- por crear la misma fila, y Flyway ya da exactamente lo que hace falta -- se ejecuta una vez, en
-- orden, y queda registrado que se ejecuto.

SET search_path TO renaser, public;

-- `conversacion_global_unica_uk` (V1:1292) es un indice unico parcial sobre (tipo) WHERE
-- tipo = 'GLOBAL', asi que este INSERT es idempotente incluso si otra via ya la creo.
--
-- SOLO SI YA HAY USUARIOS. Una base recien creada no tiene nada que arreglar: el primero que se
-- registre disparara `unirse` y la creara con el, que es el comportamiento correcto y el que ya
-- estaba. Crearla igual en una base vacia dejaria una conversacion sin nadie dentro en todo
-- entorno nuevo -- y rompia las pruebas de `ChatPersistenceAdapterTest`, que crean la suya
-- confiando en que no exista. Esa rotura fue util: senalaba que la migracion estaba apuntando mas
-- ancho que su propio nombre.
INSERT INTO conversaciones (tipo, nombre)
SELECT 'GLOBAL', 'Comunidad Global'
WHERE NOT EXISTS (SELECT 1 FROM conversaciones WHERE tipo = 'GLOBAL')
  AND EXISTS (SELECT 1 FROM usuarios);

-- Todos los usuarios, sin filtrar por estado: es lo que habria pasado si el listener hubiera
-- existido cuando cada uno se registro. `MiembroService` ya decide a quien MUESTRA -- el roster no
-- filtra por UserStatus a proposito, y el directorio para escribir si. Filtrar aca duplicaria esa
-- decision en un sitio donde nadie iria a buscarla.
INSERT INTO participantes_conversacion (conversacion_id, usuario_id)
SELECT g.id, u.id
FROM conversaciones g
CROSS JOIN usuarios u
WHERE g.tipo = 'GLOBAL'
  AND NOT EXISTS (
        SELECT 1 FROM participantes_conversacion p
        WHERE p.conversacion_id = g.id AND p.usuario_id = u.id);
