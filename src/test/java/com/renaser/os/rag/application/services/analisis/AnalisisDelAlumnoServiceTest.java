package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase.ConsultaDeAnalisisDeAlumno;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.application.InterruptoresDePrueba;
import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El análisis de un alumno para su mentor o su guía (spec 031, 031-2 A): el mismo guardián que el Código Renaser del alumno, más la
 * formación (404 si es de otra) y el interruptor «Código Renaser en las fichas» de la spec 034 (404 si está apagado). Autoriza primero y lee
 * después: ningún camino rechazado toca el análisis ni averigua si existe.
 */
@ExtendWith(MockitoExtension.class)
class AnalisisDelAlumnoServiceTest {

    private static final UserId MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId OTRA_MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId AJENA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b9"));
    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final Instant AHORA = Instant.parse("2026-10-26T15:00:00Z");

    @Mock
    private AcompanamientoFinder acompanamiento;
    @Mock
    private UserSummaryFinder usuarios;
    @Mock
    private AlcanceDeFormacionFinder formaciones;
    @Mock
    private Analisis8020Finder analisis;

    private VistaDelAnalisis vista;

    @BeforeEach
    void laMentoraAcompanaAAna() {
        lenient().when(acompanamiento.acompanaVigente(MENTORA, FENIX, AHORA)).thenReturn(true);
        lenient().when(acompanamiento.aprendicesVigentes(FENIX, AHORA)).thenReturn(List.of(ANA));
        lenient().when(usuarios.findById(MENTORA)).thenReturn(Optional.of(new UserSummary(MENTORA, "Luisa", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(usuarios.findById(OTRA_MENTORA)).thenReturn(Optional.of(new UserSummary(OTRA_MENTORA, "Otra", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        vista = new VistaDelAnalisis(Tipo.SEMANA, 10, java.time.LocalDate.of(2026, 10, 19), java.time.LocalDate.of(2026, 10, 25), true, null, null,
                CifrasDePrueba.semanaDeDoce(), List.of(), "LISTO", List.of(), 5, null, null, AHORA);
        lenient().when(analisis.de(any(), any(), any())).thenReturn(vista);
    }

    private AnalisisDelAlumnoService servicio(InterruptoresDePrueba interruptores) {
        return new AnalisisDelAlumnoService(acompanamiento, usuarios, formaciones, interruptores, analisis, FixedClock.at(AHORA));
    }

    private static ConsultaDeAnalisisDeAlumno consulta(UserId actor, UserId alumno) {
        return new ConsultaDeAnalisisDeAlumno(actor, FENIX, alumno, TipoDePeriodo.SEMANA, null);
    }

    @Test
    @DisplayName("la mentora vigente del grupo lee el análisis de su alumna, por la formación y con el interruptor prendido")
    void laMentoraLoLee() {
        assertThat(servicio(InterruptoresDePrueba.conApagadas()).deAlumno(consulta(MENTORA, ANA))).isSameAs(vista);

        verify(formaciones).exigirQueLaVea(MENTORA, ANA);
        verify(analisis).de(ANA, Tipo.SEMANA, null);
    }

    @Test
    @DisplayName("una mentora que no acompaña ese grupo recibe 403 y no se toca el análisis")
    void otraMentora() {
        assertThatThrownBy(() -> servicio(InterruptoresDePrueba.conApagadas()).deAlumno(consulta(OTRA_MENTORA, ANA)))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(analisis);
    }

    @Test
    @DisplayName("una alumna que no es de ese grupo: 403, aunque el grupo sea el de la mentora")
    void alumnaAjenaAlGrupo() {
        assertThatThrownBy(() -> servicio(InterruptoresDePrueba.conApagadas()).deAlumno(consulta(MENTORA, AJENA)))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(analisis);
    }

    @Test
    @DisplayName("una mentora con la cuenta suspendida recibe 403")
    void cuentaSuspendida() {
        when(usuarios.findById(MENTORA)).thenReturn(Optional.of(new UserSummary(MENTORA, "Luisa", null, UserRole.MENTOR, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> servicio(InterruptoresDePrueba.conApagadas()).deAlumno(consulta(MENTORA, ANA)))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(analisis);
    }

    @Test
    @DisplayName("FR-022: una persona de otra formación responde 404, como si no existiera")
    void otraFormacion() {
        doThrow(new ObjetoDeOtraFormacionException()).when(formaciones).exigirQueLaVea(MENTORA, ANA);

        assertThatThrownBy(() -> servicio(InterruptoresDePrueba.conApagadas()).deAlumno(consulta(MENTORA, ANA)))
                .isInstanceOf(ObjetoDeOtraFormacionException.class);

        verifyNoInteractions(analisis);
    }

    @Test
    @DisplayName("031-2 A / spec 034: con «Código Renaser en las fichas» apagado la mentora no ve el análisis (404) y no se lee nada")
    void conElInterruptorDeLaFichaApagado() {
        assertThatThrownBy(() -> servicio(InterruptoresDePrueba.conApagadas(ClavesDeFuncionalidad.RADAR_EN_FICHAS)).deAlumno(consulta(MENTORA, ANA)))
                .isInstanceOf(FuncionalidadApagadaException.class);

        verifyNoInteractions(analisis);
    }
}
