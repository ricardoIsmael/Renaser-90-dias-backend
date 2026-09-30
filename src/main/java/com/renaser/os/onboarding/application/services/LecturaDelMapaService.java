package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort.ValorDeRespuesta;
import com.renaser.os.onboarding.domain.model.mapa.AccionMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocoloReemplazoMapa;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementa {@link MapaDeRenacimientoFinder} (D-233): todas las respuestas del Mapa en una consulta por
 * clave, mas las dos listas ({@code acciones_mapa}, {@code protocolos_reemplazo_mapa}) y la marca de etapa.
 *
 * <p>No pide actor activo como {@code MapaRenacimientoService}: es una lectura para otro modulo, que ya
 * autentico a quien pregunta, y una cuenta suspendida no llega a conversar.
 */
@Service
public class LecturaDelMapaService implements MapaDeRenacimientoFinder {

    static final String PRIORIDAD = "map_priority_area";
    static final String RETORNO = "map_return_protocol";
    private static final List<Integer> DIAS_DE_HITO = List.of(30, 60, 90);

    /** El area del Mapa ({@code acciones_mapa.area}) y el prefijo de sus claves en la V41. */
    private enum Area {
        SALUD("salud", "health"), NEGOCIO("negocio_dinero", "business"), RELACIONES("relaciones", "relations");

        private final String clave;
        private final String prefijo;

        Area(String clave, String prefijo) {
            this.clave = clave;
            this.prefijo = prefijo;
        }

        String pregunta(String campo) {
            return "map_" + prefijo + "_" + campo;
        }

        String hito(int dia) {
            return "map_milestone_" + prefijo + "_" + dia;
        }
    }

    private static final List<String> CAMPOS_DE_OBJETIVO = List.of("result_type", "bond", "baseline",
            "baseline_scale", "target_day90", "target_scale", "unit", "currency", "period", "observable_change",
            "evidence", "reason", "goal_text");

    private static final Set<String> CLAVES = todasLasClaves();

    private final LeerRespuestasPorClavePort leerRespuestasPort;
    private final LoadMapaPort loadMapaPort;
    private final EtapaOnboardingPort etapaPort;

    public LecturaDelMapaService(LeerRespuestasPorClavePort leerRespuestasPort, LoadMapaPort loadMapaPort,
                                 EtapaOnboardingPort etapaPort) {
        this.leerRespuestasPort = leerRespuestasPort;
        this.loadMapaPort = loadMapaPort;
        this.etapaPort = etapaPort;
    }

    @Override
    @Transactional(readOnly = true)
    public MapaDeRenacimiento delParticipante(UserId usuarioId) {
        Map<String, ValorDeRespuesta> respuestas = leerRespuestasPort.deUsuario(usuarioId, CLAVES);
        List<AccionDelMapa> acciones = loadMapaPort.accionesDe(usuarioId).acciones().stream()
                .map(LecturaDelMapaService::aAccion).toList();
        List<String> reemplazos = loadMapaPort.protocolosDe(usuarioId).protocolos().stream()
                .map(ProtocoloReemplazoMapa::frase).toList();
        if (respuestas.isEmpty() && acciones.isEmpty() && reemplazos.isEmpty()) {
            return MapaDeRenacimiento.sinMapa();
        }
        boolean completado = etapaPort.flujosCompletados(usuarioId).contains(MapaRenacimientoService.FLUJO);
        return new MapaDeRenacimiento(true, completado, texto(respuestas, PRIORIDAD), objetivos(respuestas),
                hitos(respuestas), texto(respuestas, RETORNO), acciones, reemplazos);
    }

    private static List<ObjetivoDelMapa> objetivos(Map<String, ValorDeRespuesta> r) {
        return Stream.of(salud(r), negocio(r), relaciones(r)).filter(Objects::nonNull).toList();
    }

    private static ObjetivoDelMapa salud(Map<String, ValorDeRespuesta> r) {
        Area a = Area.SALUD;
        return objetivoSiHayAlgo(new ObjetivoDelMapa(a.clave, texto(r, a.pregunta("result_type")),
                texto(r, a.pregunta("baseline")), texto(r, a.pregunta("target_day90")), texto(r, a.pregunta("unit")),
                null, null, texto(r, a.pregunta("evidence")), texto(r, a.pregunta("reason")),
                texto(r, a.pregunta("goal_text"))));
    }

    private static ObjetivoDelMapa negocio(Map<String, ValorDeRespuesta> r) {
        Area a = Area.NEGOCIO;
        return objetivoSiHayAlgo(new ObjetivoDelMapa(a.clave, texto(r, a.pregunta("result_type")),
                texto(r, a.pregunta("baseline")), texto(r, a.pregunta("target_day90")),
                texto(r, a.pregunta("currency")), texto(r, a.pregunta("period")), null,
                texto(r, a.pregunta("evidence")), texto(r, a.pregunta("reason")), texto(r, a.pregunta("goal_text"))));
    }

    /** Relaciones se mide en una escala de 1 a 10 y trae la conducta propia que va a cambiar. */
    private static ObjetivoDelMapa relaciones(Map<String, ValorDeRespuesta> r) {
        Area a = Area.RELACIONES;
        String base = escala(r, a.pregunta("baseline_scale"));
        String meta = escala(r, a.pregunta("target_scale"));
        return objetivoSiHayAlgo(new ObjetivoDelMapa(a.clave, texto(r, a.pregunta("bond")), base, meta,
                base == null && meta == null ? null : "de 10", null, texto(r, a.pregunta("observable_change")),
                texto(r, a.pregunta("evidence")), texto(r, a.pregunta("reason")), texto(r, a.pregunta("goal_text"))));
    }

    /** Un area que no contesto no aparece: un objetivo con todo en null solo confunde a quien lo lee. */
    private static ObjetivoDelMapa objetivoSiHayAlgo(ObjetivoDelMapa objetivo) {
        boolean vacio = Stream.of(objetivo.tipo(), objetivo.lineaBase(), objetivo.metaDia90(), objetivo.conductaNueva(),
                objetivo.evidencia(), objetivo.porque(), objetivo.metaRedactada()).allMatch(Objects::isNull);
        return vacio ? null : objetivo;
    }

    private static List<HitoDelMapa> hitos(Map<String, ValorDeRespuesta> r) {
        List<HitoDelMapa> hitos = new ArrayList<>();
        for (Area area : Area.values()) {
            for (int dia : DIAS_DE_HITO) {
                String texto = texto(r, area.hito(dia));
                if (texto != null) {
                    hitos.add(new HitoDelMapa(area.clave, dia, texto));
                }
            }
        }
        return hitos;
    }

    private static AccionDelMapa aAccion(AccionMapa accion) {
        return new AccionDelMapa(accion.area().clave(), accion.texto(), accion.frecuenciaSemanal());
    }

    private static String texto(Map<String, ValorDeRespuesta> respuestas, String clave) {
        ValorDeRespuesta valor = respuestas.get(clave);
        return valor == null || valor.texto() == null || valor.texto().isBlank() ? null : valor.texto().trim();
    }

    private static String escala(Map<String, ValorDeRespuesta> respuestas, String clave) {
        ValorDeRespuesta valor = respuestas.get(clave);
        return valor == null || valor.escala() == null ? null : String.valueOf(valor.escala());
    }

    private static Set<String> todasLasClaves() {
        Stream<String> deObjetivos = Stream.of(Area.values())
                .flatMap(area -> CAMPOS_DE_OBJETIVO.stream().map(area::pregunta));
        Stream<String> deHitos = Stream.of(Area.values())
                .flatMap(area -> DIAS_DE_HITO.stream().map(area::hito));
        return Stream.of(deObjetivos, deHitos, Stream.of(PRIORIDAD, RETORNO))
                .flatMap(s -> s).collect(Collectors.toUnmodifiableSet());
    }
}
