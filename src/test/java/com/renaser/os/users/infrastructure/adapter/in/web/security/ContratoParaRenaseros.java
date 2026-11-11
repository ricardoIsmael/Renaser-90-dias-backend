package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.users.api.UserRole;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El contrato OpenAPI que se entrega a una integración de renaseros (D-336, docs/API_OPENAPI.md): del documento completo
 * deja SOLO lo que puede usar un participante del programa, para que el archivo —que se comparte, se abre en otras
 * herramientas y se pega en chats de IA— no publique el mapa de lo administrativo.
 *
 * <p>Una operación entra si cumple las dos cosas (doble candado):
 * <ol>
 *   <li>su permiso ({@code x-permiso}) lo tiene un TRAINEE según {@link UserRole#can(Permission)}, la misma matriz que
 *       aplica el servidor; o es pública ({@code security: []}) y es de cuenta (entrar, contraseña, solicitar acceso,
 *       borrar la cuenta);</li>
 *   <li>su ruta no es de personal ({@link #PREFIJOS_DE_PERSONAL}): {@code USE_APP} lo tiene toda cuenta activa, y varias
 *       rutas del equipo lo declaran y dejan el control fino al servicio;</li>
 *   <li>su alcance ({@code @RequiresPermission(scope)}) no es solo del equipo: si nombra roles del equipo y no al propio
 *       participante (o dice «el aprendiz no»), queda fuera. Las mixtas («el propio participante o su mentor») entran.</li>
 * </ol>
 * Una operación sin permiso declarado y no pública no entra (lo que no se sabe, no se publica). Después se quitan los
 * esquemas que solo usaban las rutas que quedaron fuera.
 */
final class ContratoParaRenaseros {

    static final List<String> PREFIJOS_DE_PERSONAL = List.of(
            "/api/v1/admin", "/api/v1/mentor", "/api/v1/leadership", "/api/v1/soporte-ti", "/api/v1/webhooks",
            "/api/v1/integraciones", "/api/v1/participants", "/api/v1/enforcer-events", "/api/v1/analisis-",
            "/api/v1/diagnostico/admin");

    static final List<String> PUBLICAS_DE_CUENTA = List.of(
            "/api/v1/auth/", "/api/v1/account-requests", "/api/v1/account-deletion");

    private static final Pattern ALCANCE = Pattern.compile("\\(alcance: ([^)]*)\\)");
    private static final Pattern ROLES_DEL_EQUIPO = Pattern.compile(
            "(?i)ADMIN|ALCHEMIST|alquimista|MENTOR|l[ií]der|personal|staff|soporte ti|IT_SUPPORT|gu[ií]a");
    private static final Pattern EL_PROPIO = Pattern.compile(
            "(?i)propio|dueñ|dueno|autor|TRAINEE|participante de|quien puede ver|los suyos");

    private static final Set<String> METODOS = Set.of("get", "post", "put", "patch", "delete");
    private static final Pattern REFERENCIA = Pattern.compile("#/components/schemas/([^\"/]+)");

    private ContratoParaRenaseros() {
    }

    static ObjectNode filtrar(ObjectNode completo) {
        ObjectNode doc = completo.deepCopy();
        ObjectNode rutas = (ObjectNode) doc.path("paths");
        List<String> vacias = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = rutas.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> ruta = it.next();
            ObjectNode item = (ObjectNode) ruta.getValue();
            List<String> fuera = new ArrayList<>();
            item.fieldNames().forEachRemaining(metodo -> {
                if (METODOS.contains(metodo) && !entra(ruta.getKey(), item.path(metodo))) fuera.add(metodo);
            });
            item.remove(fuera);
            if (METODOS.stream().noneMatch(item::has)) vacias.add(ruta.getKey());
        }
        rutas.remove(vacias);
        dejarSoloLosEsquemasUsados(doc);
        etiquetar(doc);
        return doc;
    }

    /**
     * Lo contrario: lo que hace el equipo (Administración, Alquimista, líder, mentor, Soporte TI), es decir, toda operación
     * que NO entra en el contrato de renaseros. Documento INTERNO: es el mapa de lo administrativo, no se entrega afuera.
     */
    static ObjectNode delEquipo(ObjectNode completo) {
        ObjectNode doc = completo.deepCopy();
        ObjectNode rutas = (ObjectNode) doc.path("paths");
        List<String> vacias = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = rutas.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> ruta = it.next();
            ObjectNode item = (ObjectNode) ruta.getValue();
            List<String> fuera = new ArrayList<>();
            item.fieldNames().forEachRemaining(metodo -> {
                if (METODOS.contains(metodo) && entra(ruta.getKey(), item.path(metodo))) fuera.add(metodo);
            });
            item.remove(fuera);
            if (METODOS.stream().noneMatch(item::has)) vacias.add(ruta.getKey());
        }
        rutas.remove(vacias);
        dejarSoloLosEsquemasUsados(doc);
        ObjectNode info = (ObjectNode) doc.path("info");
        info.put("title", "Renaser — API del equipo (INTERNO)");
        info.put("description", info.path("description").asText("") + """


                **Documento interno.** Lo que hacen Administración, Alquimista, líder, mentor, guía y Soporte TI (todo lo que \
                no es de un participante). Es el mapa de lo administrativo: no se comparte fuera del equipo ni se pega en \
                chats de IA externos. El permiso de cada operación está en `x-permiso` y el alcance en la descripción.""");
        doc.remove("servers");
        return doc;
    }

    static boolean entra(String ruta, JsonNode operacion) {
        if (PREFIJOS_DE_PERSONAL.stream().anyMatch(ruta::startsWith)) return false;
        JsonNode seguridad = operacion.path("security");
        if (seguridad.isArray() && seguridad.isEmpty()) {
            return PUBLICAS_DE_CUENTA.stream().anyMatch(ruta::startsWith);
        }
        String permiso = operacion.path("x-permiso").asText("");
        if (permiso.isEmpty() || esSoloDelEquipo(operacion.path("description").asText(""))) return false;
        try {
            return UserRole.TRAINEE.can(Permission.valueOf(permiso));
        } catch (IllegalArgumentException desconocido) {
            return false;
        }
    }

    static boolean esSoloDelEquipo(String descripcion) {
        Matcher m = ALCANCE.matcher(descripcion);
        if (!m.find()) return false;
        String alcance = m.group(1);
        if (alcance.toLowerCase(java.util.Locale.ROOT).contains("el aprendiz no")) return true;
        return ROLES_DEL_EQUIPO.matcher(alcance).find() && !EL_PROPIO.matcher(alcance).find();
    }

    /** Los esquemas alcanzables desde las rutas que quedaron (siguiendo los $ref entre esquemas); el resto se quita. */
    private static void dejarSoloLosEsquemasUsados(ObjectNode doc) {
        JsonNode esquemas = doc.path("components").path("schemas");
        if (!(esquemas instanceof ObjectNode todos)) return;
        Set<String> usados = new HashSet<>();
        Deque<String> pendientes = new ArrayDeque<>(referencias(doc.path("paths")));
        while (!pendientes.isEmpty()) {
            String nombre = pendientes.pop();
            if (usados.add(nombre) && todos.has(nombre)) pendientes.addAll(referencias(todos.get(nombre)));
        }
        List<String> sobran = new ArrayList<>();
        todos.fieldNames().forEachRemaining(n -> {
            if (!usados.contains(n)) sobran.add(n);
        });
        todos.remove(sobran);
    }

    private static Set<String> referencias(JsonNode nodo) {
        Set<String> nombres = new HashSet<>();
        Matcher m = REFERENCIA.matcher(nodo.toString());
        while (m.find()) nombres.add(m.group(1));
        return nombres;
    }

    private static void etiquetar(ObjectNode doc) {
        ObjectNode info = (ObjectNode) doc.path("info");
        info.put("title", "Renaser — API para renaseros");
        info.put("description", info.path("description").asText("") + """


                **Alcance de este documento.** Solo las operaciones que puede usar un participante del programa (rol \
                TRAINEE) y las públicas de cuenta. Lo administrativo y del equipo no aparece. Cada pedido se hace con la \
                sesión de la propia persona (`X-Auth-Token`); el servidor vuelve a comprobar el permiso en cada uno.""");
        if (doc.path("tags") instanceof ArrayNode) doc.remove("tags");
        // El servidor de la prueba (localhost con puerto al azar) no le sirve a nadie: quien integra pone su base.
        doc.remove("servers");
    }
}
