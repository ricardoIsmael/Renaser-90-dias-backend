package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.CierreDeSemanaPort;
import com.renaser.os.rocks.api.CierreDeSemanaPort.MotivoRechazo;
import com.renaser.os.rocks.api.CierreDeSemanaPort.ResultadoCierre;
import com.renaser.os.rocks.api.CierreDeSemanaPort.RevisionDelEje;
import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CierreDeSemanaService}: resuelve la roca semanal de cada eje en la semana pedida, delega
 * en {@link CerrarSemanaUseCase} y no pisa una revision ya hecha.
 */
class CierreDeSemanaServiceTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-09-20T15:00:00Z");
    private static final int SEMANA = 3;

    private static final RocaMaestra MAESTRA_CUERPO = maestra(EjeObjetivo.CUERPO);
    private static final RocaMaestra MAESTRA_TRABAJO = maestra(EjeObjetivo.TRABAJO);
    private static final RocaSemanal SEMANAL_CUERPO = semanal(MAESTRA_CUERPO, null);
    private static final RocaSemanal SEMANAL_TRABAJO = semanal(MAESTRA_TRABAJO, null);

    private static final RevisionDelEje REVISION_CUERPO = new RevisionDelEje("CUERPO", 7, "Poco sueno", "Dormir 22:30");
    private static final RevisionDelEje REVISION_TRABAJO = new RevisionDelEje("TRABAJO", 4, "Reuniones", "Bloquear la agenda");

    private final ConsultarRocasMaestrasUseCase maestras = mock(ConsultarRocasMaestrasUseCase.class);
    private final ConsultarRocasSemanalesUseCase semanales = mock(ConsultarRocasSemanalesUseCase.class);
    private final CerrarSemanaUseCase cerrar = mock(CerrarSemanaUseCase.class);
    private final CierreDeSemanaService service = new CierreDeSemanaService(maestras, semanales, cerrar);

    @BeforeEach
    void rocas() {
        when(maestras.misRocasMaestras(APRENDIZ)).thenReturn(List.of(MAESTRA_CUERPO, MAESTRA_TRABAJO));
        when(semanales.misRocasSemanales(APRENDIZ, SEMANA)).thenReturn(List.of(SEMANAL_CUERPO, SEMANAL_TRABAJO));
    }

    private static RocaMaestra maestra(EjeObjetivo eje) {
        return RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), APRENDIZ, eje, "Objetivo " + eje, null,
                AHORA, AHORA);
    }

    private static RocaSemanal semanal(RocaMaestra maestra, Integer autoevaluacionFin) {
        return RocaSemanal.rehydrate(RocaSemanalId.of(UUID.randomUUID()), maestra.id(), SEMANA, "Titulo", null, null,
                null, autoevaluacionFin, autoevaluacionFin == null ? null : "b", autoevaluacionFin == null ? null : "c",
                AHORA, AHORA);
    }

    @Test
    @DisplayName("cada revision llega al caso de uso de la app con la roca de su eje en la semana pedida")
    void cierraCadaEje() {
        ResultadoCierre resultado = service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_TRABAJO, REVISION_CUERPO));

        assertThat(resultado).isEqualTo(new ResultadoCierre.Cerrada(List.of("TRABAJO", "CUERPO")));
        verify(cerrar).cerrar(new CerrarSemanaCommand(APRENDIZ, SEMANAL_TRABAJO.id(), 4, "Reuniones",
                "Bloquear la agenda"));
        verify(cerrar).cerrar(new CerrarSemanaCommand(APRENDIZ, SEMANAL_CUERPO.id(), 7, "Poco sueno",
                "Dormir 22:30"));
        verify(semanales).misRocasSemanales(APRENDIZ, SEMANA);
    }

    @Test
    @DisplayName("un eje sin objetivo esa semana rechaza TODO el cierre: no se escribe ninguno")
    void sinObjetivoNoEscribeNada() {
        ResultadoCierre resultado = service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_CUERPO,
                new RevisionDelEje("RELACIONES", 5, "x", "y")));

        assertThat(resultado).isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.SIN_OBJETIVO_SEMANAL));
        verify(cerrar, never()).cerrar(any());
    }

    @Test
    @DisplayName("un eje ya revisado no se pisa desde aca, aunque el caso de uso lo permitiria")
    void yaRevisadaNoSePisa() {
        when(semanales.misRocasSemanales(APRENDIZ, SEMANA))
                .thenReturn(List.of(SEMANAL_CUERPO, semanal(MAESTRA_TRABAJO, 8)));

        ResultadoCierre resultado = service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_CUERPO, REVISION_TRABAJO));

        assertThat(resultado).isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.YA_REVISADA));
        verify(cerrar, never()).cerrar(any());
    }

    @Test
    @DisplayName("un eje repetido, inventado, o una autoevaluacion fuera de escala: DATOS_INVALIDOS sin escribir")
    void datosInvalidos() {
        assertThat(service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_CUERPO, REVISION_CUERPO)))
                .isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        assertThat(service.cerrarSemana(APRENDIZ, SEMANA, List.of(new RevisionDelEje("NEGOCIO", 5, "x", "y"))))
                .isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        assertThat(service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_CUERPO,
                new RevisionDelEje("TRABAJO", 11, "x", "y"))))
                .isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.DATOS_INVALIDOS));
        verify(cerrar, never()).cerrar(any());
    }

    @Test
    @DisplayName("participante inexistente o sin acceso: el rechazo del caso de uso se traduce, no sube")
    void traduceLasGuardas() {
        doThrow(new NoSuchElementException("no existe")).when(maestras).misRocasMaestras(APRENDIZ);
        assertThat(service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_CUERPO)))
                .isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.SIN_PROGRAMA));

        doThrow(new NotAuthorizedException("Cuenta suspendida")).when(maestras).misRocasMaestras(APRENDIZ);
        assertThat(service.cerrarSemana(APRENDIZ, SEMANA, List.of(REVISION_CUERPO)))
                .isEqualTo(new ResultadoCierre.Rechazado(MotivoRechazo.SIN_ACCESO));
    }

    @Test
    @DisplayName("la escala publicada en rocks.api es la misma que valida CerrarSemanaCommand")
    void escalaCoincideConElComando() {
        RocaSemanalId id = RocaSemanalId.of(UUID.randomUUID());
        assertThatCode(() -> new CerrarSemanaCommand(APRENDIZ, id, CierreDeSemanaPort.AUTOEVALUACION_MINIMA, "b", "c"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new CerrarSemanaCommand(APRENDIZ, id, CierreDeSemanaPort.AUTOEVALUACION_MAXIMA, "b", "c"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new CerrarSemanaCommand(APRENDIZ, id, CierreDeSemanaPort.AUTOEVALUACION_MINIMA - 1,
                "b", "c")).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> new CerrarSemanaCommand(APRENDIZ, id, CierreDeSemanaPort.AUTOEVALUACION_MAXIMA + 1,
                "b", "c")).isInstanceOf(ConstraintViolationException.class);
    }
}
