package com.renaser.os.community.application.services;

import com.renaser.os.community.api.CelulaCreadaEvent;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.SaveCohortePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.GrupoDePrueba;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.CuentasDePruebaFinder;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** El «Grupo de prueba (ver como)» (D-270): solo cuentas de prueba, como el sistema, idempotente. Sin Spring. */
@ExtendWith(MockitoExtension.class)
class GrupoDePruebaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @Mock
    private LoadCohortePort loadCohorte;
    @Mock
    private SaveCohortePort saveCohorte;
    @Mock
    private LoadCelulaPort loadCelula;
    @Mock
    private SaveCelulaPort saveCelula;
    @Mock
    private LoadAsignacionesPort loadAsignaciones;
    @Mock
    private SaveAsignacionPort saveAsignacion;
    @Mock
    private AsignacionCelulaPort punteros;
    @Mock
    private UserSummaryFinder usuarios;
    @Mock
    private CuentasDePruebaFinder cuentasDePrueba;
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private IdGenerator idGenerator;

    private GrupoDePruebaService servicio;
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UserId guia = UserId.of(UUID.randomUUID());
    /** Lo guardado, para que las lecturas siguientes lo vean (como la base). */
    private final List<AsignacionCelula> asignaciones = new ArrayList<>();

    @BeforeEach
    void armar() {
        servicio = new GrupoDePruebaService(loadCohorte, saveCohorte, loadCelula, saveCelula, loadAsignaciones,
                saveAsignacion, punteros, usuarios, cuentasDePrueba, eventos, idGenerator, FixedClock.at(AHORA));
        lenient().when(usuarios.findById(soporte)).thenReturn(Optional.of(new UserSummary(soporte, "Soporte", null,
                UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        lenient().when(cuentasDePrueba.entre(anyCollection())).thenAnswer(i -> Set.copyOf(i.getArgument(0)));
        lenient().when(idGenerator.newId()).thenAnswer(i -> UUID.randomUUID());
        lenient().when(saveCelula.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(saveAsignacion.save(any())).thenAnswer(i -> {
            asignaciones.removeIf(a -> a.id().equals(((AsignacionCelula) i.getArgument(0)).id()));
            asignaciones.add(i.getArgument(0));
            return i.getArgument(0);
        });
        lenient().when(loadAsignaciones.porCelula(GrupoDePrueba.CELULA))
                .thenAnswer(i -> asignaciones.stream().filter(a -> a.celulaId().equals(GrupoDePrueba.CELULA)).toList());
        lenient().when(loadAsignaciones.porUsuario(any()))
                .thenAnswer(i -> asignaciones.stream().filter(a -> a.usuarioId().equals(i.getArgument(0))).toList());
    }

    @Test
    @DisplayName("la primera vez crea la cohorte y el grupo REGULAR sin período, con mentor, aprendiz y guía")
    void laPrimeraVez() {
        when(loadCohorte.porId(GrupoDePrueba.COHORTE)).thenReturn(Optional.empty());
        when(loadCelula.porId(GrupoDePrueba.CELULA)).thenReturn(Optional.empty());

        UUID grupo = servicio.asegurar(soporte, mentor, List.of(aprendiz), List.of(guia));

        assertThat(grupo).isEqualTo(GrupoDePrueba.CELULA.value());
        ArgumentCaptor<Cohorte> cohorte = ArgumentCaptor.forClass(Cohorte.class);
        verify(saveCohorte).save(cohorte.capture());
        assertThat(cohorte.getValue().nombre()).isEqualTo(GrupoDePrueba.NOMBRE_DE_LA_COHORTE);
        ArgumentCaptor<Celula> celulas = ArgumentCaptor.forClass(Celula.class);
        verify(saveCelula, org.mockito.Mockito.atLeastOnce()).save(celulas.capture());
        Celula guardada = celulas.getAllValues().getLast();
        assertThat(guardada.nombre()).isEqualTo(GrupoDePrueba.NOMBRE_DEL_GRUPO);
        assertThat(guardada.esRecepcion()).as("nunca recepción: no recibe a quien se registra").isFalse();
        assertThat(guardada.tienePeriodo()).as("sin período: nunca vence").isFalse();
        assertThat(guardada.mentorId()).isEqualTo(mentor);
        assertThat(asignaciones).extracting(AsignacionCelula::funcion).containsExactlyInAnyOrder(
                FuncionAcompanamiento.MENTOR, FuncionAcompanamiento.APRENDIZ, FuncionAcompanamiento.GUIA);
        assertThat(asignaciones).allMatch(AsignacionCelula::ejecutadaPorSistema);
        verify(punteros).sincronizarAcompanamiento(aprendiz, GrupoDePrueba.CELULA.value(), mentor);
        verify(eventos).publishEvent(any(CelulaCreadaEvent.class));
        verify(eventos).publishEvent(any(ComposicionDeCelulaCambiadaEvent.class));
    }

    @Test
    @DisplayName("la segunda vez no cambia nada ni avisa: es idempotente")
    void laSegundaVez() {
        Celula grupo = Celula.crear(GrupoDePrueba.CELULA, GrupoDePrueba.NOMBRE_DEL_GRUPO, GrupoDePrueba.COHORTE, null,
                null, AHORA);
        when(loadCohorte.porId(GrupoDePrueba.COHORTE)).thenReturn(Optional.of(Cohorte.crear(GrupoDePrueba.COHORTE,
                GrupoDePrueba.NOMBRE_DE_LA_COHORTE, java.time.LocalDate.parse("2026-10-14"), null, AHORA)));
        when(loadCelula.porId(GrupoDePrueba.CELULA)).thenReturn(Optional.of(grupo));
        servicio.asegurar(soporte, mentor, List.of(aprendiz), List.of(guia));
        org.mockito.Mockito.clearInvocations(eventos, saveAsignacion, saveCelula);

        servicio.asegurar(soporte, mentor, List.of(aprendiz), List.of(guia));

        verify(saveAsignacion, never()).save(any());
        verify(saveCelula, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("nunca toca a una persona real: si alguna no es cuenta de prueba, 400 sin escribir nada")
    void soloCuentasDePrueba() {
        UserId real = UserId.of(UUID.randomUUID());
        when(cuentasDePrueba.entre(anyCollection())).thenReturn(Set.of(mentor, guia));

        assertThatThrownBy(() -> servicio.asegurar(soporte, mentor, List.of(real), List.of(guia)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(saveCohorte, saveCelula, saveAsignacion, punteros, eventos);
    }

    @Test
    @DisplayName("solo lo pide Soporte TI activo (403)")
    void soloSoporteTi() {
        UserId admin = UserId.of(UUID.randomUUID());
        when(usuarios.findById(admin)).thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN,
                UserStatus.ACTIVE)));

        assertThatThrownBy(() -> servicio.asegurar(admin, mentor, List.of(aprendiz), List.of(guia)))
                .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(saveCohorte, saveCelula, saveAsignacion);
    }

    @Test
    @DisplayName("si alguien cambió el mentor del grupo a mano, el de prueba vuelve a liderarlo")
    void reparaElMentor() {
        UserId otroMentor = UserId.of(UUID.randomUUID());
        Celula grupo = Celula.crear(GrupoDePrueba.CELULA, GrupoDePrueba.NOMBRE_DEL_GRUPO, GrupoDePrueba.COHORTE, null,
                null, AHORA);
        grupo.asignarMentor(otroMentor, AHORA);
        asignaciones.add(AsignacionCelula.abrir(com.renaser.os.community.domain.model.acompanamiento.AsignacionId.of(
                        UUID.randomUUID()), GrupoDePrueba.CELULA, otroMentor, FuncionAcompanamiento.MENTOR,
                AHORA.minusSeconds(3600), com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion
                        .ADMINISTRATIVO, null, "otra"));
        when(loadCohorte.porId(GrupoDePrueba.COHORTE)).thenReturn(Optional.of(Cohorte.crear(GrupoDePrueba.COHORTE,
                GrupoDePrueba.NOMBRE_DE_LA_COHORTE, java.time.LocalDate.parse("2026-10-14"), null, AHORA)));
        when(loadCelula.porId(GrupoDePrueba.CELULA)).thenReturn(Optional.of(grupo));

        servicio.asegurar(soporte, mentor, List.of(aprendiz), List.of(guia));

        assertThat(grupo.mentorId()).isEqualTo(mentor);
        assertThat(asignaciones.stream().filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR && a.vigenteEn(AHORA))
                .map(AsignacionCelula::usuarioId).toList()).containsExactly(mentor);
    }

    @Test
    @DisplayName("el id del grupo y de la cohorte son fijos: así las listas los dejan afuera")
    void idsFijos() {
        assertThat(GrupoDePrueba.CELULA).isEqualTo(CelulaId.of(UUID.fromString("7e57c0de-0000-4000-8000-000000000002")));
        assertThat(GrupoDePrueba.COHORTE.value()).isEqualTo(UUID.fromString("7e57c0de-0000-4000-8000-000000000001"));
    }
}
