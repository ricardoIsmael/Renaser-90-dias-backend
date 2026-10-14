package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Una funcionalidad EN despliegue progresivo (spec 002, US4; contrato fase 2 §3): llega a sus probadores y a las
 * personas cuya {@link Cubeta} es menor que el porcentaje. Sin despliegue (o al 100 %, que lo termina) llega a todos.
 *
 * @param porcentaje 0 a 99: el 100 no es un escalón, es «prender para todos» y deja de haber despliegue
 * @param version    de la secuencia global de la plataforma: el bloqueo optimista del {@code PUT} (409)
 * @param iniciadoEn cuándo empezó este despliegue; no cambia al subir o bajar de escalón
 */
public record DespliegueDeFuncionalidad(String clave, int porcentaje, Set<UserId> probadores, long version,
                                        Instant iniciadoEn, UserId actualizadoPor, Instant actualizadoEn) {

    /** «Prender para todos»: no es un escalón, termina el despliegue. */
    public static final int PARA_TODOS = 100;

    public DespliegueDeFuncionalidad {
        Objects.requireNonNull(clave, "clave es obligatoria");
        if (porcentaje < 0 || porcentaje >= PARA_TODOS) {
            throw new IllegalArgumentException("Un despliegue va de 0 a 99 %: con 100 deja de estar en despliegue");
        }
        probadores = probadores == null ? Set.of() : Set.copyOf(probadores);
        Objects.requireNonNull(iniciadoEn, "iniciadoEn es obligatorio");
        Objects.requireNonNull(actualizadoPor, "actualizadoPor es obligatorio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
    }

    /** Empieza un despliegue nuevo, sin probadores todavía (se suman después: 409 si no hay despliegue). */
    public static DespliegueDeFuncionalidad empezar(String clave, int porcentaje, long version, UserId actor,
                                                    Instant ahora) {
        return new DespliegueDeFuncionalidad(clave, porcentaje, Set.of(), version, ahora, actor, ahora);
    }

    /** Si a esta persona le llega: es probadora o su cubeta cae dentro del porcentaje. */
    public boolean incluye(UserId persona) {
        return probadores.contains(persona) || Cubeta.de(clave, persona) < porcentaje;
    }

    /** El mismo despliegue en otro escalón: conserva cuándo empezó y sus probadores. */
    public DespliegueDeFuncionalidad conPorcentaje(int nuevo, long nuevaVersion, UserId actor, Instant ahora) {
        return new DespliegueDeFuncionalidad(clave, nuevo, probadores, nuevaVersion, iniciadoEn, actor, ahora);
    }

    public DespliegueDeFuncionalidad conProbadora(UserId persona) {
        Set<UserId> nuevos = new HashSet<>(probadores);
        nuevos.add(persona);
        return new DespliegueDeFuncionalidad(clave, porcentaje, nuevos, version, iniciadoEn, actualizadoPor,
                actualizadoEn);
    }

    public DespliegueDeFuncionalidad sinProbadora(UserId persona) {
        Set<UserId> nuevos = new HashSet<>(probadores);
        nuevos.remove(persona);
        return new DespliegueDeFuncionalidad(clave, porcentaje, nuevos, version, iniciadoEn, actualizadoPor,
                actualizadoEn);
    }

    /** La foto para la auditoría (contrato fase 2 §3.4): {@code {"despliegue": {"porcentaje": 25, "probadores": 3}}}. */
    public Map<String, Object> paraAuditoria() {
        Map<String, Object> despliegue = new LinkedHashMap<>();
        despliegue.put("porcentaje", porcentaje);
        despliegue.put("probadores", probadores.size());
        return Map.of("despliegue", despliegue);
    }

    /** La misma forma sin despliegue: {@code {"despliegue": null}}, así toda fila de despliegue lleva la clave. */
    public static Map<String, Object> sinDespliegueParaAuditoria() {
        return Collections.singletonMap("despliegue", null);
    }
}
