package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.AnularVeredictoUseCase.AnularVeredictoCommand;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.ListarEvidenciaComando;
import com.renaser.os.evidence.application.ports.in.evidencia.RevisarManualmenteUseCase.RevisarManualmenteCommand;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
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

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 005 (R-5, FR-011): el líder de mentores LEE las evidencias de la ficha de una persona —el detalle y el archivo
 * firmado— pero no las revisa. La lectura no le da {@code MANAGE_EVIDENCE}: aprobar, rechazar o anular le sigue
 * respondiendo 403 (SC-003). Un líder suspendido no lee nada.
 */
@ExtendWith(MockitoExtension.class)
class EvidenciaServiceLectorLiderTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-19T03:00:00Z"));
    private static final UserId LIDER = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId LUIS = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

    @Mock
    private LoadEvidenciaPort loadEvidenciaPort;
    @Mock
    private SaveEvidenciaPort saveEvidenciaPort;
    @Mock
    private ValidacionIAPort validacionIAPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private IdGenerator idGenerator;

    private EvidenciaService service;

    @BeforeEach
    void preparar() {
        service = new EvidenciaService(loadEvidenciaPort, saveEvidenciaPort, validacionIAPort, userSummaryFinder,
                participacionFinder, ajustarPuntosPort, almacenamientoPort, CLOCK, idGenerator);
    }

    private void actor(UserId id, UserRole rol, UserStatus estado) {
        when(userSummaryFinder.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Fixture", null, rol, estado)));
    }

    private Evidencia fotoDe(UserId dueno) {
        Evidencia evidencia = Evidencia.registrar(EvidenciaId.of(UUID.randomUUID()), dueno,
                new DestinoEvidencia.RegistroHabito(UUID.randomUUID()), TipoEvidencia.FOTO, "renaser",
                "evidencia-habitos/" + dueno + "/foto.jpg", null, null, null, null, false, CLOCK.now(), CLOCK);
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        return evidencia;
    }

    private Evidencia textoDe(UserId dueno) {
        Evidencia evidencia = Evidencia.registrar(EvidenciaId.of(UUID.randomUUID()), dueno,
                new DestinoEvidencia.RegistroHabito(UUID.randomUUID()), TipoEvidencia.TEXTO, null, null,
                "Mis respuestas de la audioterapia", null, null, null, false, CLOCK.now(), CLOCK);
        when(loadEvidenciaPort.byId(evidencia.id())).thenReturn(Optional.of(evidencia));
        return evidencia;
    }

    @Test
    @DisplayName("un lider activo lee el detalle de la evidencia de otra persona, texto incluido")
    void liderLeeElDetalle() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        Evidencia texto = textoDe(ANA);

        Evidencia leida = service.porId(LIDER, texto.id());

        assertThat(leida.contenidoTexto()).isEqualTo("Mis respuestas de la audioterapia");
    }

    @Test
    @DisplayName("un lider activo recibe el archivo firmado de la evidencia de otra persona; un texto no tiene archivo")
    void liderRecibeElArchivoFirmado() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        Evidencia foto = fotoDe(ANA);
        Evidencia texto = textoDe(ANA);
        when(almacenamientoPort.firmarLectura(any(), any())).thenReturn(URI.create("https://almacen/foto-firmada"));

        var url = service.urlDeLectura(LIDER, foto.id());

        assertThat(url).get().satisfies(u -> {
            assertThat(u.url()).isEqualTo("https://almacen/foto-firmada");
            // Vence en 10 minutos: lo mismo que ve el administrador.
            assertThat(u.venceEn()).isEqualTo(CLOCK.now().plusSeconds(600));
        });
        assertThat(service.urlDeLectura(LIDER, texto.id())).isEmpty();
    }

    @Test
    @DisplayName("autorizacion negativa: un lider SUSPENDIDO no lee ni el detalle ni el archivo, y no se firma nada")
    void liderSuspendidoNoLee() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        Evidencia foto = fotoDe(ANA);

        assertThatThrownBy(() -> service.porId(LIDER, foto.id())).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.urlDeLectura(LIDER, foto.id())).isInstanceOf(NotAuthorizedException.class);
        verify(almacenamientoPort, never()).firmarLectura(any(), any());
    }

    @Test
    @DisplayName("SC-003: leer no es revisar; el lider no aprueba, no rechaza y no anula, y nada se guarda")
    void liderNoRevisaNiAnula() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        EvidenciaId cualquiera = EvidenciaId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.revisar(new RevisarManualmenteCommand(LIDER, cualquiera, true, "bien")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.revisar(new RevisarManualmenteCommand(LIDER, cualquiera, false, "mal")))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.anular(new AnularVeredictoCommand(LIDER, cualquiera, "no")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveEvidenciaPort, never()).save(any());
    }

    @Test
    @DisplayName("leer por id no abre el listado de otra persona: sin participanteId el lider ve solo lo suyo")
    void liderNoListaLoAjeno() {
        actor(LIDER, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(LIDER, ANA, null, null, null, null, null)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(loadEvidenciaPort, never()).buscar(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("el resto sigue igual: un aprendiz no lee la evidencia de otro aprendiz")
    void unAprendizNoLeeLoAjeno() {
        actor(LUIS, UserRole.TRAINEE, UserStatus.ACTIVE);
        Evidencia deAna = fotoDe(ANA);

        assertThatThrownBy(() -> service.porId(LUIS, deAna.id())).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.urlDeLectura(LUIS, deAna.id())).isInstanceOf(NotAuthorizedException.class);
    }
}
