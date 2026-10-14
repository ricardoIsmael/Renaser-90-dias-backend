package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola;
import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola.Probadora;
import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola.Actor;
import com.renaser.os.plataforma.application.ports.out.CargarDesplieguesPort;
import com.renaser.os.plataforma.application.ports.out.MetricasDeDesplieguePort;
import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora;
import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora.MetricaDeLaHora;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Arma la vista de la consola del despliegue de una funcionalidad: la fila leída de la BASE (no de la copia en memoria,
 * como {@link FuncionalidadesEnConsola}), los probadores con nombre y correo, quién lo cambió y las últimas 24 h.
 */
@Component
class DesplieguesEnConsola {

    private final CargarDesplieguesPort cargarDesplieguesPort;
    private final MetricasDeDesplieguePort metricasPort;
    private final UserSummaryFinder userSummaryFinder;
    private final CorreosDeUsuariosFinder correosFinder;
    private final Clock clock;

    DesplieguesEnConsola(CargarDesplieguesPort cargarDesplieguesPort, MetricasDeDesplieguePort metricasPort,
                         UserSummaryFinder userSummaryFinder, CorreosDeUsuariosFinder correosFinder, Clock clock) {
        this.cargarDesplieguesPort = cargarDesplieguesPort;
        this.metricasPort = metricasPort;
        this.userSummaryFinder = userSummaryFinder;
        this.correosFinder = correosFinder;
        this.clock = clock;
    }

    DespliegueEnConsola detalle(String clave) {
        Instant ahora = clock.now();
        List<MetricaDeLaHora> metricas = MetricasPorHora.ultimas24Horas(ahora,
                metricasPort.desde(clave, MetricasPorHora.desde(ahora)));
        boolean desplegable = CatalogoDeFuncionalidades.inicial().definicion(clave)
                .map(DespliegueDeFuncionalidad::admite).orElse(false);
        Optional<DespliegueDeFuncionalidad> despliegue = cargarDesplieguesPort.de(clave);
        if (despliegue.isEmpty()) {
            return new DespliegueEnConsola(clave, false, desplegable, DespliegueDeFuncionalidad.PARA_TODOS, 0L, null,
                    null, null, List.of(), metricas);
        }
        DespliegueDeFuncionalidad d = despliegue.get();
        Set<UserId> personas = new HashSet<>(d.probadores());
        personas.add(d.actualizadoPor());
        Map<UserId, UserSummary> resumenes = userSummaryFinder.findByIds(personas);
        Map<UserId, String> correos = correosFinder.correosDe(d.probadores());
        return new DespliegueEnConsola(clave, true, desplegable, d.porcentaje(), d.version(), d.iniciadoEn(),
                new Actor(d.actualizadoPor(), nombre(resumenes, d.actualizadoPor())), d.actualizadoEn(),
                probadores(d, resumenes, correos), metricas);
    }

    private static List<Probadora> probadores(DespliegueDeFuncionalidad despliegue, Map<UserId, UserSummary> resumenes,
                                              Map<UserId, String> correos) {
        return despliegue.probadores().stream()
                .map(id -> new Probadora(id, nombre(resumenes, id), correos.get(id)))
                .sorted(Comparator.comparing(Probadora::nombre, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(probadora -> probadora.id().value()))
                .toList();
    }

    private static String nombre(Map<UserId, UserSummary> resumenes, UserId id) {
        UserSummary resumen = resumenes.get(id);
        return resumen == null ? null : resumen.fullName();
    }
}
