package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.CambioDelObjetivo;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.MotivoRechazo;
import com.renaser.os.rocks.api.EdicionDeObjetivoSemanalPort.ResultadoEdicion;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.rocks.domain.model.rocasemanal.VentanaPlanificacionSemanal;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-177: corregir un objetivo semanal desde el chat. La ventana (W-03, RK-5) se mira en la zona de la
 * persona: domingo 12:00 a lunes 09:00, o 2 horas desde que se creo si fue fuera de ella.
 */
class EdicionDeObjetivoSemanalServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Domingo 27, 13:00 en Lima: ventana abierta. */
    private static final Instant DOMINGO_13_EN_LIMA = Instant.parse("2026-09-27T18:00:00Z");
    private static final Instant MIERCOLES_10_EN_LIMA = Instant.parse("2026-09-23T15:00:00Z");

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final RocaMaestra trabajo = new RocaMaestra(new RocaMaestraId(UUID.randomUUID()), aprendiz,
            EjeObjetivo.TRABAJO, "Vender", null, DOMINGO_13_EN_LIMA, DOMINGO_13_EN_LIMA);
    private final ConsultarRocasMaestrasUseCase maestras = mock(ConsultarRocasMaestrasUseCase.class);
    private final ConsultarRocasSemanalesUseCase semanales = mock(ConsultarRocasSemanalesUseCase.class);
    private final EditarDentroDe48hUseCase editar = mock(EditarDentroDe48hUseCase.class);
    private final ConsultarProgresoParticipanteRocksPort progresoPort = mock(ConsultarProgresoParticipanteRocksPort.class);

    private EdicionDeObjetivoSemanalService servicio(Instant ahora, RocaSemanal... rocas) {
        when(progresoPort.deParticipante(aprendiz)).thenReturn(Optional.of(new ProgresoParticipanteRocks(23,
                LocalDate.of(2026, 9, 1), LIMA, RolParticipante.TRAINEE, false, true)));
        when(maestras.misRocasMaestras(aprendiz)).thenReturn(List.of(trabajo));
        when(semanales.misRocasSemanales(aprendiz, 4)).thenReturn(List.of(rocas));
        return new EdicionDeObjetivoSemanalService(maestras, semanales, editar, progresoPort, FixedClock.at(ahora));
    }

    @Test
    @DisplayName("dentro de la ventana: delega al caso de uso de la app solo lo que cambia")
    void editaDentroDeLaVentana() {
        RocaSemanal roca = semanal(Instant.parse("2026-09-21T13:00:00Z"));

        ResultadoEdicion resultado = servicio(DOMINGO_13_EN_LIMA, roca).editar(aprendiz, 4, "TRABAJO",
                new CambioDelObjetivo(null, "Poco tiempo", null, 6));

        assertThat(resultado).isEqualTo(new ResultadoEdicion.Editado("TRABAJO"));
        verify(editar).editar(new EditarRocaSemanalCommand(aprendiz, roca.id(), null, "Poco tiempo", null, 6));
    }

    @Test
    @DisplayName("creado en plazo y ya fuera de la ventana: VENTANA_CERRADA, sin llamar al caso de uso")
    void ventanaCerrada() {
        // Creado el lunes 21 a las 08:00 en Lima (13:00 UTC): en plazo. El miercoles ya no se edita.
        RocaSemanal roca = semanal(Instant.parse("2026-09-21T13:00:00Z"));

        ResultadoEdicion resultado = servicio(MIERCOLES_10_EN_LIMA, roca).editar(aprendiz, 4, "TRABAJO",
                new CambioDelObjetivo("Vender 3", null, null, null));

        assertThat(resultado).isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.VENTANA_CERRADA));
        verify(editar, never()).editar(any());
    }

    @Test
    @DisplayName("creado a destiempo: 2 horas de margen, contadas en la zona de la persona")
    void margenTardio() {
        // Creado el miercoles 23 a las 09:00 en Lima (14:00 UTC). A las 10:00 en Lima sigue editable.
        RocaSemanal roca = semanal(Instant.parse("2026-09-23T14:00:00Z"));

        ResultadoEdicion resultado = servicio(MIERCOLES_10_EN_LIMA, roca).editar(aprendiz, 4, "TRABAJO",
                new CambioDelObjetivo("Vender 3", null, null, null));

        assertThat(resultado).isEqualTo(new ResultadoEdicion.Editado("TRABAJO"));
    }

    @Test
    @DisplayName("domingo 12:30 UTC es 07:30 en Lima: la ventana del Domingo Ritual todavia no abrio")
    void ventanaEnLaZonaDeLaPersona() {
        RocaSemanal roca = semanal(Instant.parse("2026-09-21T13:00:00Z"));

        ResultadoEdicion resultado = servicio(Instant.parse("2026-09-27T12:30:00Z"), roca).editar(aprendiz, 4,
                "TRABAJO", new CambioDelObjetivo("Vender 3", null, null, null));

        assertThat(resultado).isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.VENTANA_CERRADA));
    }

    @Test
    @DisplayName("un eje sin objetivo esa semana: SIN_OBJETIVO_SEMANAL")
    void sinObjetivo() {
        ResultadoEdicion resultado = servicio(DOMINGO_13_EN_LIMA).editar(aprendiz, 4, "TRABAJO",
                new CambioDelObjetivo("Vender 3", null, null, null));

        assertThat(resultado).isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.SIN_OBJETIVO_SEMANAL));
    }

    @Test
    @DisplayName("nada que cambiar o eje inventado: DATOS_INVALIDOS sin consultar nada")
    void datosInvalidos() {
        EdicionDeObjetivoSemanalService servicio = new EdicionDeObjetivoSemanalService(maestras, semanales, editar,
                progresoPort, FixedClock.at(DOMINGO_13_EN_LIMA));

        assertThat(servicio.editar(aprendiz, 4, "TRABAJO", new CambioDelObjetivo(null, null, null, null)))
                .isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        assertThat(servicio.editar(aprendiz, 4, "NEGOCIO", new CambioDelObjetivo("x", null, null, null)))
                .isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        verifyNoInteractions(maestras, semanales, editar);
    }

    @Test
    @DisplayName("cuenta suspendida (la guarda de la lectura): SIN_ACCESO; texto fuera de escala: DATOS_INVALIDOS")
    void rechazosDelCasoDeUso() {
        RocaSemanal roca = semanal(Instant.parse("2026-09-21T13:00:00Z"));
        EdicionDeObjetivoSemanalService servicio = servicio(DOMINGO_13_EN_LIMA, roca);
        when(editar.editar(any())).thenThrow(new IllegalArgumentException("autoevaluacionInicio debe estar entre 1 y 10"));
        assertThat(servicio.editar(aprendiz, 4, "TRABAJO", new CambioDelObjetivo(null, null, null, 11)))
                .isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.DATOS_INVALIDOS));

        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(maestras).misRocasMaestras(aprendiz);
        assertThat(servicio.editar(aprendiz, 4, "TRABAJO", new CambioDelObjetivo("x", null, null, null)))
                .isEqualTo(new ResultadoEdicion.Rechazado(MotivoRechazo.SIN_ACCESO));
    }

    @Test
    @DisplayName("las constantes de la ventana que ve rag son las del dominio")
    void constantesDeLaVentana() {
        assertThat(EdicionDeObjetivoSemanalPort.VENTANA_ABRE_DOMINGO_HORA)
                .isEqualTo(VentanaPlanificacionSemanal.ABRE_HORA_DOMINGO);
        assertThat(EdicionDeObjetivoSemanalPort.VENTANA_CIERRA_LUNES_HORA)
                .isEqualTo(VentanaPlanificacionSemanal.CIERRA_HORA_LUNES);
        assertThat(EdicionDeObjetivoSemanalPort.MARGEN_TARDIO_HORAS).isEqualTo(2);
    }

    private RocaSemanal semanal(Instant creadoEn) {
        return RocaSemanal.rehydrate(new RocaSemanalId(UUID.randomUUID()), trabajo.id(), 4, "Vender", null, null, null,
                null, null, null, creadoEn, creadoEn);
    }
}
