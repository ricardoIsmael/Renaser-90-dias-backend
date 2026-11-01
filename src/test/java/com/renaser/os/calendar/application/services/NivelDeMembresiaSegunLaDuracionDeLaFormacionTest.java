package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Spec 023 (coordinador de la 022): el nivel de membresía de un aprendiz se mide sobre la duración de SU programa y no sobre 90 días
 * fijos. El mismo día 45 es la mitad del camino en la Escuela y el 12 % en un Diplomado de un año; quien mira el evento debe ver
 * el nivel que de verdad alcanzó.
 */
@ExtendWith(MockitoExtension.class)
class NivelDeMembresiaSegunLaDuracionDeLaFormacionTest {

    private static final List<NivelMembresia> NIVELES = List.of(
            new NivelMembresia(1, 1, "Bronce", 0),
            new NivelMembresia(2, 2, "Plata", 30),
            new NivelMembresia(3, 3, "Oro", 70));

    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;
    @Mock
    private ResolverAudienciaCursoPort cursoPort;
    @Mock
    private ConsultarPertenenciaAGrupoPort pertenencia;

    private AccesoEventoService acceso() {
        return new AccesoEventoService(progresoPort, nivelPort, cursoPort, (usuario, tipo) -> true, pertenencia);
    }

    private static ProgresoParticipanteCalendar aprendiz(int dia, int duracionDias) {
        return new ProgresoParticipanteCalendar(dia, ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null, duracionDias);
    }

    @Test
    @DisplayName("el día 45 de la Escuela (90 días) ya es nivel Plata")
    void elDia45DeLaEscuelaEsPlata() {
        when(nivelPort.listar()).thenReturn(NIVELES);

        assertThat(acceso().buildVisor(aprendiz(45, 90)).rangoNivel()).isEqualTo(2);
    }

    @Test
    @DisplayName("el mismo día 45 de un Diplomado de un año (365 días) sigue en Bronce")
    void elDia45DeUnDiplomadoDeUnAnioSigueEnBronce() {
        when(nivelPort.listar()).thenReturn(NIVELES);

        assertThat(acceso().buildVisor(aprendiz(45, 365)).rangoNivel()).isEqualTo(1);
    }

    @Test
    @DisplayName("el último día de un Diplomado de un año llega al nivel más alto")
    void elUltimoDiaDeUnDiplomadoDeUnAnioEsOro() {
        when(nivelPort.listar()).thenReturn(NIVELES);

        assertThat(acceso().buildVisor(aprendiz(365, 365)).rangoNivel()).isEqualTo(3);
    }
}
