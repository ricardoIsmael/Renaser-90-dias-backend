package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.domain.model.agenda.AgendaSemanal;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * D-161: {@code consultar_mi_agenda}, {@code proponer_guardar_agenda} y lo que aplica el boton.
 * Proponer nunca guarda; solo confirmar escribe.
 */
class AgendaGuardadaHerramientasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final AgendaEnMemoria agendas = new AgendaEnMemoria();
    private final ProponerAccionUseCase proponer = mock(ProponerAccionUseCase.class);
    private final PropuestaDeGuardarAgenda propuesta = new PropuestaDeGuardarAgenda(proponer);
    private final GuardarAgendaConfirmable confirmable = new GuardarAgendaConfirmable(agendas);
    private final ConsultarMiAgendaHerramienta consultar = new ConsultarMiAgendaHerramienta(agendas);

    private static InvocacionHerramienta guardar(String dias, String ocupado) {
        return new InvocacionHerramienta(PropuestaDeGuardarAgenda.NOMBRE,
                Map.of(PropuestaDeGuardarAgenda.ARGUMENTO_DIAS, dias, PropuestaDeGuardarAgenda.ARGUMENTO_OCUPADO, ocupado));
    }

    @Test
    @DisplayName("proponer deja una propuesta con un resumen legible y NO guarda nada")
    void proponerNoGuarda() {
        ResultadoHerramienta resultado = propuesta.ejecutar(APRENDIZ, guardar("lunes-viernes", "09:00-18:00"));

        ArgumentCaptor<String> resumen = ArgumentCaptor.forClass(String.class);
        verify(proponer).proponer(eq(APRENDIZ), any(InvocacionHerramienta.class), resumen.capture());
        assertThat(resumen.getValue()).isEqualTo("Recordar que estas ocupado/a lunes, martes, miercoles, jueves, "
                + "viernes de 09:00-18:00 (reemplaza lo guardado esos dias)");
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(agendas.de(APRENDIZ).estaVacia()).isTrue();
    }

    @Test
    @DisplayName("proponer algo que no se entiende es un fallo y no deja propuesta")
    void proponerInvalidoNoPropone() {
        ResultadoHerramienta resultado = propuesta.ejecutar(APRENDIZ, guardar("feriados", "09:00-18:00"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        verifyNoInteractions(proponer);
    }

    @Test
    @DisplayName("confirmar guarda solo esos dias y respeta lo que habia en los demas")
    void confirmarGuarda() {
        agendas.guardar(APRENDIZ, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.SATURDAY), "10:00-12:00"));

        ResultadoHerramienta resultado = confirmable.aplicar(APRENDIZ, guardar("lunes-viernes", "09:00-18:00"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        assertThat(agendas.de(APRENDIZ).delDia(DayOfWeek.MONDAY).texto()).isEqualTo("09:00-18:00");
        assertThat(agendas.de(APRENDIZ).delDia(DayOfWeek.SATURDAY).texto()).isEqualTo("10:00-12:00");
    }

    @Test
    @DisplayName("'ninguno' al confirmar deja libres esos dias")
    void confirmarNinguno() {
        agendas.guardar(APRENDIZ, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.MONDAY), "09:00-18:00"));

        confirmable.aplicar(APRENDIZ, guardar("todos", "ninguno"));

        assertThat(agendas.de(APRENDIZ).estaVacia()).isTrue();
    }

    @Test
    @DisplayName("consultar muestra lo guardado, o que no hay nada")
    void consultar() {
        assertThat(((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarMiAgendaHerramienta.NOMBRE))).contenido())
                .contains("no tiene horas ocupadas guardadas");

        agendas.guardar(APRENDIZ, AgendaSemanal.vacia().conDias(Set.of(DayOfWeek.MONDAY), "09:00-18:00"));

        assertThat(((ResultadoHerramienta.Exito) consultar.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarMiAgendaHerramienta.NOMBRE))).contenido())
                .contains("lunes 09:00-18:00");
    }
}
