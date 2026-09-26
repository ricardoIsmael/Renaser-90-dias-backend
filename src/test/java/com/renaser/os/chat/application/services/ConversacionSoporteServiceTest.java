package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase.ResultadoRelleno;
import com.renaser.os.chat.application.ports.in.conversacion.SalirDeConversacionSoporteUseCase.SalirDeConversacionSoporteCommand;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El chat de soporte por aprendiz (D-136).
 *
 * <p>Las reglas que se prueban son las cinco que confirmo el dueño del proyecto: una conversacion
 * por aprendiz con todo el staff administrativo adentro, creada al ENTRAR AL PROGRAMA y no al
 * registrarse, el staff nuevo sumandose a las que ya existen, el staff pudiendo irse y el aprendiz
 * no, y el relleno de los que ya estaban — sin una consulta por persona.
 *
 * <p>Sin Spring y sin Postgres: {@code PlatformTransactionManager} sin stubbing hace que
 * {@code TransactionTemplate} ejecute el callback directo, mismo criterio que
 * {@code ConversacionServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class ConversacionSoporteServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-16T10:00:00Z"));
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000009");

    private static final UserId ANA = UserId.of(UUID.fromString("11111111-1111-4111-8111-111111111111"));
    private static final UserId BRUNO = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final UserId ADMINA = UserId.of(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));
    private static final UserId ALQUIMISTA = UserId.of(UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"));
    private static final UserId MENTOR = UserId.of(UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"));

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private SaveConversacionPort saveConversacionPort;
    @Mock
    private AgregarParticipantePort agregarParticipantePort;
    @Mock
    private QuitarParticipantePort quitarParticipantePort;
    @Mock
    private EsParticipantePort esParticipantePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ParticipacionProgramaFinder participacionProgramaFinder;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventos;

    private ConversacionSoporteService service;

    @BeforeEach
    void preparar() {
        service = new ConversacionSoporteService(loadConversacionPort, saveConversacionPort,
                agregarParticipantePort, quitarParticipantePort, esParticipantePort, userSummaryFinder,
                participacionProgramaFinder, CLOCK, idGenerator, transactionManager, eventos);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(saveConversacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── 1 y 2. Nace sola al entrar al programa, con el aprendiz y todo el staff ──────────────

    @Test
    @DisplayName("un aprendiz inscrito recibe su soporte con el aprendiz y TODO el staff administrativo")
    void elAprendizInscritoRecibeSuSoporteConTodoElStaff() {
        inscrito(ANA, UserRole.TRAINEE);
        perfil(ANA, "Ana Perez", UserRole.TRAINEE, UserStatus.ACTIVE);
        when(loadConversacionPort.porClaveDirecta(Conversacion.claveSoporteDe(ANA))).thenReturn(Optional.empty());
        when(participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(ADMINA, ALQUIMISTA));

        service.incorporar(ANA);

        var guardada = org.mockito.ArgumentCaptor.forClass(Conversacion.class);
        verify(saveConversacionPort).save(guardada.capture());
        assertThat(guardada.getValue().tipo()).isEqualTo(TipoConversacion.SOPORTE);
        assertThat(guardada.getValue().claveDirecta()).isEqualTo("soporte:" + ANA.value());
        assertThat(guardada.getValue().nombre()).isEqualTo("Ana – Formación Renaser");
        // El aprendiz + los dos del staff, nadie mas. El mentor NO entra.
        verify(agregarParticipantePort, times(3)).agregar(any());
        assertThat(participantesAgregados()).containsExactlyInAnyOrder(ANA, ADMINA, ALQUIMISTA);
        // D-174: avisa que nacio, para que salga la bienvenida.
        verify(eventos).publishEvent(new com.renaser.os.chat.domain.model.conversacion.SoporteDeAprendizNacioEvent(
                ConversacionId.of(ID_GENERADO), ANA));
    }

    /** D-174: si otro camino lo creo primero, la bienvenida la dispara ese, no este. */
    @Test
    @DisplayName("si pierde la carrera de creacion, no avisa: la bienvenida sale una sola vez")
    void siPierdeLaCarreraNoAvisa() {
        inscrito(ANA, UserRole.TRAINEE);
        perfil(ANA, "Ana Perez", UserRole.TRAINEE, UserStatus.ACTIVE);
        when(loadConversacionPort.porClaveDirecta(Conversacion.claveSoporteDe(ANA))).thenReturn(Optional.empty());
        when(saveConversacionPort.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("clave_directa duplicada"));

        service.incorporar(ANA);

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    /** D-173: el formato del procedimiento de Operaciones, con el primer nombre solo. */
    @Test
    @DisplayName("el soporte se nombra con el primer nombre: 'María – Formación Renaser'")
    void elNombreLlevaSoloElPrimerNombre() {
        assertThat(ConversacionSoporteService.nombreDeSoporte("María José Ñahui Quispe")).isEqualTo("María – Formación Renaser");
        assertThat(ConversacionSoporteService.nombreDeSoporte("  Luis   Gomez ")).isEqualTo("Luis – Formación Renaser");
        assertThat(ConversacionSoporteService.nombreDeSoporte("Ana")).isEqualTo("Ana – Formación Renaser");
    }

    @Test
    @DisplayName("sin nombre legible, el soporte igual nace con un título genérico")
    void sinNombreQuedaElTituloGenerico() {
        assertThat(ConversacionSoporteService.nombreDeSoporte(null)).isEqualTo("Formación Renaser");
        assertThat(ConversacionSoporteService.nombreDeSoporte("   ")).isEqualTo("Formación Renaser");
    }

    /**
     * La regla que separa "entro al programa" de "se registro": sin fila en
     * `participantes_programa` todavia no entro, y no se le crea nada.
     */
    @Test
    @DisplayName("un aprendiz SIN participacion todavia no entro al programa: no se le crea nada")
    void unAprendizSinParticipacionNoRecibeSoporte() {
        when(participacionProgramaFinder.deParticipante(ANA))
                .thenReturn(Optional.of(participacion(ANA, UserRole.TRAINEE, false, false)));

        service.incorporar(ANA);

        verify(saveConversacionPort, never()).save(any());
        verify(agregarParticipantePort, never()).agregar(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("un usuario suspendido no entra a ningun soporte, tenga el rol que tenga")
    void unUsuarioSuspendidoNoEntraANingunSoporte() {
        when(participacionProgramaFinder.deParticipante(ANA))
                .thenReturn(Optional.of(participacion(ANA, UserRole.TRAINEE, true, true)));

        service.incorporar(ANA);

        verify(saveConversacionPort, never()).save(any());
        verify(loadConversacionPort, never()).deSoporte();
    }

    @Test
    @DisplayName("crear el soporte es idempotente: si ya existe, no se crea un segundo")
    void crearElSoporteEsIdempotente() {
        inscrito(ANA, UserRole.TRAINEE);
        when(loadConversacionPort.porClaveDirecta(Conversacion.claveSoporteDe(ANA)))
                .thenReturn(Optional.of(soporteDe(ANA)));

        service.incorporar(ANA);

        verify(saveConversacionPort, never()).save(any());
        verify(agregarParticipantePort, never()).agregar(any());
    }

    @Test
    @DisplayName("un mentor no genera soporte propio ni se suma a los ajenos")
    void unMentorNoParticipaDelCircuitoDeSoporte() {
        when(participacionProgramaFinder.deParticipante(MENTOR))
                .thenReturn(Optional.of(participacion(MENTOR, UserRole.MENTOR, false, false)));

        service.incorporar(MENTOR);

        verify(saveConversacionPort, never()).save(any());
        verify(loadConversacionPort, never()).deSoporte();
        verify(agregarParticipantePort, never()).agregar(any());
    }

    // ── 3. El staff nuevo entra a las conversaciones que ya existen ──────────────────────────

    @Test
    @DisplayName("un ADMIN nuevo entra a todos los soportes que ya existian")
    void unAdminNuevoEntraATodosLosSoportesQueYaExistian() {
        when(participacionProgramaFinder.deParticipante(ADMINA))
                .thenReturn(Optional.of(participacion(ADMINA, UserRole.ADMIN, false, false)));
        Conversacion deAna = soporteDe(ANA);
        Conversacion deBruno = soporteDe(BRUNO);
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(deAna, deBruno));
        when(esParticipantePort.esParticipante(any(), eq(ADMINA))).thenReturn(false);

        service.incorporar(ADMINA);

        assertThat(participantesAgregados()).containsExactly(ADMINA, ADMINA);
        verify(agregarParticipantePort, times(2)).agregar(any());
    }

    @Test
    @DisplayName("un ALQUIMISTA tambien es staff administrativo")
    void unAlquimistaTambienEsStaffAdministrativo() {
        when(participacionProgramaFinder.deParticipante(ALQUIMISTA))
                .thenReturn(Optional.of(participacion(ALQUIMISTA, UserRole.ALCHEMIST, false, false)));
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(soporteDe(ANA)));
        when(esParticipantePort.esParticipante(any(), eq(ALQUIMISTA))).thenReturn(false);

        service.incorporar(ALQUIMISTA);

        verify(agregarParticipantePort, times(1)).agregar(any());
    }

    /** Reentregar el evento (el outbox entrega al-menos-una-vez) no puede duplicar nada ni pisar
     * cuanto leyo el administrador. */
    @Test
    @DisplayName("sumar staff es idempotente: a quien ya esta adentro no se lo agrega de nuevo")
    void sumarStaffEsIdempotente() {
        when(participacionProgramaFinder.deParticipante(ADMINA))
                .thenReturn(Optional.of(participacion(ADMINA, UserRole.ADMIN, false, false)));
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(soporteDe(ANA)));
        when(esParticipantePort.esParticipante(any(), eq(ADMINA))).thenReturn(true);

        service.incorporar(ADMINA);

        verify(agregarParticipantePort, never()).agregar(any());
    }

    // ── 4. El staff se va; el aprendiz no ───────────────────────────────────────────────────

    @Test
    @DisplayName("un miembro del staff puede salirse de un chat de soporte")
    void elStaffPuedeSalirseDeUnChatDeSoporte() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        Conversacion soporte = soporteDe(ANA);
        when(loadConversacionPort.porId(soporte.id())).thenReturn(Optional.of(soporte));
        when(esParticipantePort.esParticipante(soporte.id(), ADMINA)).thenReturn(true);

        service.salir(new SalirDeConversacionSoporteCommand(ADMINA, soporte.id()));

        verify(quitarParticipantePort).quitar(soporte.id(), ADMINA);
    }

    @Test
    @DisplayName("el aprendiz NO puede salirse de su propio chat de soporte")
    void elAprendizNoPuedeSalirseDeSuPropioSoporte() {
        perfil(ANA, "Ana Perez", UserRole.TRAINEE, UserStatus.ACTIVE);
        Conversacion soporte = soporteDe(ANA);
        when(loadConversacionPort.porId(soporte.id())).thenReturn(Optional.of(soporte));

        assertThatThrownBy(() -> service.salir(new SalirDeConversacionSoporteCommand(ANA, soporte.id())))
                .isInstanceOf(NotAuthorizedException.class);

        verify(quitarParticipantePort, never()).quitar(any(), any());
    }

    @Test
    @DisplayName("salir solo aplica al soporte: de la GLOBAL o de un grupo no se sale por aca")
    void salirNoAplicaAOtrosTiposDeConversacion() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        Conversacion global = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), CLOCK.now());
        when(loadConversacionPort.porId(global.id())).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.salir(new SalirDeConversacionSoporteCommand(ADMINA, global.id())))
                .isInstanceOf(IllegalArgumentException.class);

        verify(quitarParticipantePort, never()).quitar(any(), any());
    }

    @Test
    @DisplayName("quien no es participante no puede sacarse de una conversacion ajena")
    void quienNoEsParticipanteNoPuedeSalir() {
        perfil(MENTOR, "Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        Conversacion soporte = soporteDe(ANA);
        when(loadConversacionPort.porId(soporte.id())).thenReturn(Optional.of(soporte));
        when(esParticipantePort.esParticipante(soporte.id(), MENTOR)).thenReturn(false);

        assertThatThrownBy(() -> service.salir(new SalirDeConversacionSoporteCommand(MENTOR, soporte.id())))
                .isInstanceOf(NotAuthorizedException.class);

        verify(quitarParticipantePort, never()).quitar(any(), any());
    }

    @Test
    @DisplayName("un actor suspendido no puede salirse de nada")
    void unActorSuspendidoNoPuedeSalir() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);
        Conversacion soporte = soporteDe(ANA);

        assertThatThrownBy(() -> service.salir(new SalirDeConversacionSoporteCommand(ADMINA, soporte.id())))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── 4-bis. La baja de rol revoca: excepcion ACOTADA a CH-11 ─────────────────────────────

    /**
     * Regresion de la auditoria de seguridad. <b>Falla contra el codigo viejo</b>: antes no existia
     * ningun camino que sacara a nadie del soporte que no fuera la propia persona pulsando salir,
     * asi que un ex administrador conservaba la fila —y con ella el chat privado— de cada aprendiz.
     */
    @Test
    @DisplayName("bajar del staff lo saca de TODAS las conversaciones de soporte ajenas")
    void laBajaDeStaffLoSacaDeTodosLosSoportesAjenos() {
        perfil(ADMINA, "Ex admin", UserRole.MENTOR, UserStatus.ACTIVE);
        Conversacion deAna = soporteDe(ANA);
        Conversacion deBruno = soporteDe(BRUNO);
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(deAna, deBruno));
        when(esParticipantePort.esParticipante(any(), eq(ADMINA))).thenReturn(true);

        service.retirarPorBajaDeStaff(ADMINA);

        verify(quitarParticipantePort).quitar(deAna.id(), ADMINA);
        verify(quitarParticipantePort).quitar(deBruno.id(), ADMINA);
    }

    /** Regla 4: el aprendiz no sale de la suya ni aunque quiera. Una baja hasta TRAINEE lo vuelve
     * aprendiz, y esa conversacion es su via de contacto con la casa. */
    @Test
    @DisplayName("la baja de rol NO toca la conversacion de soporte propia")
    void laBajaDeStaffNoTocaElSoportePropio() {
        perfil(ANA, "Ana Perez", UserRole.TRAINEE, UserStatus.ACTIVE);
        Conversacion deAna = soporteDe(ANA);
        Conversacion deBruno = soporteDe(BRUNO);
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(deAna, deBruno));
        when(esParticipantePort.esParticipante(any(), eq(ANA))).thenReturn(true);

        service.retirarPorBajaDeStaff(ANA);

        verify(quitarParticipantePort, never()).quitar(eq(deAna.id()), any());
        verify(quitarParticipantePort).quitar(deBruno.id(), ANA);
    }

    /** Corre en el outbox, que entrega al-menos-una-vez: quitar una fila que ya no esta no falla. */
    @Test
    @DisplayName("retirar es idempotente: a quien ya no tiene fila no se le quita nada")
    void retirarEsIdempotente() {
        perfil(ADMINA, "Ex admin", UserRole.MENTOR, UserStatus.ACTIVE);
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(soporteDe(ANA)));
        when(esParticipantePort.esParticipante(any(), eq(ADMINA))).thenReturn(false);

        service.retirarPorBajaDeStaff(ADMINA);

        verify(quitarParticipantePort, never()).quitar(any(), any());
    }

    /**
     * El outbox entrega al-menos-una-vez y SIN ORDEN: un evento de baja viejo puede reentregarse
     * cuando la persona ya volvio al staff, o llegar despues de un ascenso posterior. Decidir
     * contra el rol VIGENTE es lo que impide que eso le borre las filas a un ADMIN legitimo.
     */
    @Test
    @DisplayName("no retira nada si a esta altura la persona volvio a ser staff administrativo")
    void noRetiraSiSigueSiendoStaff() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);

        service.retirarPorBajaDeStaff(ADMINA);

        verify(loadConversacionPort, never()).deSoporte();
        verify(quitarParticipantePort, never()).quitar(any(), any());
    }

    @Test
    @DisplayName("un usuario que ya no existe no es staff: se le retiran las filas igual")
    void unUsuarioQueYaNoExisteNoEsStaff() {
        when(userSummaryFinder.findById(ADMINA)).thenReturn(Optional.empty());
        Conversacion deAna = soporteDe(ANA);
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(deAna));
        when(esParticipantePort.esParticipante(deAna.id(), ADMINA)).thenReturn(true);

        service.retirarPorBajaDeStaff(ADMINA);

        verify(quitarParticipantePort).quitar(deAna.id(), ADMINA);
    }

    // ── 5. Relleno de los aprendices que ya estaban ─────────────────────────────────────────

    @Test
    @DisplayName("el relleno crea solo las que faltan y no toca las que ya estaban")
    void elRellenoCreaSoloLasQueFaltan() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userSummaryFinder.aprendicesActivos()).thenReturn(List.of(
                new UserSummary(ANA, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                new UserSummary(BRUNO, "Bruno Diaz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.participantesInscritosActivos()).thenReturn(List.of(ANA, BRUNO));
        when(loadConversacionPort.deSoporte()).thenReturn(List.of(soporteDe(ANA)));
        when(participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST)))
                .thenReturn(List.of(ADMINA));

        ResultadoRelleno resultado = service.rellenar(ADMINA);

        assertThat(resultado).isEqualTo(new ResultadoRelleno(2, 1, 1, 0));
        assertThat(participantesAgregados()).containsExactlyInAnyOrder(BRUNO, ADMINA);
        // D-174: el relleno es de los que ya estaban; nadie recibe una bienvenida tarde.
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    /**
     * D-43: el relleno no puede hacer una consulta por persona. Con 2 aprendices tiene que pedir
     * el padron, los inscritos, los soportes existentes y el staff UNA sola vez cada uno — y nunca
     * {@code porClaveDirecta}, que es justamente la consulta por aprendiz.
     */
    @Test
    @DisplayName("el relleno no hace una consulta por aprendiz (D-43)")
    void elRellenoNoHaceUnaConsultaPorAprendiz() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userSummaryFinder.aprendicesActivos()).thenReturn(List.of(
                new UserSummary(ANA, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                new UserSummary(BRUNO, "Bruno Diaz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.participantesInscritosActivos()).thenReturn(List.of(ANA, BRUNO));
        when(loadConversacionPort.deSoporte()).thenReturn(List.of());
        when(participacionProgramaFinder.usuariosActivosConRol(any())).thenReturn(List.of(ADMINA));

        service.rellenar(ADMINA);

        verify(userSummaryFinder, times(1)).aprendicesActivos();
        verify(participacionProgramaFinder, times(1)).participantesInscritosActivos();
        verify(loadConversacionPort, times(1)).deSoporte();
        verify(participacionProgramaFinder, times(1)).usuariosActivosConRol(any());
        verify(loadConversacionPort, never()).porClaveDirecta(anyString());
        verify(participacionProgramaFinder, never()).deParticipante(any());
    }

    /** Mismo criterio que D-135: un aprendiz activo pero sin fila de programa todavia no entro. */
    @Test
    @DisplayName("el relleno saltea al aprendiz activo que todavia no esta inscrito")
    void elRellenoSalteaAlAprendizNoInscrito() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userSummaryFinder.aprendicesActivos()).thenReturn(List.of(
                new UserSummary(ANA, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.participantesInscritosActivos()).thenReturn(List.of());
        when(loadConversacionPort.deSoporte()).thenReturn(List.of());
        when(participacionProgramaFinder.usuariosActivosConRol(any())).thenReturn(List.of(ADMINA));

        ResultadoRelleno resultado = service.rellenar(ADMINA);

        assertThat(resultado).isEqualTo(new ResultadoRelleno(1, 0, 0, 0));
        verify(saveConversacionPort, never()).save(any());
    }

    @Test
    @DisplayName("el relleno es de administracion: un aprendiz no lo puede disparar")
    void elRellenoEsSoloParaAdministradores() {
        perfil(ANA, "Ana Perez", UserRole.TRAINEE, UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.rellenar(ANA)).isInstanceOf(NotAuthorizedException.class);

        verify(userSummaryFinder, never()).aprendicesActivos();
    }

    @Test
    @DisplayName("un administrador suspendido no puede disparar el relleno")
    void unAdministradorSuspendidoNoPuedeRellenar() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> service.rellenar(ADMINA)).isInstanceOf(NotAuthorizedException.class);
    }

    /** Un aprendiz que falla no puede detener el barrido (.claude/rules/02): el resto se crea y el
     * que fallo se cuenta, no se esconde. */
    @Test
    @DisplayName("si un aprendiz falla, el relleno sigue con los demas y lo informa")
    void siUnAprendizFallaElRellenoSigueConLosDemas() {
        perfil(ADMINA, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userSummaryFinder.aprendicesActivos()).thenReturn(List.of(
                new UserSummary(ANA, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                new UserSummary(BRUNO, "Bruno Diaz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(participacionProgramaFinder.participantesInscritosActivos()).thenReturn(List.of(ANA, BRUNO));
        when(loadConversacionPort.deSoporte()).thenReturn(List.of());
        when(participacionProgramaFinder.usuariosActivosConRol(any())).thenReturn(List.of(ADMINA));
        when(saveConversacionPort.save(any())).thenAnswer(inv -> {
            Conversacion c = inv.getArgument(0);
            if (c.claveDirecta().equals(Conversacion.claveSoporteDe(ANA))) {
                throw new IllegalStateException("fallo simulado de la base");
            }
            return c;
        });

        ResultadoRelleno resultado = service.rellenar(ADMINA);

        assertThat(resultado).isEqualTo(new ResultadoRelleno(2, 1, 0, 1));
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────────────────────

    private List<UserId> participantesAgregados() {
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.renaser.os.chat.domain.model.conversacion.Participante.class);
        verify(agregarParticipantePort, org.mockito.Mockito.atLeast(0)).agregar(captor.capture());
        return captor.getAllValues().stream()
                .map(com.renaser.os.chat.domain.model.conversacion.Participante::usuarioId)
                .toList();
    }

    private static Conversacion soporteDe(UserId aprendizId) {
        return Conversacion.crearSoporte(ConversacionId.of(UUID.nameUUIDFromBytes(aprendizId.value().toString()
                .getBytes())), aprendizId, "Soporte", CLOCK.now());
    }

    private void inscrito(UserId id, UserRole rol) {
        when(participacionProgramaFinder.deParticipante(id))
                .thenReturn(Optional.of(participacion(id, rol, true, false)));
    }

    private void perfil(UserId id, String nombre, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id))
                .thenReturn(Optional.of(new UserSummary(id, nombre, null, rol, estado)));
    }

    private static ParticipacionPrograma participacion(UserId id, UserRole rol, boolean inscrito,
                                                        boolean suspendido) {
        return new ParticipacionPrograma(id, inscrito, inscrito ? 3 : 0,
                inscrito ? LocalDate.of(2026, 9, 14) : null, ZoneId.of("America/Lima"),
                FasePrograma.values()[0], null, null, rol, suspendido, inscrito);
    }
}
