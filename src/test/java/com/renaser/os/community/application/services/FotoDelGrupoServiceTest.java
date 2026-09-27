package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.celula.CambiarFotoDelGrupoUseCase.CambiarFotoDelGrupoCommand;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.celula.FotoDelGrupoPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.PrepararFotoDelGrupoPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La foto propia de un grupo (D-212): la cambian «Admin y el mentor de ese grupo», y nadie más. Todo lo
 * que prueba falla contra el código viejo, donde no existía.
 */
@ExtendWith(MockitoExtension.class)
class FotoDelGrupoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final CelulaId FENIX = CelulaId.of(UUID.randomUUID());
    private static final CelulaId AURORA = CelulaId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final UserId ALQUIMISTA = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR_DE_FENIX = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR_DE_AURORA = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final byte[] SUBIDA = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};
    private static final byte[] PREPARADA = {(byte) 0xFF, (byte) 0xD8, 9, 9};

    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private LoadAsignacionesPort loadAsignacionesPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private FotoDelGrupoPort fotoDelGrupoPort;
    @Mock
    private PrepararFotoDelGrupoPort prepararFoto;
    @Mock
    private AlmacenamientoPort almacenamiento;

    private FotoDelGrupoService servicio;
    private final List<AsignacionCelula> asignacionesDeFenix = new ArrayList<>();

    @BeforeEach
    void preparar() {
        servicio = new FotoDelGrupoService(loadCelulaPort, loadAsignacionesPort, userSummaryFinder, fotoDelGrupoPort,
                prepararFoto, almacenamiento, FixedClock.at(AHORA));
        grupo(FENIX);
        grupo(AURORA);
        lenient().when(loadAsignacionesPort.porCelula(FENIX)).thenReturn(asignacionesDeFenix);
        lenient().when(loadAsignacionesPort.porCelula(AURORA)).thenReturn(List.of(
                asignacion(AURORA, MENTOR_DE_AURORA, FuncionAcompanamiento.MENTOR)));
        asignacionesDeFenix.add(asignacion(FENIX, MENTOR_DE_FENIX, FuncionAcompanamiento.MENTOR));
        asignacionesDeFenix.add(asignacion(FENIX, ANA, FuncionAcompanamiento.APRENDIZ));
        cuenta(ADMIN, UserRole.ADMIN, UserStatus.ACTIVE);
        cuenta(ALQUIMISTA, UserRole.ALCHEMIST, UserStatus.ACTIVE);
        cuenta(MENTOR_DE_FENIX, UserRole.MENTOR, UserStatus.ACTIVE);
        cuenta(MENTOR_DE_AURORA, UserRole.MENTOR, UserStatus.ACTIVE);
        cuenta(ANA, UserRole.TRAINEE, UserStatus.ACTIVE);
        lenient().when(prepararFoto.comoJpegCuadrado(any())).thenReturn(PREPARADA);
    }

    // ── Quién puede ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("el ADMIN cambia la de cualquier grupo: sube la foto preparada con una clave nueva y borra la anterior")
    void elAdminLaCambia() {
        when(fotoDelGrupoPort.reemplazar(eq(FENIX), any())).thenReturn(Optional.of("grupos/vieja.jpg"));

        FotoDelGrupo nueva = servicio.cambiar(new CambiarFotoDelGrupoCommand(ADMIN, FENIX, SUBIDA, "image/jpeg"));

        assertThat(nueva.ruta()).isEqualTo("grupos/" + FENIX + "/foto-" + AHORA.toEpochMilli() + ".jpg");
        assertThat(nueva.cambiadaEn()).isEqualTo(AHORA);
        verify(almacenamiento).subir(nueva.ruta(), PREPARADA, "image/jpeg");
        verify(fotoDelGrupoPort).reemplazar(FENIX, nueva);
        verify(almacenamiento).borrar("grupos/vieja.jpg");
    }

    @Test
    @DisplayName("el mentor que acompaña HOY al grupo cambia la de su grupo")
    void suMentorLaCambia() {
        when(fotoDelGrupoPort.reemplazar(eq(FENIX), any())).thenReturn(Optional.empty());

        servicio.cambiar(new CambiarFotoDelGrupoCommand(MENTOR_DE_FENIX, FENIX, SUBIDA, "image/png"));

        verify(almacenamiento).subir(anyString(), eq(PREPARADA), eq("image/jpeg"));
        verify(almacenamiento, never()).borrar(anyString());
    }

    @Test
    @DisplayName("autorización negativa: el mentor de OTRO grupo, un aprendiz del grupo y el Alquimista reciben 403, sin subir nada")
    void nadieMasLaCambia() {
        for (UserId intruso : List.of(MENTOR_DE_AURORA, ANA, ALQUIMISTA)) {
            assertThatThrownBy(() -> servicio.cambiar(new CambiarFotoDelGrupoCommand(intruso, FENIX, SUBIDA, "image/jpeg")))
                    .as("%s", intruso).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.volverALaDeRenaser(intruso, FENIX)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.actual(intruso, FENIX)).isInstanceOf(NotAuthorizedException.class);
        }
        verifyNoInteractions(prepararFoto, almacenamiento, fotoDelGrupoPort);
    }

    @Test
    @DisplayName("autorización negativa: un mentor que ya no acompaña al grupo (asignación cerrada) recibe 403")
    void unMentorQueYaNoAcompanaNoLaCambia() {
        asignacionesDeFenix.clear();
        AsignacionCelula cerrada = asignacion(FENIX, MENTOR_DE_FENIX, FuncionAcompanamiento.MENTOR);
        cerrada.cerrar(AHORA.minusSeconds(60), MotivoAsignacion.ADMINISTRATIVO);
        asignacionesDeFenix.add(cerrada);

        assertThatThrownBy(() -> servicio.cambiar(new CambiarFotoDelGrupoCommand(MENTOR_DE_FENIX, FENIX, SUBIDA, "image/jpeg")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("autorización negativa: una cuenta SUSPENDIDA recibe 403 aunque sea ADMIN")
    void unaCuentaSuspendidaNoLaCambia() {
        cuenta(ADMIN, UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.cambiar(new CambiarFotoDelGrupoCommand(ADMIN, FENIX, SUBIDA, "image/jpeg")))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(almacenamiento);
    }

    @Test
    @DisplayName("un grupo que no existe es 404")
    void unGrupoQueNoExisteEs404() {
        CelulaId inexistente = CelulaId.of(UUID.randomUUID());
        when(loadCelulaPort.porId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiar(new CambiarFotoDelGrupoCommand(ADMIN, inexistente, SUBIDA, "image/jpeg")))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── Qué se acepta ───────────────────────────────────────────────────────

    @Test
    @DisplayName("tipo y peso acotados: solo JPEG o PNG, hasta 2 MB (400), y solo después de ver quién es")
    void tipoYPesoAcotados() {
        assertThatThrownBy(() -> servicio.cambiar(new CambiarFotoDelGrupoCommand(ADMIN, FENIX, SUBIDA, "image/gif")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPEG o PNG");
        assertThatThrownBy(() -> servicio.cambiar(
                new CambiarFotoDelGrupoCommand(ADMIN, FENIX, new byte[2 * 1024 * 1024 + 1], "image/jpeg")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 MB");
        verifyNoInteractions(prepararFoto, almacenamiento, fotoDelGrupoPort);
    }

    @Test
    @DisplayName("dos cambios en el mismo milisegundo dan la misma clave: esa no se borra, porque es la nueva")
    void laMismaClaveNoSeBorra() {
        String misma = "grupos/" + FENIX + "/foto-" + AHORA.toEpochMilli() + ".jpg";
        when(fotoDelGrupoPort.reemplazar(eq(FENIX), any())).thenReturn(Optional.of(misma));

        servicio.cambiar(new CambiarFotoDelGrupoCommand(ADMIN, FENIX, SUBIDA, "image/jpeg"));

        verify(almacenamiento, never()).borrar(anyString());
    }

    @Test
    @DisplayName("si no se puede borrar la anterior, el cambio igual queda hecho")
    void borrarLaAnteriorNoHaceFallarElCambio() {
        when(fotoDelGrupoPort.reemplazar(eq(FENIX), any())).thenReturn(Optional.of("grupos/vieja.jpg"));
        doThrow(new IllegalStateException("S3 caído")).when(almacenamiento).borrar("grupos/vieja.jpg");

        assertThat(servicio.cambiar(new CambiarFotoDelGrupoCommand(ADMIN, FENIX, SUBIDA, "image/jpeg"))).isNotNull();
    }

    // ── Volver a la de Renaser y leerla ─────────────────────────────────────

    @Test
    @DisplayName("volver a la foto de Renaser quita la referencia y borra el objeto; sin foto propia no borra nada")
    void volverALaDeRenaser() {
        when(fotoDelGrupoPort.quitar(FENIX)).thenReturn(Optional.of("grupos/actual.jpg")).thenReturn(Optional.empty());

        servicio.volverALaDeRenaser(MENTOR_DE_FENIX, FENIX);
        servicio.volverALaDeRenaser(MENTOR_DE_FENIX, FENIX);

        verify(almacenamiento).borrar("grupos/actual.jpg");
    }

    @Test
    @DisplayName("el chat la lee del almacenamiento; si el objeto no está, es como si no tuviera")
    void elChatLaLee() {
        FotoDelGrupo actual = new FotoDelGrupo("grupos/actual.jpg", AHORA);
        when(fotoDelGrupoPort.deGrupo(FENIX)).thenReturn(Optional.of(actual));
        when(almacenamiento.leer(eq("grupos/actual.jpg"), anyLong())).thenReturn(Optional.of(PREPARADA)).thenReturn(Optional.empty());

        assertThat(servicio.fotoDe(FENIX.value())).hasValueSatisfying(foto -> {
            assertThat(foto.jpeg()).isSameAs(PREPARADA);
            assertThat(foto.cambiadaEn()).isEqualTo(AHORA);
        });
        assertThat(servicio.fotoDe(FENIX.value())).isEmpty();
    }

    @Test
    @DisplayName("para la lista de chats: cuándo cambió la de cada grupo, en una consulta; los de Renaser no figuran")
    void cuandoCambioLaDeCadaGrupo() {
        when(fotoDelGrupoPort.deGrupos(List.of(FENIX, AURORA)))
                .thenReturn(Map.of(FENIX, new FotoDelGrupo("grupos/actual.jpg", AHORA)));

        assertThat(servicio.cambiadasEn(List.of(FENIX.value(), AURORA.value())))
                .containsExactly(Map.entry(FENIX.value(), AHORA));
    }

    @Test
    @DisplayName("quien puede cambiarla ve si hay una y desde cuándo")
    void laActual() {
        when(fotoDelGrupoPort.deGrupo(FENIX)).thenReturn(Optional.of(new FotoDelGrupo("grupos/actual.jpg", AHORA)));

        assertThat(servicio.actual(ADMIN, FENIX)).map(FotoDelGrupo::cambiadaEn).contains(AHORA);
    }

    private void grupo(CelulaId id) {
        lenient().when(loadCelulaPort.porId(id)).thenReturn(Optional.of(
                Celula.crear(id, "Grupo", CohorteId.of(UUID.randomUUID()), null, AHORA)));
    }

    private void cuenta(UserId id, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Alguien", null, rol, estado)));
    }

    private static AsignacionCelula asignacion(CelulaId grupo, UserId usuario, FuncionAcompanamiento funcion) {
        return AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), grupo, usuario, funcion,
                AHORA.minusSeconds(3600), MotivoAsignacion.ADMINISTRATIVO, null, UUID.randomUUID().toString());
    }
}
