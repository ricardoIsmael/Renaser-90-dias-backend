package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder.AccionDelMapa;
import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder.HitoDelMapa;
import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder.MapaDeRenacimiento;
import com.renaser.os.onboarding.api.MapaDeRenacimientoFinder.ObjetivoDelMapa;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.mapa.LoadMapaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort.ValorDeRespuesta;
import com.renaser.os.onboarding.domain.model.mapa.AccionMapa;
import com.renaser.os.onboarding.domain.model.mapa.AccionesDelMapa;
import com.renaser.os.onboarding.domain.model.mapa.AreaMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocoloReemplazoMapa;
import com.renaser.os.onboarding.domain.model.mapa.ProtocolosDelMapa;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-233: el Mapa completo para otro modulo, desde las respuestas por clave, las dos listas y la marca. */
class LecturaDelMapaServiceTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-30T15:00:00Z"));

    private final LeerRespuestasPorClavePort respuestas = mock(LeerRespuestasPorClavePort.class);
    private final LoadMapaPort listas = mock(LoadMapaPort.class);
    private final EtapaOnboardingPort etapas = mock(EtapaOnboardingPort.class);
    private final LecturaDelMapaService service = new LecturaDelMapaService(respuestas, listas, etapas);

    private void contesto(Map<String, ValorDeRespuesta> valores, AccionesDelMapa acciones,
                          ProtocolosDelMapa protocolos, Set<String> completados) {
        when(respuestas.deUsuario(eq(PERSONA), any())).thenReturn(valores);
        when(listas.accionesDe(PERSONA)).thenReturn(acciones);
        when(listas.protocolosDe(PERSONA)).thenReturn(protocolos);
        when(etapas.flujosCompletados(PERSONA)).thenReturn(completados);
    }

    private static ValorDeRespuesta texto(String valor) {
        return new ValorDeRespuesta(valor, null);
    }

    @Test
    @DisplayName("sin ninguna respuesta ni lista es 'sin mapa', no un error")
    void sinMapa() {
        contesto(Map.of(), AccionesDelMapa.vacio(), ProtocolosDelMapa.vacio(), Set.of());

        MapaDeRenacimiento mapa = service.delParticipante(PERSONA);

        assertThat(mapa).isEqualTo(MapaDeRenacimiento.sinMapa());
        assertThat(mapa.recorrido()).isFalse();
    }

    @Test
    @DisplayName("pide todas las claves del Mapa en UNA consulta: prioridad, los tres objetivos, los nueve hitos y el retorno")
    void pideTodasLasClaves() {
        contesto(Map.of(), AccionesDelMapa.vacio(), ProtocolosDelMapa.vacio(), Set.of());
        var pedidas = new java.util.concurrent.atomic.AtomicReference<Set<String>>();
        when(respuestas.deUsuario(eq(PERSONA), any())).thenAnswer(inv -> {
            pedidas.set(inv.getArgument(1));
            return Map.of();
        });

        service.delParticipante(PERSONA);

        assertThat(pedidas.get()).contains("map_priority_area", "map_return_protocol", "map_health_goal_text",
                "map_health_baseline", "map_health_target_day90", "map_health_unit", "map_health_reason",
                "map_business_currency", "map_business_period", "map_relations_bond", "map_relations_baseline_scale",
                "map_relations_target_scale", "map_relations_observable_change", "map_milestone_health_30",
                "map_milestone_business_60", "map_milestone_relations_90");
    }

    @Test
    @DisplayName("arma el Mapa completo: objetivos por area, relaciones en escala, hitos por dia, acciones y reemplazos")
    void mapaCompleto() {
        Map<String, ValorDeRespuesta> valores = new HashMap<>();
        valores.put("map_priority_area", texto("salud"));
        valores.put("map_health_result_type", texto("peso"));
        valores.put("map_health_baseline", texto("92"));
        valores.put("map_health_target_day90", texto("85"));
        valores.put("map_health_unit", texto("kg"));
        valores.put("map_health_evidence", texto("foto de la balanza"));
        valores.put("map_health_reason", texto("quiero jugar con mis hijos"));
        valores.put("map_health_goal_text", texto("Bajar de 92 a 85 kg al dia 90"));
        valores.put("map_relations_bond", texto("pareja"));
        valores.put("map_relations_baseline_scale", new ValorDeRespuesta(null, (short) 5));
        valores.put("map_relations_target_scale", new ValorDeRespuesta(null, (short) 8));
        valores.put("map_milestone_health_30", texto("89 kg"));
        valores.put("map_milestone_relations_60", texto("una cita por semana"));
        valores.put("map_return_protocol", texto("  caminar 10 minutos  "));
        AccionMapa caminar = AccionMapa.crear(UUID.randomUUID(), PERSONA, "a1", AreaMapa.SALUD,
                "Caminar 40 minutos", 5, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), null, null, RELOJ);
        ProtocoloReemplazoMapa reemplazo = ProtocoloReemplazoMapa.crear(UUID.randomUUID(), PERSONA, "p1", "ansiedad",
                "llego cansado", "abrir el refri", "tomar agua", RELOJ);
        contesto(valores, new AccionesDelMapa(List.of(caminar)), new ProtocolosDelMapa(List.of(reemplazo)),
                Set.of("mapa_dia7"));

        MapaDeRenacimiento mapa = service.delParticipante(PERSONA);

        assertThat(mapa.recorrido()).isTrue();
        assertThat(mapa.completado()).isTrue();
        assertThat(mapa.prioridad()).isEqualTo("salud");
        assertThat(mapa.objetivos()).containsExactly(
                new ObjetivoDelMapa("salud", "peso", "92", "85", "kg", null, null, "foto de la balanza",
                        "quiero jugar con mis hijos", "Bajar de 92 a 85 kg al dia 90"),
                new ObjetivoDelMapa("relaciones", "pareja", "5", "8", "de 10", null, null, null, null, null));
        assertThat(mapa.hitos()).containsExactly(new HitoDelMapa("salud", 30, "89 kg"),
                new HitoDelMapa("relaciones", 60, "una cita por semana"));
        assertThat(mapa.protocoloDeRetorno()).isEqualTo("caminar 10 minutos");
        assertThat(mapa.acciones()).containsExactly(new AccionDelMapa("salud", "Caminar 40 minutos", 5));
        assertThat(mapa.reemplazos()).containsExactly("Cuando llego cansado, en lugar de abrir el refri, hare tomar agua.");
    }

    @Test
    @DisplayName("a medias: sale lo que haya, sin la marca de completado y sin objetivos vacios")
    void aMedias() {
        contesto(Map.of("map_priority_area", texto("relaciones")), AccionesDelMapa.vacio(),
                ProtocolosDelMapa.vacio(), Set.of("ficha_inicial"));

        MapaDeRenacimiento mapa = service.delParticipante(PERSONA);

        assertThat(mapa.recorrido()).isTrue();
        assertThat(mapa.completado()).isFalse();
        assertThat(mapa.prioridad()).isEqualTo("relaciones");
        assertThat(mapa.objetivos()).isEmpty();
        assertThat(mapa.protocoloDeRetorno()).isNull();
    }
}
