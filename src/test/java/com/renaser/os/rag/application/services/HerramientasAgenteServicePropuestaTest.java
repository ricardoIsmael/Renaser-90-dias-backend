package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeMarcarHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code marcar_habito_completado} con el flag {@code confirmacion-con-botones} prendido (fase 2,
 * D-153): el modelo ya no marca, propone. Lo que se fija aca es la razon de ser del cambio —que
 * ningun camino que dispare el modelo llegue a {@code completar}— y que no se ofrezca un boton
 * que el negocio va a rechazar.
 */
@ExtendWith(MockitoExtension.class)
class HerramientasAgenteServicePropuestaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID REGISTRO = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private ConsultarAgendaHabitosPort agendaHabitosPort;

    @Mock
    private ProponerAccionUseCase proponerAccion;

    private HerramientasAgenteService servicio() {
        return new HerramientasAgenteService(agendaHabitosPort, List.of(),
                new PropuestaDeMarcarHabito(agendaHabitosPort, proponerAccion, true));
    }

    private static InvocacionHerramienta marcar(String registroId) {
        return new InvocacionHerramienta(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, registroId));
    }

    private static HabitoDelDia habito(UUID registroId, String estado, Integer puntosEnJuego) {
        return new HabitoDelDia(registroId, "Meditacion", estado, puntosEnJuego, puntosEnJuego == null ? null : 10,
                Instant.parse("2026-09-24T02:00:00Z"), false);
    }

    @Test
    @DisplayName("con el flag prendido no marca: propone una vez, con la invocacion normalizada y el resumen")
    void proponeEnVezDeMarcar() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habito(REGISTRO, "PENDIENTE", 8)));
        String resumen = "Marcar 'Meditacion' como hecho (+8 puntos si lo confirmas ahora)";
        when(proponerAccion.proponer(any(), any(), any())).thenReturn(
                new PropuestaCreada(UUID.randomUUID(), resumen, Instant.parse("2026-09-23T15:10:00Z")));

        // Espacios alrededor y un argumento de mas: lo que se guarda para confirmar es solo el id limpio.
        ResultadoHerramienta resultado = servicio().ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, "  " + REGISTRO + " ", "extra", "x")));

        verify(proponerAccion).proponer(APRENDIZ, marcar(REGISTRO.toString()), resumen);
        verify(agendaHabitosPort, never()).completar(any(), any());
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(((ResultadoHerramienta.Exito) resultado).contenido())
                .contains(resumen).contains("TODAVIA NO esta marcado").contains("Confirmar")
                .doesNotContain("Puntos otorgados");
    }

    @Test
    @DisplayName("un habito que ya no esta en juego (hecho o vencido) es Fallo y no genera propuesta")
    void noPropone_siYaNoEstaEnJuego() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habito(REGISTRO, "EXPIRADO", null)));

        ResultadoHerramienta resultado = servicio().ejecutar(APRENDIZ, marcar(REGISTRO.toString()));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponerAccion, never()).proponer(any(), any(), any());
        verify(agendaHabitosPort, never()).completar(any(), any());
    }

    @Test
    @DisplayName("un registro que no es uno de sus habitos de hoy es Fallo y no genera propuesta")
    void noPropone_siNoEsDeHoy() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habito(UUID.randomUUID(), "PENDIENTE", 10)));

        ResultadoHerramienta resultado = servicio().ejecutar(APRENDIZ, marcar(REGISTRO.toString()));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verify(proponerAccion, never()).proponer(any(), any(), any());
        verify(agendaHabitosPort, never()).completar(any(), any());
    }

    @Test
    @DisplayName("un id inventado se rechaza antes de mirar la agenda, igual que con el flag apagado")
    void idInventado() {
        ResultadoHerramienta resultado = servicio().ejecutar(APRENDIZ, marcar("el-de-la-manana"));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("no es valido");
        verify(agendaHabitosPort, never()).deHoyDe(any());
        verify(proponerAccion, never()).proponer(any(), any(), any());
    }

    @Test
    @DisplayName("si no se puede guardar la propuesta, el modelo recibe un Fallo legible y no la excepcion")
    void fallaAlGuardarLaPropuesta() {
        when(agendaHabitosPort.deHoyDe(APRENDIZ)).thenReturn(List.of(habito(REGISTRO, "PENDIENTE", 8)));
        when(proponerAccion.proponer(any(), any(), any())).thenThrow(new IllegalStateException("db caida"));

        ResultadoHerramienta resultado = servicio().ejecutar(APRENDIZ, marcar(REGISTRO.toString()));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).doesNotContain("db caida");
        verify(agendaHabitosPort, never()).completar(any(), any());
    }

    @Test
    @DisplayName("con el flag prendido, el modelo lee que la herramienta propone y no marca")
    void laDescripcionDiceQuePropone() {
        DefinicionHerramienta marcar = servicio().disponibles(AgenteConversacional.COMPANION).stream()
                .filter(definicion -> definicion.nombre().equals(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO))
                .findFirst().orElseThrow();

        assertThat(marcar.descripcion()).startsWith("Propone").contains("Confirmar")
                .doesNotContain("devuelve los puntos otorgados");
    }
}
