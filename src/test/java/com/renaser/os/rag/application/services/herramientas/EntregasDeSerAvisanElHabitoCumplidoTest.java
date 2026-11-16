package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.EstadoClase;
import com.renaser.os.rag.application.ports.out.audioterapia.AudioterapiaSemanalPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.domain.model.herramienta.HabitoCumplido;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #99 de la app: cumplir la Pastilla Renacer, la Audioterapia o la Clase diaria desde SER no apagaba las alarmas ni celebraba,
 * porque esas tres se cierran por su propio camino y el resultado no decia QUE habito se cumplio. Ahora la entrega devuelve
 * {@link HabitoCumplido} (registro, habito, dia y puntos), igual que {@code marcar_habito_completado} desde la spec 038.
 */
class EntregasDeSerAvisanElHabitoCumplidoTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 11, 9);
    private static final UUID REGISTRO = UUID.fromString("99999999-0000-0000-0000-000000000001");
    private static final UUID HABITO = UUID.fromString("99999999-0000-0000-0000-000000000002");

    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);

    private static HabitoDelDia deHoy(String clave, String estado, Integer puntosEnJuego) {
        return new HabitoDelDia(REGISTRO, "Titulo", estado, puntosEnJuego, 20, null, false, List.of(), clave, null, null,
                "Mente", HABITO, HOY);
    }

    private static HabitoDelDia otro() {
        return new HabitoDelDia(UUID.randomUUID(), "Otro", "PENDIENTE", 5, 5, null, false, List.of(), "OTRO", null, null,
                "Cuerpo", UUID.randomUUID(), HOY);
    }

    private static HabitoCumplido cumplido(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).cumplido();
    }

    // ─── Clase diaria ───────────────────────────────────────────────────────────────────────

    private final ClaseDiariaDelAprendizPort clase = mock(ClaseDiariaDelAprendizPort.class);
    private final EntregarClaseDeHoyConfirmable entregarClase = new EntregarClaseDeHoyConfirmable(clase, agenda);
    private static final InvocacionHerramienta CLASE_GUARDADA = new InvocacionHerramienta(PropuestaDeEntregarClaseDeHoy.NOMBRE, Map.of(
            PropuestaDeEntregarClaseDeHoy.ARGUMENTO_RESUMEN, "La constancia pesa mas que la intensidad.",
            PropuestaDeEntregarClaseDeHoy.ARGUMENTO_LECCION_ID, "lec-12",
            PropuestaDeEntregarClaseDeHoy.ARGUMENTO_DIA_PROGRAMA, "12"));

    private void claseDisponible() {
        when(clase.claseDeHoy(APRENDIZ)).thenReturn(new ClaseDeHoy(EstadoClase.DISPONIBLE, 12, "Fundamentos", "lec-12",
                "Clase 12", false, 15, 2000));
        when(clase.entregar(APRENDIZ, "lec-12", "La constancia pesa mas que la intensidad.")).thenReturn(8);
    }

    @Test
    @DisplayName("Clase diaria entregada desde SER: dice que habito y que dia se cumplieron, con sus puntos")
    void claseAvisaElHabito() { // spec 038 FR-009
        claseDisponible();
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(otro(), deHoy(HabitoDelDia.CLAVE_CLASE_DIARIA, "PENDIENTE", 8)));

        assertThat(cumplido(entregarClase.aplicar(APRENDIZ, CLASE_GUARDADA)))
                .isEqualTo(new HabitoCumplido(REGISTRO, HABITO, HOY, 8));
    }

    @Test
    @DisplayName("Clase ya entregada antes hoy (segundo resumen, no suma): no vuelve a celebrar")
    void claseRepetidaNoCelebra() {
        claseDisponible();
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(deHoy(HabitoDelDia.CLAVE_CLASE_DIARIA, "COMPLETADO", null)));

        assertThat(cumplido(entregarClase.aplicar(APRENDIZ, CLASE_GUARDADA))).isNull();
    }

    @Test
    @DisplayName("Si la agenda no se puede leer, la Clase se entrega igual, solo sin el dato")
    void claseSinAgenda() {
        claseDisponible();
        when(agenda.deHoyDe(APRENDIZ)).thenThrow(new IllegalStateException("caido"));

        assertThat(cumplido(entregarClase.aplicar(APRENDIZ, CLASE_GUARDADA))).isNull();
    }

    // ─── Audioterapia ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Audioterapia entregada desde SER: dice que habito y que dia se cumplieron, con sus puntos")
    void audioterapiaAvisaElHabito() { // spec 038 FR-009
        AudioterapiaSemanalPort audioterapia = mock(AudioterapiaSemanalPort.class);
        when(audioterapia.entregarRespuestas(APRENDIZ, REGISTRO, "Calma")).thenReturn(10);
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(otro(), deHoy("AUDIO_THERAPY_WEEKLY", "PENDIENTE", 10)));

        ResultadoHerramienta resultado = new EntregarResumenAudioterapiaConfirmable(audioterapia, agenda).aplicar(APRENDIZ,
                new InvocacionHerramienta(PropuestaDeResumenAudioterapia.NOMBRE, Map.of(
                        PropuestaDeResumenAudioterapia.ARGUMENTO_TEXTO, "Calma",
                        PropuestaDeResumenAudioterapia.ARGUMENTO_REGISTRO_ID, REGISTRO.toString())));

        assertThat(cumplido(resultado)).isEqualTo(new HabitoCumplido(REGISTRO, HABITO, HOY, 10));
    }

    // ─── Pastilla Renacer (resumen de Espiritu) ─────────────────────────────────────────────

    @Test
    @DisplayName("Resumen de Espiritu desde SER: avisa la Pastilla Renacer de hoy con los puntos que estaban en juego")
    void pastillaAvisaElHabito() { // spec 038 FR-009
        EnfoqueDiarioDelAprendizPort enfoque = mock(EnfoqueDiarioDelAprendizPort.class);
        when(enfoque.entregarResumenEspiritu(APRENDIZ, 5, "Aprendi")).thenReturn(true);
        when(agenda.deHoyDe(APRENDIZ)).thenReturn(List.of(otro(), deHoy(HabitoDelDia.CLAVE_PASTILLA_RENACER, "PENDIENTE", 15)));

        ResultadoHerramienta resultado = new EntregarResumenEspirituConfirmable(enfoque, agenda).aplicar(APRENDIZ,
                new InvocacionHerramienta(PropuestaDeResumenEspiritu.NOMBRE, Map.of(
                        PropuestaDeResumenEspiritu.ARGUMENTO_RESUMEN, "Aprendi",
                        PropuestaDeResumenEspiritu.ARGUMENTO_DIA, "5")));

        assertThat(cumplido(resultado)).isEqualTo(new HabitoCumplido(REGISTRO, HABITO, HOY, 15));
    }
}
