package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.categoria.CategoriaDelCatalogo;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.ActualizarCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.CambiarActivaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.CrearCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.EliminarCategoriaCommand;
import com.renaser.os.habits.application.ports.in.categoria.GestionarCategoriasUseCase.OrdenarCategoriasCommand;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort.ConteoDeHabitos;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.Accion;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.CambioDelCatalogo;
import com.renaser.os.habits.application.ports.out.categoria.SaveCategoriaHabitoPort;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 023, historia 2: Administración configura las categorías de cada formación. */
@ExtendWith(MockitoExtension.class)
class CategoriasDeHabitoAdminServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-31T15:00:00Z"));

    @Mock
    private LoadCategoriaHabitoPort loadPort;
    @Mock
    private SaveCategoriaHabitoPort savePort;
    @Mock
    private RegistrarCambioDelCatalogoPort auditoria;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private CategoriasDeHabitoAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alquimista = UserId.of(UUID.randomUUID());
    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void preparar() {
        service = new CategoriasDeHabitoAdminService(loadPort, savePort, auditoria, new HabitoAdminGuard(userSummaryFinder), CLOCK);
        quien(admin, UserRole.ADMIN, UserStatus.ACTIVE);
        quien(alquimista, UserRole.ALCHEMIST, UserStatus.ACTIVE);
        quien(lider, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        quien(suspendido, UserRole.ADMIN, UserStatus.SUSPENDED);
        lenient().when(loadPort.contarHabitos(any())).thenReturn(new ConteoDeHabitos(0, 0));
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void quien(UserId id, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Alguien", null, rol, estado)));
    }

    private static CategoriaHabito categoria(String clave, Formacion formacion, int orden, boolean activa) {
        return CategoriaHabito.rehydrate(clave, formacion, "Nombre " + clave, orden, "heart", activa, CLOCK.now(), CLOCK.now());
    }

    private CategoriaHabito existente(String clave, Formacion formacion) {
        CategoriaHabito c = categoria(clave, formacion, 1, true);
        when(loadPort.byClave(clave)).thenReturn(Optional.of(c));
        return c;
    }

    // ── Quién puede ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("un líder de mentores, una cuenta suspendida o un desconocido no tocan el catálogo (403, sin escribir nada)")
    void soloAdminYAlquimista() {
        assertThatThrownBy(() -> service.crear(new CrearCategoriaCommand(lider, Formacion.DIPLOMADO, "Estudio", null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.crear(new CrearCategoriaCommand(suspendido, Formacion.DIPLOMADO, "Estudio", null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.listar(lider, Formacion.DIPLOMADO)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.eliminar(new EliminarCategoriaCommand(lider, "X_Y_Z"))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.ordenar(new OrdenarCategoriasCommand(lider, Formacion.ESCUELA, List.of())))
                .isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
        verify(savePort, never()).eliminar(any());
        verify(auditoria, never()).registrar(any());
    }

    // ── Crear ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear una categoría en el Diplomado: clave con la formación, al final del orden, activa y con constancia")
    void crear() {
        when(loadPort.existeNombreEn(Formacion.DIPLOMADO, "Estudio profundo", null)).thenReturn(false);
        when(loadPort.existeClave("DIPLOMADO_ESTUDIO_PROFUNDO")).thenReturn(false);
        when(loadPort.siguienteOrden(Formacion.DIPLOMADO)).thenReturn(3);

        CategoriaDelCatalogo creada = service.crear(new CrearCategoriaCommand(alquimista, Formacion.DIPLOMADO, "Estudio profundo", "bookOpen"));

        assertThat(creada.categoria().clave()).isEqualTo("DIPLOMADO_ESTUDIO_PROFUNDO");
        assertThat(creada.categoria().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(creada.categoria().orden()).isEqualTo(3);
        assertThat(creada.categoria().activa()).isTrue();
        assertThat(creada.categoria().icono()).isEqualTo("bookOpen");
        ArgumentCaptor<CambioDelCatalogo> cambio = ArgumentCaptor.forClass(CambioDelCatalogo.class);
        verify(auditoria).registrar(cambio.capture());
        assertThat(cambio.getValue().accion()).isEqualTo(Accion.CREADA);
        assertThat(cambio.getValue().actor()).isEqualTo(alquimista);
        assertThat(cambio.getValue().formacion()).isEqualTo(Formacion.DIPLOMADO);
    }

    @Test
    @DisplayName("un nombre que ya existe en la MISMA formación se rechaza con su motivo; el mismo nombre en otra, no")
    void nombreRepetido() {
        when(loadPort.existeNombreEn(Formacion.DIPLOMADO, "Mente", null)).thenReturn(true);
        when(loadPort.existeNombreEn(Formacion.CERTIFICACION, "Mente", null)).thenReturn(false);
        when(loadPort.existeClave("CERTIFICACION_MENTE")).thenReturn(false);
        when(loadPort.siguienteOrden(Formacion.CERTIFICACION)).thenReturn(1);

        assertThatThrownBy(() -> service.crear(new CrearCategoriaCommand(admin, Formacion.DIPLOMADO, "Mente", null)))
                .isInstanceOf(IllegalStateException.class).hasMessage(CategoriasDeHabitoAdminService.NOMBRE_REPETIDO);
        assertThat(service.crear(new CrearCategoriaCommand(admin, Formacion.CERTIFICACION, "Mente", null)).categoria().clave())
                .isEqualTo("CERTIFICACION_MENTE");
    }

    @Test
    @DisplayName("si la clave ya existe se prueba con un sufijo numérico")
    void claveOcupada() {
        when(loadPort.existeNombreEn(any(), any(), any())).thenReturn(false);
        when(loadPort.existeClave("ESCUELA_FE")).thenReturn(true);
        when(loadPort.existeClave("ESCUELA_FE_2")).thenReturn(true);
        when(loadPort.existeClave("ESCUELA_FE_3")).thenReturn(false);
        when(loadPort.siguienteOrden(Formacion.ESCUELA)).thenReturn(5);

        assertThat(service.crear(new CrearCategoriaCommand(admin, Formacion.ESCUELA, "Fe", null)).categoria().clave())
                .isEqualTo("ESCUELA_FE_3");
    }

    @Test
    @DisplayName("un nombre fuera de rango o un ícono que la app no dibuja se rechazan sin guardar")
    void datosInvalidos() {
        when(loadPort.existeNombreEn(any(), any(), any())).thenReturn(false);
        when(loadPort.existeClave(any())).thenReturn(false);

        assertThatThrownBy(() -> service.crear(new CrearCategoriaCommand(admin, Formacion.ESCUELA, "A", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear(new CrearCategoriaCommand(admin, Formacion.ESCUELA, "Estudio", "inventado")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(savePort, never()).save(any());
    }

    // ── Renombrar / ícono ───────────────────────────────────────────────────

    @Test
    @DisplayName("renombrar y cambiar el ícono dejan constancia de lo de antes y lo de después")
    void actualizar() {
        existente("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO);
        when(loadPort.existeNombreEn(Formacion.DIPLOMADO, "Lectura", "DIPLOMADO_ESTUDIO")).thenReturn(false);

        CategoriaDelCatalogo actualizada = service.actualizar(
                new ActualizarCategoriaCommand(admin, "DIPLOMADO_ESTUDIO", "Lectura", "brain", true));

        assertThat(actualizada.categoria().nombre()).isEqualTo("Lectura");
        assertThat(actualizada.categoria().icono()).isEqualTo("brain");
        ArgumentCaptor<CambioDelCatalogo> cambios = ArgumentCaptor.forClass(CambioDelCatalogo.class);
        verify(auditoria, org.mockito.Mockito.times(2)).registrar(cambios.capture());
        assertThat(cambios.getAllValues()).extracting(CambioDelCatalogo::accion).containsExactly(Accion.RENOMBRADA, Accion.ICONO);
        assertThat(cambios.getAllValues().get(0).detalle()).containsEntry("antes", "Nombre DIPLOMADO_ESTUDIO")
                .containsEntry("despues", "Lectura");
    }

    @Test
    @DisplayName("renombrar a un nombre de otra categoría de la formación se rechaza; sin cambiar nada no hay constancia")
    void renombrarRepetido() {
        existente("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO);
        when(loadPort.existeNombreEn(Formacion.DIPLOMADO, "Mente", "DIPLOMADO_ESTUDIO")).thenReturn(true);

        assertThatThrownBy(() -> service.actualizar(new ActualizarCategoriaCommand(admin, "DIPLOMADO_ESTUDIO", "Mente", null, false)))
                .isInstanceOf(IllegalStateException.class);
        service.actualizar(new ActualizarCategoriaCommand(admin, "DIPLOMADO_ESTUDIO", null, null, false));
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("una categoría que no existe es un 404")
    void inexistente() {
        when(loadPort.byClave("NO_EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(new ActualizarCategoriaCommand(admin, "NO_EXISTE", "Algo", null, false)))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── Desactivar / eliminar ───────────────────────────────────────────────

    @Test
    @DisplayName("una categoría con hábitos activos no se desactiva (SC-009); sin ellos, sí")
    void desactivar() {
        existente("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO);
        when(loadPort.contarHabitos("DIPLOMADO_ESTUDIO")).thenReturn(new ConteoDeHabitos(2, 3));

        assertThatThrownBy(() -> service.cambiarActiva(new CambiarActivaCommand(admin, "DIPLOMADO_ESTUDIO", false)))
                .isInstanceOf(IllegalStateException.class).hasMessage(CategoriasDeHabitoAdminService.CON_HABITOS_ACTIVOS);

        when(loadPort.contarHabitos("DIPLOMADO_ESTUDIO")).thenReturn(new ConteoDeHabitos(0, 3));
        assertThat(service.cambiarActiva(new CambiarActivaCommand(admin, "DIPLOMADO_ESTUDIO", false)).categoria().activa()).isFalse();
    }

    @Test
    @DisplayName("reactivar una categoría no pide nada y deja constancia")
    void reactivar() {
        CategoriaHabito c = existente("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO);
        c.desactivar(CLOCK.now());

        assertThat(service.cambiarActiva(new CambiarActivaCommand(admin, "DIPLOMADO_ESTUDIO", true)).categoria().activa()).isTrue();
        ArgumentCaptor<CambioDelCatalogo> cambio = ArgumentCaptor.forClass(CambioDelCatalogo.class);
        verify(auditoria).registrar(cambio.capture());
        assertThat(cambio.getValue().accion()).isEqualTo(Accion.ACTIVADA);
    }

    @Test
    @DisplayName("eliminar: con hábitos activos o con historial se rechaza con su motivo; vacía, se borra")
    void eliminar() {
        CategoriaHabito c = existente("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO);
        when(loadPort.contarHabitos("DIPLOMADO_ESTUDIO")).thenReturn(new ConteoDeHabitos(1, 1));
        assertThatThrownBy(() -> service.eliminar(new EliminarCategoriaCommand(admin, "DIPLOMADO_ESTUDIO")))
                .hasMessage(CategoriasDeHabitoAdminService.CON_HABITOS_ACTIVOS);

        when(loadPort.contarHabitos("DIPLOMADO_ESTUDIO")).thenReturn(new ConteoDeHabitos(0, 4));
        assertThatThrownBy(() -> service.eliminar(new EliminarCategoriaCommand(admin, "DIPLOMADO_ESTUDIO")))
                .hasMessage(CategoriasDeHabitoAdminService.CON_HISTORIAL);
        verify(savePort, never()).eliminar(any());

        when(loadPort.contarHabitos("DIPLOMADO_ESTUDIO")).thenReturn(new ConteoDeHabitos(0, 0));
        service.eliminar(new EliminarCategoriaCommand(admin, "DIPLOMADO_ESTUDIO"));
        verify(savePort).eliminar(c.clave());
    }

    // ── Ordenar ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ordenar reasigna 1..n en el orden pedido y solo guarda las que cambiaron")
    void ordenar() {
        CategoriaHabito a = categoria("D_A", Formacion.DIPLOMADO, 1, true);
        CategoriaHabito b = categoria("D_B", Formacion.DIPLOMADO, 2, true);
        CategoriaHabito c = categoria("D_C", Formacion.DIPLOMADO, 3, true);
        when(loadPort.deFormacion(Formacion.DIPLOMADO)).thenReturn(List.of(a, b, c));

        List<CategoriaDelCatalogo> resultado = service.ordenar(
                new OrdenarCategoriasCommand(admin, Formacion.DIPLOMADO, List.of("D_C", "D_A", "D_B")));

        assertThat(resultado).extracting(r -> r.categoria().clave()).containsExactly("D_C", "D_A", "D_B");
        assertThat(c.orden()).isEqualTo(1);
        assertThat(a.orden()).isEqualTo(2);
        assertThat(b.orden()).isEqualTo(3);
        verify(savePort, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    @DisplayName("el orden debe traer todas las categorías de la formación, una vez cada una (no se cuela una de otra formación)")
    void ordenIncompleto() {
        when(loadPort.deFormacion(Formacion.DIPLOMADO)).thenReturn(List.of(categoria("D_A", Formacion.DIPLOMADO, 1, true),
                categoria("D_B", Formacion.DIPLOMADO, 2, true)));

        assertThatThrownBy(() -> service.ordenar(new OrdenarCategoriasCommand(admin, Formacion.DIPLOMADO, List.of("D_A"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ordenar(new OrdenarCategoriasCommand(admin, Formacion.DIPLOMADO, List.of("D_A", "CUERPO"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ordenar(new OrdenarCategoriasCommand(admin, Formacion.DIPLOMADO, List.of("D_A", "D_A"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(savePort, never()).save(any());
    }

    // ── Listar ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listar una formación trae solo las suyas; «todas» (nula) trae las de las tres, cada una con su conteo")
    void listar() {
        when(loadPort.deFormacion(Formacion.ESCUELA)).thenReturn(List.of(categoria("CUERPO", Formacion.ESCUELA, 1, true)));
        when(loadPort.deFormacion(Formacion.DIPLOMADO)).thenReturn(List.of());
        when(loadPort.deFormacion(Formacion.CERTIFICACION)).thenReturn(List.of(categoria("C_A", Formacion.CERTIFICACION, 1, false)));
        when(loadPort.contarHabitos("CUERPO")).thenReturn(new ConteoDeHabitos(5, 6));

        assertThat(service.listar(admin, Formacion.DIPLOMADO)).isEmpty();
        List<CategoriaDelCatalogo> todas = service.listar(admin, null);
        assertThat(todas).extracting(c -> c.categoria().clave()).containsExactly("CUERPO", "C_A");
        assertThat(todas.get(0).habitosActivos()).isEqualTo(5);
        assertThat(todas.get(0).puedeEliminarse()).isFalse();
        assertThat(todas.get(0).puedeDesactivarse()).isFalse();
        assertThat(todas.get(1).puedeEliminarse()).isTrue();
    }
}
