package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.TramoPuntos;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_tiempo_para_puntos}: las horas y los minutos salen del codigo, en la zona del
 * aprendiz. El reloj de casi todos los casos esta a la 01:30 UTC del 24, que en Lima son las 20:30
 * del 23 (regla 02/03: una hora UTC que cae en el dia local ANTERIOR). Con la zona mal, la
 * cabecera diria 01:30 y cada plazo quedaria corrido cinco horas.
 */
class ConsultarTiempoParaPuntosHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 20:30 en Lima del 23. */
    private static final Instant NOCHE_EN_LIMA = Instant.parse("2026-09-24T01:30:00Z");
    /** 21:10 en Lima. */
    private static final Instant PLAZO_LEER = Instant.parse("2026-09-24T02:10:00Z");
    /** 00:10 en Lima del 24: vence despues de la medianoche local. */
    private static final Instant PLAZO_MEDITAR = Instant.parse("2026-09-24T05:10:00Z");

    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);

    /**
     * Fixture con la MISMA forma que produce {@code habits} (ver AgendaDelDiaFinderServiceTest):
     * 10 hasta 8 min antes del plazo, despues 9, 8, 7 y 6. {@code puntosAhora} es el tramo en el
     * que cae el reloj del caso, para que el fixture sea coherente.
     */
    private static HabitoDelDia conPlazo(String titulo, Instant plazo, int puntosAhora) {
        List<TramoPuntos> tramos = List.of(new TramoPuntos(plazo.minus(Duration.ofMinutes(8)), 10),
                new TramoPuntos(plazo.minus(Duration.ofMinutes(6)), 9),
                new TramoPuntos(plazo.minus(Duration.ofMinutes(4)), 8),
                new TramoPuntos(plazo.minus(Duration.ofMinutes(2)), 7), new TramoPuntos(plazo, 6));
        return new HabitoDelDia(UUID.randomUUID(), titulo, "PENDIENTE", puntosAhora, 10, plazo, false, tramos);
    }

    private static HabitoDelDia sinHorario(String titulo) {
        return new HabitoDelDia(UUID.randomUUID(), titulo, "PENDIENTE", 10, 10, null, false, List.of());
    }

    private static HabitoDelDia completado(String titulo) {
        return new HabitoDelDia(UUID.randomUUID(), titulo, "COMPLETADO", null, null, null, false, List.of());
    }

    private ResultadoHerramienta ejecutar(Instant ahora, Map<String, String> argumentos) {
        return new ConsultarTiempoParaPuntosHerramienta(agenda, FixedClock.at(ahora)).ejecutar(APRENDIZ,
                new InvocacionHerramienta(ConsultarTiempoParaPuntosHerramienta.NOMBRE, argumentos));
    }

    private List<String> lineasA(Instant ahora, Map<String, String> argumentos, HabitoDelDia... habitos) {
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(habitos));
        when(agenda.zonaDe(APRENDIZ)).thenReturn(LIMA);
        ResultadoHerramienta resultado = ejecutar(ahora, argumentos);
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido().lines().toList();
    }

    @Test
    @DisplayName("desde ahora: hora local, cuanto paga, cuando baja y cuanto falta; primero el que vence antes")
    void desdeAhoraEnLaZonaDelAprendiz() {
        HabitoDelDia meditar = conPlazo("Meditar", PLAZO_MEDITAR, 10);
        HabitoDelDia agua = sinHorario("Agua");
        HabitoDelDia leer = conPlazo("Leer", PLAZO_LEER, 10);

        List<String> lineas = lineasA(NOCHE_EN_LIMA, Map.of(), meditar, agua, completado("Correr"), leer);

        assertThat(lineas).containsExactly(
                "Hora actual del aprendiz: 20:30.",
                "id=" + leer.registroId() + " | Leer | paga_ahora=10 | paga 10 hasta las 21:02 (faltan 32 min), "
                        + "despues 9 y va bajando hasta 6 | vence a las 21:10 (faltan 40 min)",
                "id=" + meditar.registroId() + " | Meditar | paga_ahora=10 | paga 10 hasta las 00:02 del dia "
                        + "siguiente (faltan 3 h 32 min), despues 9 y va bajando hasta 6 | vence a las 00:10 del "
                        + "dia siguiente (faltan 3 h 40 min)",
                "id=" + agua.registroId() + " | Agua | paga_ahora=10 | no vence: no tiene horario",
                "Primero en vencer: Leer, a las 21:10 (faltan 40 min).");
    }

    @Test
    @DisplayName("ya en la gracia: dice el puntaje de ahora y cuando baja al siguiente")
    void enLaGracia() {
        HabitoDelDia leer = conPlazo("Leer", PLAZO_LEER, 8);

        // 02:05 UTC = 21:05 en Lima: tramo de 8, que termina a las 21:06.
        List<String> lineas = lineasA(Instant.parse("2026-09-24T02:05:00Z"), Map.of(), leer);

        assertThat(lineas.get(1)).isEqualTo("id=" + leer.registroId() + " | Leer | paga_ahora=8 | paga 8 hasta "
                + "las 21:06 (faltan 1 min), despues 7 y va bajando hasta 6 | vence a las 21:10 (faltan 5 min)");
    }

    @Test
    @DisplayName("en el ultimo tramo no hay 'despues': solo cuanto falta para vencer, redondeado hacia abajo")
    void enElUltimoTramo() {
        HabitoDelDia leer = conPlazo("Leer", PLAZO_LEER, 6);

        List<String> lineas = lineasA(Instant.parse("2026-09-24T02:09:30Z"), Map.of(), leer);

        assertThat(lineas.get(1)).isEqualTo("id=" + leer.registroId()
                + " | Leer | paga_ahora=6 | vence a las 21:10 (faltan menos de 1 min)");
    }

    @Test
    @DisplayName("con hora: cuanto pagaria a esa hora y cuanto pierde respecto de ahora")
    void conHoraCalculaLoQuePierde() {
        HabitoDelDia leer = conPlazo("Leer", PLAZO_LEER, 10);
        HabitoDelDia meditar = conPlazo("Meditar", PLAZO_MEDITAR, 10);
        HabitoDelDia agua = sinHorario("Agua");

        List<String> lineas = lineasA(NOCHE_EN_LIMA, Map.of("hora", "21:05"), leer, meditar, agua);

        assertThat(lineas).containsExactly(
                "Hora actual del aprendiz: 20:30.",
                "id=" + leer.registroId() + " | Leer | paga_ahora=10 | a las 21:05 pagaria 8 (pierde 2) | vence a "
                        + "las 21:10 (faltan 40 min)",
                "id=" + meditar.registroId() + " | Meditar | paga_ahora=10 | a las 21:05 pagaria 10 (no pierde "
                        + "nada) | vence a las 00:10 del dia siguiente (faltan 3 h 40 min)",
                "id=" + agua.registroId() + " | Agua | paga_ahora=10 | a las 21:05 pagaria 10 (no pierde nada) | "
                        + "no vence: no tiene horario",
                "Primero en vencer: Leer, a las 21:10 (faltan 40 min).");
    }

    @Test
    @DisplayName("con una hora posterior al plazo: ya estaria vencido y pierde todo lo que paga ahora")
    void conHoraPasadoElPlazo() {
        HabitoDelDia leer = conPlazo("Leer", PLAZO_LEER, 10);

        List<String> lineas = lineasA(NOCHE_EN_LIMA, Map.of("hora", "21:30"), leer);

        assertThat(lineas.get(1)).isEqualTo("id=" + leer.registroId() + " | Leer | paga_ahora=10 | a las 21:30 ya "
                + "estaria vencido: pierde los 10 | vence a las 21:10 (faltan 40 min)");
    }

    @Test
    @DisplayName("una hora que hoy ya paso se toma como la de manana, y lo dice")
    void horaQueYaPasoEsLaDeManana() {
        HabitoDelDia leer = conPlazo("Leer", PLAZO_LEER, 10);

        // Son las 20:30 en Lima: las 9:00 de hoy ya pasaron (en UTC todavia no seria "ayer").
        List<String> lineas = lineasA(NOCHE_EN_LIMA, Map.of("hora", "9:00"), leer);

        assertThat(lineas.get(1)).contains("a las 09:00 del dia siguiente ya estaria vencido: pierde los 10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"25:00", "21:60", "9pm", "21h", "21:00:00", "21.00", "manana"})
    @DisplayName("una hora mal formada es un fallo legible, sin consultar la agenda")
    void horaMalFormada(String hora) {
        ResultadoHerramienta resultado = ejecutar(NOCHE_EN_LIMA, Map.of("hora", hora));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo("No entendi la hora. Pasala en formato de 24 "
                + "horas HH:mm, por ejemplo 21:00 para las 9 de la noche."));
        verifyNoInteractions(agenda);
    }

    @Test
    @DisplayName("sin nada en juego (todo hecho o ya vencido aunque el barrido no paso) lo dice en una linea")
    void sinNadaEnJuego() {
        HabitoDelDia vencidoSinBarrer = new HabitoDelDia(UUID.randomUUID(), "Leer", "PENDIENTE", 0, 10, PLAZO_LEER,
                false, List.of());
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(completado("Correr"), vencidoSinBarrer));

        assertThat(ejecutar(NOCHE_EN_LIMA, Map.of())).isEqualTo(ResultadoHerramienta.exito(
                "No le queda ningun habito por entregar hoy: ya no tiene puntos en juego."));
    }

    @Test
    @DisplayName("la hora es opcional: sin argumentos no falta nada")
    void horaOpcional() {
        DefinicionHerramienta definicion = new ConsultarTiempoParaPuntosHerramienta(agenda,
                FixedClock.at(NOCHE_EN_LIMA)).definicion();

        assertThat(definicion.nombre()).isEqualTo("consultar_tiempo_para_puntos");
        assertThat(definicion.parametros()).extracting(ParametroHerramienta::nombre).containsExactly("hora");
        assertThat(definicion.parametros()).noneMatch(ParametroHerramienta::obligatorio);
        assertThat(definicion.obligatoriosFaltantesEn(InvocacionHerramienta.sinArgumentos(definicion.nombre())))
                .isEmpty();
    }
}
