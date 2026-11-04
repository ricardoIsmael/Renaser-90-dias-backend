package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase.ConsultaCierresDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase.ConsultaHuecosDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.ConsultaPersonasDelPrograma;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.PaginaDePersonas;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.PersonaDelPrograma;
import com.renaser.os.leadership.application.ports.in.ConsultarRadarDePersonaUseCase.ConsultaRadarDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDePersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.ConsultaSemanaDeUnaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.mentoring.api.EvidenciaDeObligacion;
import com.renaser.os.mentoring.api.ObligacionDelDia;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.AHORA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.DESDE_VIGENTE;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.FENIX;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SEMANA_CERRADA;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.SIN_MENTOR;
import static com.renaser.os.leadership.application.services.BancoDeCumplimiento.VIERNES_FUTURO;
import static com.renaser.os.mentoring.application.services.VentanasDePrueba.ventanaPareja;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El líder ve a todos los aprendices, estén o no en un grupo (spec 021): la lista, y la semana, el Radar, los huecos y
 * los cierres de una persona sin pasar por un grupo. Lo que se prueba es lo del líder: a quién deja ver, desde qué grupo
 * mira, y qué registra.
 */
class LiderVeATodosServiceTest {

    private static final UserId LIDER = id("c1");
    private static final UserId ADMIN = id("d1");
    private static final UserId ALQUIMISTA = id("d2");
    private static final UserId SOPORTE_TI = id("e1");
    private static final UserId MENTORA = id("a1");
    private static final UserId OTRO_MENTOR = id("a2");
    private static final UserId GUIA = id("a3");
    private static final UserId ANA = id("b1");
    private static final UserId BETO = id("b2");
    private static final UserId CARLA = id("b3");
    private static final UserId DIEGO = id("b4");
    private static final UserId ELENA = id("b5");
    private static final UserId FABIO = id("b6");
    private static final UserId GINA = id("b7");
    private static final UserId IRMA = id("b8");
    private static final UUID RECEPCION = UUID.fromString("00000000-0000-0000-0000-00000000f003");
    private static final UUID OCASO = UUID.fromString("00000000-0000-0000-0000-00000000f004");

    private final BancoDeCumplimiento banco = new BancoDeCumplimiento();
    private PersonasDelProgramaService lista;
    private SemanaDePersonaService semana;
    private RadarDePersonaService radar;
    private HuecosDePersonaService huecos;
    private CierresDePersonaService cierres;

    private static UserId id(String sufijo) {
        return UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000" + sufijo));
    }

    @BeforeEach
    void preparar() {
        lista = banco.personasDelProgramaService();
        semana = banco.semanaService();
        radar = banco.radarService();
        huecos = banco.huecosService();
        cierres = banco.cierresService();
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        banco.usuario(ADMIN, "Admin", UserRole.ADMIN, UserStatus.ACTIVE);
        banco.usuario(ALQUIMISTA, "Alquimista", UserRole.ALCHEMIST, UserStatus.ACTIVE);
        banco.usuario(SOPORTE_TI, "Soporte", UserRole.IT_SUPPORT, UserStatus.ACTIVE);
        banco.usuario(OTRO_MENTOR, "Otro Mentor", UserRole.MENTOR, UserStatus.ACTIVE);
        banco.usuario(GUIA, "Guía de aprendiz", UserRole.TRAINEE_GUIDE, UserStatus.ACTIVE);
        banco.grupoConMentor(FENIX, "Grupo Fénix", MENTORA, "Luisa Rojas");
        banco.recepcion(RECEPCION, "Recepción");
        banco.grupoSinMentor(SIN_MENTOR, "Grupo sin mentor");
        banco.aprendiz(FENIX, ANA, "Ana Pérez");
        banco.aprendizSinGrupo(BETO, "Beto Álvarez");
        banco.aprendiz(RECEPCION, CARLA, "Carla Díaz");
        banco.aprendiz(SIN_MENTOR, DIEGO, "Diego Ñúñez");
        // Irma eligió ningún Día 1 todavía: su programa no está activado.
        banco.aprendiz(FENIX, IRMA, "Irma Zapata");
        banco.programa(IRMA, UserRole.TRAINEE, LocalDate.of(2026, 10, 20), false);
        banco.vigente(ANA, ventanaPareja(DESDE_VIGENTE, false, 70));
        banco.vigente(BETO, ventanaPareja(DESDE_VIGENTE, false, 40));
        banco.semana(SEMANA_CERRADA, BETO, ventanaPareja(DESDE_SEMANA_CERRADA, true, 40));
        banco.semana(SEMANA_CERRADA, ANA, ventanaPareja(DESDE_SEMANA_CERRADA, true, 70));
    }

    private PaginaDePersonas listar(UserId actor, String busqueda, int pagina, int tamano) {
        return lista.listar(new ConsultaPersonasDelPrograma(actor, busqueda, pagina, tamano));
    }

    private SemanaDeLaPersona semanaDe(UserId actor, UserId persona, LocalDate hasta) {
        return semana.semanaDeUnaPersona(new ConsultaSemanaDeUnaPersona(actor, persona.value(), hasta));
    }

    // ── la lista ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lista a todos los aprendices, con grupo, en la recepción, en un grupo sin mentor o sin ninguno, por nombre")
    void listaATodos() {
        PaginaDePersonas pagina = listar(LIDER, null, 0, 30);

        assertThat(pagina.items()).extracting(PersonaDelPrograma::nombre)
                .containsExactly("Ana Pérez", "Beto Álvarez", "Carla Díaz", "Diego Ñúñez", "Irma Zapata");
        assertThat(pagina.total()).isEqualTo(5);
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.tamano()).isEqualTo(30);
    }

    @Test
    @DisplayName("cada fila dice su grupo, su mentor y su día; sin grupo o sin mentor, en null; sin Día 1 elegido, día null")
    void cadaFila() {
        List<PersonaDelPrograma> items = listar(LIDER, null, 0, 30).items();

        PersonaDelPrograma ana = items.get(0);
        assertThat(ana.personaId()).isEqualTo(ANA.value());
        assertThat(ana.grupoId()).isEqualTo(FENIX);
        assertThat(ana.grupoNombre()).isEqualTo("Grupo Fénix");
        assertThat(ana.mentorNombre()).isEqualTo("Luisa Rojas");
        assertThat(ana.diaPrograma()).isEqualTo(63);
        PersonaDelPrograma beto = items.get(1);
        assertThat(beto.grupoId()).isNull();
        assertThat(beto.grupoNombre()).isNull();
        assertThat(beto.mentorNombre()).isNull();
        assertThat(beto.diaPrograma()).isEqualTo(63);
        PersonaDelPrograma carla = items.get(2);
        assertThat(carla.grupoId()).isEqualTo(RECEPCION);
        assertThat(carla.grupoNombre()).isEqualTo("Recepción");
        assertThat(carla.mentorNombre()).isNull();
        PersonaDelPrograma diego = items.get(3);
        assertThat(diego.grupoId()).isEqualTo(SIN_MENTOR);
        assertThat(diego.mentorNombre()).isNull();
        assertThat(items.get(4).diaPrograma()).as("Irma no eligió su Día 1").isNull();
    }

    @Test
    @DisplayName("no entran las cuentas suspendidas, las de prueba, las que no son de un aprendiz ni quien no está en el programa")
    void quienesNoSeMuestran() {
        banco.aprendiz(FENIX, ELENA, "Elena Suspendida");
        banco.usuario(ELENA, "Elena Suspendida", UserRole.TRAINEE, UserStatus.SUSPENDED);
        banco.aprendiz(FENIX, FABIO, "Fabio de Prueba");
        banco.cuentasDePrueba.add(FABIO);
        // Gina tiene cuenta de aprendiz pero nunca entró al programa: sin fila de participación.
        banco.usuario(GINA, "Gina Sinprograma", UserRole.TRAINEE, UserStatus.ACTIVE);
        // La mentora con programa propio es persona del grupo pero no una aprendiz.
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);

        assertThat(listar(LIDER, null, 0, 30).items()).extracting(PersonaDelPrograma::nombre)
                .doesNotContain("Elena Suspendida", "Fabio de Prueba", "Gina Sinprograma", "Luisa Rojas", "Lider Uno");
    }

    @Test
    @DisplayName("la búsqueda es por nombre, sin tildes ni mayúsculas, y el total cuenta lo que coincide")
    void busqueda() {
        PaginaDePersonas pagina = listar(LIDER, "ALVAREZ", 0, 30);

        assertThat(pagina.items()).extracting(PersonaDelPrograma::nombre).containsExactly("Beto Álvarez");
        assertThat(pagina.total()).isEqualTo(1);
        assertThat(listar(LIDER, "nunez", 0, 30).items()).extracting(PersonaDelPrograma::nombre)
                .containsExactly("Diego Ñúñez");
        assertThat(listar(LIDER, "zzz", 0, 30).items()).isEmpty();
        assertThat(listar(LIDER, "zzz", 0, 30).total()).isZero();
    }

    @Test
    @DisplayName("pagina de a tamano; el total es el de toda la búsqueda y una página más allá del final viene vacía")
    void paginacion() {
        PaginaDePersonas primera = listar(LIDER, null, 0, 2);
        PaginaDePersonas segunda = listar(LIDER, null, 1, 2);
        PaginaDePersonas tercera = listar(LIDER, null, 2, 2);
        PaginaDePersonas lejos = listar(LIDER, null, 9, 2);

        assertThat(primera.items()).extracting(PersonaDelPrograma::nombre).containsExactly("Ana Pérez", "Beto Álvarez");
        assertThat(segunda.items()).extracting(PersonaDelPrograma::nombre).containsExactly("Carla Díaz", "Diego Ñúñez");
        assertThat(tercera.items()).extracting(PersonaDelPrograma::nombre).containsExactly("Irma Zapata");
        assertThat(List.of(primera.total(), segunda.total(), tercera.total(), lejos.total())).containsOnly(5L);
        assertThat(lejos.items()).isEmpty();
        assertThat(lejos.pagina()).isEqualTo(9);
    }

    @Test
    @DisplayName("el orden es por nombre sin tildes ni mayúsculas; ante homónimos, estable")
    void orden() {
        banco.aprendizSinGrupo(id("c9"), "álvaro Zuñiga");
        banco.aprendizSinGrupo(id("c8"), "beto álvarez");

        assertThat(listar(LIDER, null, 0, 30).items()).extracting(PersonaDelPrograma::nombre)
                .containsExactly("álvaro Zuñiga", "Ana Pérez", "Beto Álvarez", "beto álvarez", "Carla Díaz",
                        "Diego Ñúñez", "Irma Zapata");
    }

    @Test
    @DisplayName("una persona en dos grupos sale con el que tiene mentor")
    void enDosGrupos() {
        banco.aprendiz(RECEPCION, ANA, "Ana Pérez");
        banco.grupoSinMentor(OCASO, "Ocaso");
        banco.aprendiz(OCASO, ANA, "Ana Pérez");

        assertThat(listar(LIDER, "ana", 0, 30).items()).singleElement().satisfies(ana -> {
            assertThat(ana.grupoId()).isEqualTo(FENIX);
            assertThat(ana.mentorNombre()).isEqualTo("Luisa Rojas");
        });
    }

    @Test
    @DisplayName("la lista no trae el correo ni ningún dato de contacto: la fila solo tiene lo del contrato")
    void sinCorreo() {
        assertThat(Arrays.stream(PersonaDelPrograma.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("personaId", "nombre", "avatarUrl", "grupoId", "grupoNombre", "mentorNombre",
                        "diaPrograma");
    }

    @Test
    @DisplayName("el líder, Administración y Alquimista abren la lista; abrirla no deja registro de consulta")
    void losTresRoles() {
        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            assertThat(listar(actor, null, 0, 30).total()).isEqualTo(5);
        }
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("un mentor, una guía, un aprendiz, Soporte TI o un líder suspendido no abren la lista: 403")
    void sinPermiso() {
        for (UserId actor : List.of(OTRO_MENTOR, MENTORA, GUIA, ANA, SOPORTE_TI)) {
            assertThatThrownBy(() -> listar(actor, null, 0, 30)).as("actor " + actor)
                    .isInstanceOf(NotAuthorizedException.class);
        }
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> listar(LIDER, null, 0, 30)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("la página y el tamaño tienen límites: negativa, cero o más de 100 es 400")
    void limites() {
        assertThatThrownBy(() -> listar(LIDER, null, -1, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listar(LIDER, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listar(LIDER, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(listar(LIDER, null, 0, 100).items()).hasSize(5);
    }

    // ── la semana ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("alguien sin grupo se abre igual: sin grupo ni mentor, con su ventana vigente, y la consulta se registra con el grupo en null")
    void semanaSinGrupo() {
        SemanaDeLaPersona ficha = semanaDe(LIDER, BETO, null);

        assertThat(ficha.personaId()).isEqualTo(BETO.value());
        assertThat(ficha.nombre()).isEqualTo("Beto Álvarez");
        assertThat(ficha.grupoId()).isNull();
        assertThat(ficha.mentor()).isNull();
        assertThat(ficha.desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(ficha.dias()).hasSize(7);
        assertThat(ficha.porcentaje()).isEqualByComparingTo("40");
        assertThat(banco.consultas).singleElement().satisfies((ConsultaDeFicha c) -> {
            assertThat(c.consultorId()).isEqualTo(LIDER);
            assertThat(c.rolConsultor()).isEqualTo(UserRole.MENTOR_LEAD);
            assertThat(c.personaId()).isEqualTo(BETO);
            assertThat(c.grupoId()).isNull();
            assertThat(c.mentorId()).isNull();
            assertThat(c.semanaDesde()).isEqualTo(DESDE_VIGENTE);
            assertThat(c.consultadaEn()).isEqualTo(AHORA);
        });
    }

    @Test
    @DisplayName("con grupo y mentor es EXACTAMENTE la misma ficha que por el grupo, y se registra con ese grupo y ese mentor")
    void igualQuePorElGrupo() {
        SemanaDeLaPersona porGrupo = semana.semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, ANA.value(), SEMANA_CERRADA));
        SemanaDeLaPersona sinGrupo = semanaDe(LIDER, ANA, SEMANA_CERRADA);

        assertThat(sinGrupo).isEqualTo(porGrupo);
        assertThat(sinGrupo.grupoId()).isEqualTo(FENIX);
        assertThat(sinGrupo.mentor().id()).isEqualTo(MENTORA.value());
        assertThat(banco.consultas).hasSize(2);
        assertThat(banco.consultas.get(1)).usingRecursiveComparison().ignoringFields("id")
                .isEqualTo(banco.consultas.get(0));
        assertThat(banco.consultas.get(1).grupoId()).isEqualTo(FENIX);
        assertThat(banco.consultas.get(1).mentorId()).isEqualTo(MENTORA);
    }

    @Test
    @DisplayName("en la recepción se abre con ese grupo y sin mentor a quien avisar; en un grupo sin mentor, igual")
    void recepcionYGrupoSinMentor() {
        SemanaDeLaPersona deLaRecepcion = semanaDe(LIDER, CARLA, null);
        SemanaDeLaPersona sinMentor = semanaDe(ADMIN, DIEGO, null);

        assertThat(deLaRecepcion.grupoId()).isEqualTo(RECEPCION);
        assertThat(deLaRecepcion.mentor()).isNull();
        assertThat(sinMentor.grupoId()).isEqualTo(SIN_MENTOR);
        assertThat(sinMentor.mentor()).isNull();
        assertThat(banco.consultas).extracting(ConsultaDeFicha::mentorId).containsOnlyNulls();
        assertThat(banco.consultas).extracting(ConsultaDeFicha::grupoId).containsExactly(RECEPCION, SIN_MENTOR);
    }

    @Test
    @DisplayName("la ventana es la de la ficha por grupo: un viernes pide esa semana, y uno futuro o inválido es 400 sin registro")
    void ventana() {
        SemanaDeLaPersona cerrada = semanaDe(LIDER, BETO, SEMANA_CERRADA);

        assertThat(cerrada.desde()).isEqualTo(DESDE_SEMANA_CERRADA);
        assertThat(cerrada.cerrada()).isTrue();
        banco.consultas.clear();
        assertThatThrownBy(() -> semanaDe(LIDER, BETO, VIERNES_FUTURO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> semanaDe(LIDER, BETO, LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("Administración y Alquimista también abren sin grupo y dejan su consulta, con su rol")
    void adminYAlquimista() {
        semanaDe(ADMIN, BETO, null);
        semanaDe(ALQUIMISTA, BETO, null);

        assertThat(banco.consultas).extracting(ConsultaDeFicha::rolConsultor)
                .containsExactly(UserRole.ADMIN, UserRole.ALCHEMIST);
    }

    @Test
    @DisplayName("404 sin registro ni lectura: no existe, suspendida, de prueba, sin programa, un mentor sin su programa activado, personal que no es mentor o un uuid cualquiera")
    void noSeMuestra() {
        banco.aprendizSinGrupo(ELENA, "Elena Suspendida");
        banco.usuario(ELENA, "Elena Suspendida", UserRole.TRAINEE, UserStatus.SUSPENDED);
        banco.aprendizSinGrupo(FABIO, "Fabio de Prueba");
        banco.cuentasDePrueba.add(FABIO);
        banco.usuario(GINA, "Gina Sinprograma", UserRole.TRAINEE, UserStatus.ACTIVE);
        // D-318: la mentora se abre solo con su programa ACTIVADO; inscrita pero sin Día 1 elegido, no.
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), false);
        // El personal que no es mentor ni guía no se abre, aunque haya activado el suyo.
        banco.programa(ADMIN, UserRole.ADMIN, LocalDate.of(2026, 9, 1), true);
        banco.programa(LIDER, UserRole.MENTOR_LEAD, LocalDate.of(2026, 9, 1), true);
        banco.programa(SOPORTE_TI, UserRole.IT_SUPPORT, LocalDate.of(2026, 9, 1), true);

        for (UserId persona : List.of(ELENA, FABIO, GINA, MENTORA, OTRO_MENTOR, ADMIN, LIDER, SOPORTE_TI, id("ff"))) {
            assertThatThrownBy(() -> semanaDe(LIDER, persona, null)).as("persona " + persona)
                    .isInstanceOf(NoSuchElementException.class);
        }
        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
        assertThat(banco.horariosPedidos).isEmpty();
    }

    @Test
    @DisplayName("403 sin registro ni lectura: un mentor, una guía, un aprendiz, Soporte TI o un líder suspendido")
    void semanaSinPermiso() {
        for (UserId actor : List.of(OTRO_MENTOR, MENTORA, GUIA, ANA, SOPORTE_TI)) {
            assertThatThrownBy(() -> semanaDe(actor, BETO, null)).as("actor " + actor)
                    .isInstanceOf(NotAuthorizedException.class);
        }
        banco.usuario(LIDER, "Lider Uno", UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        assertThatThrownBy(() -> semanaDe(LIDER, BETO, null)).isInstanceOf(NotAuthorizedException.class);
        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
    }

    @Test
    @DisplayName("el 403 va antes que el 404: quien no tiene permiso no averigua qué ids existen")
    void primeroElPermiso() {
        assertThatThrownBy(() -> semanaDe(OTRO_MENTOR, id("ff"), null)).isInstanceOf(NotAuthorizedException.class);
    }

    // ── D-318: el mentor y la guía con programa propio ──────────────────────

    private static final LocalDate DIA_CON_HABITOS = LocalDate.of(2026, 10, 14);

    private ObligacionDelDia ritualCumplido() {
        UUID foto = UUID.randomUUID();
        return new ObligacionDelDia(UUID.randomUUID(), "Ritual de la mañana", "COMPLETADO", true, "ENTREGADA",
                Instant.parse("2026-10-14T14:00:00Z"), "VALIDA", foto, null, "Me sentí con energía",
                List.of(new EvidenciaDeObligacion(foto, "FOTO", Instant.parse("2026-10-14T14:00:00Z"))));
    }

    private ObligacionDelDia caminarSinHacer() {
        return new ObligacionDelDia(UUID.randomUUID(), "Caminar", "PENDIENTE", false, "NO_REQUERIDA", null, null, null,
                null, null, List.of());
    }

    private void mentoraConSuPrograma() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.vigente(MENTORA, ventanaPareja(DESDE_VIGENTE, false, 85));
        banco.obligacion(MENTORA, DIA_CON_HABITOS, ritualCumplido());
        banco.obligacion(MENTORA, DIA_CON_HABITOS, caminarSinHacer());
    }

    @Test
    @DisplayName("D-318: una mentora con su programa activado se abre sin grupo, con sus días de verdad: hábitos hechos y pendientes, evidencias y lo que escribió")
    void laMentoraSeAbreConSusDias() {
        mentoraConSuPrograma();

        SemanaDeLaPersona ficha = semanaDe(LIDER, MENTORA, null);

        assertThat(ficha.personaId()).isEqualTo(MENTORA.value());
        assertThat(ficha.nombre()).isEqualTo("Luisa Rojas");
        assertThat(ficha.rol()).isEqualTo(UserRole.MENTOR);
        assertThat(ficha.desde()).isEqualTo(DESDE_VIGENTE);
        assertThat(ficha.dias()).hasSize(7);
        DiaDeLaFicha miercoles = ficha.dias().stream().filter(d -> d.fecha().equals(DIA_CON_HABITOS)).findFirst().orElseThrow();
        assertThat(miercoles.obligaciones()).extracting(ObligacionDelDia::titulo).containsExactly("Ritual de la mañana", "Caminar");
        assertThat(miercoles.obligaciones().getFirst().respuestaTexto()).isEqualTo("Me sentí con energía");
        assertThat(miercoles.obligaciones().getFirst().evidencias()).hasSize(1);
        assertThat(miercoles.obligaciones().get(1).estadoHabito()).isEqualTo("PENDIENTE");
        assertThat(ficha.resumen().obligaciones()).isEqualTo(2);
        assertThat(ficha.primeraSemana()).isNotNull();
        // Su % se ve, pero él no cuenta para el semáforo de ningún grupo: sin color de semáforo.
        assertThat(ficha.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
        assertThat(ficha.porcentaje()).isEqualByComparingTo("85");
        // Sin grupo, y sin «mentor a quien avisar»: él es el mentor.
        assertThat(ficha.grupoId()).isNull();
        assertThat(ficha.mentor()).isNull();
    }

    @Test
    @DisplayName("D-318: la consulta de la ficha de un mentor queda registrada con el rol de quien la hizo, y sin grupo ni mentor (nadie recibe el resumen de las 20:00)")
    void laConsultaDeLaMentoraSeRegistra() {
        mentoraConSuPrograma();

        semanaDe(ADMIN, MENTORA, null);
        semanaDe(LIDER, MENTORA, SEMANA_CERRADA);

        assertThat(banco.consultas).hasSize(2);
        assertThat(banco.consultas).extracting(ConsultaDeFicha::rolConsultor).containsExactly(UserRole.ADMIN, UserRole.MENTOR_LEAD);
        assertThat(banco.consultas).allSatisfy(c -> {
            assertThat(c.personaId()).isEqualTo(MENTORA);
            assertThat(c.grupoId()).isNull();
            assertThat(c.mentorId()).isNull();
        });
        assertThat(banco.consultas.get(1).semanaDesde()).isEqualTo(DESDE_SEMANA_CERRADA);
    }

    @Test
    @DisplayName("D-318: una guía de aprendiz con su programa activado también se abre; el líder, Administración y Alquimista, los tres")
    void laGuiaSeAbre() {
        banco.programa(GUIA, UserRole.TRAINEE_GUIDE, LocalDate.of(2026, 9, 1), true);
        banco.vigente(GUIA, ventanaPareja(DESDE_VIGENTE, false, 60));

        for (UserId actor : List.of(LIDER, ADMIN, ALQUIMISTA)) {
            SemanaDeLaPersona ficha = semanaDe(actor, GUIA, null);
            assertThat(ficha.rol()).isEqualTo(UserRole.TRAINEE_GUIDE);
            assertThat(ficha.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
            assertThat(ficha.dias()).hasSize(7);
            assertThat(ficha.mentor()).isNull();
        }
    }

    @Test
    @DisplayName("D-318: se arma con el mismo armado que el de un aprendiz: con igual semáforo, el mismo motivo por día; con sus obligaciones, las de ella")
    void laMentoraSeArmaIgualQueUnAprendiz() {
        mentoraConSuPrograma();
        banco.aprendizSinGrupo(ELENA, "Elena Zeta");
        banco.obligacion(ELENA, DIA_CON_HABITOS, ritualCumplido());
        banco.vigente(ELENA, ventanaPareja(DESDE_VIGENTE, false, 85));

        SemanaDeLaPersona deLaMentora = semanaDe(LIDER, MENTORA, null);
        SemanaDeLaPersona deElena = semanaDe(LIDER, ELENA, null);

        assertThat(deLaMentora.dias().stream().map(DiaDeLaFicha::motivo).toList())
                .isEqualTo(deElena.dias().stream().map(DiaDeLaFicha::motivo).toList());
        assertThat(deLaMentora.dias().stream().map(d -> d.obligaciones().size()).toList())
                .isEqualTo(List.of(0, 0, 2, 0, 0, 0, 0));
        assertThat(deElena.dias().stream().map(d -> d.obligaciones().size()).toList())
                .isEqualTo(List.of(0, 0, 1, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("D-318: el mentor suspendido o de prueba no se abre; el otro rol del personal tampoco, aunque tenga programa")
    void mentorQueNoSeMuestra() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.programa(OTRO_MENTOR, UserRole.MENTOR, LocalDate.of(2026, 9, 1), true);
        banco.programa(GUIA, UserRole.TRAINEE_GUIDE, LocalDate.of(2026, 9, 1), true);
        banco.usuario(OTRO_MENTOR, "Otro Mentor", UserRole.MENTOR, UserStatus.SUSPENDED);
        banco.cuentasDePrueba.add(GUIA);

        for (UserId persona : List.of(OTRO_MENTOR, GUIA)) {
            assertThatThrownBy(() -> semanaDe(LIDER, persona, null)).as("persona " + persona)
                    .isInstanceOf(NoSuchElementException.class);
        }
        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
        assertThat(banco.horariosPedidos).isEmpty();
    }

    @Test
    @DisplayName("D-318: un MENTOR o una guía nunca ve los días de otro mentor ni los suyos: 403 antes de saber si el id existe")
    void unMentorNoVeLosDiasDeOtroMentor() {
        mentoraConSuPrograma();
        banco.programa(GUIA, UserRole.TRAINEE_GUIDE, LocalDate.of(2026, 9, 1), true);

        for (UserId actor : List.of(OTRO_MENTOR, GUIA, MENTORA)) {
            assertThatThrownBy(() -> semanaDe(actor, MENTORA, null)).as("actor " + actor)
                    .isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> semanaDe(actor, id("ff"), null)).as("actor " + actor)
                    .isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(banco.consultas).isEmpty();
        assertThat(banco.ventanasPedidasALaSemana).isEmpty();
    }

    @Test
    @DisplayName("D-318: la mentora y la guía tampoco entran a la lista de aprendices: la lista sigue siendo de aprendices")
    void laListaSigueSiendoDeAprendices() {
        mentoraConSuPrograma();
        banco.programa(GUIA, UserRole.TRAINEE_GUIDE, LocalDate.of(2026, 9, 1), true);

        assertThat(listar(LIDER, null, 0, 30).items()).extracting(PersonaDelPrograma::nombre)
                .doesNotContain("Luisa Rojas", "Guía de aprendiz");
        assertThat(listar(LIDER, "luisa", 0, 30).total()).isZero();
    }

    @Test
    @DisplayName("D-318: el Radar, los huecos y los cierres de la mentora se abren por la misma puerta; huecos y cierres dejan su consulta sin grupo")
    void radarHuecosYCierresDeLaMentora() {
        mentoraConSuPrograma();

        radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(LIDER, MENTORA.value(), null));
        huecos.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(LIDER, MENTORA.value(), LocalDate.of(2026, 10, 12)));
        cierres.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(ADMIN, MENTORA.value(), LocalDate.of(2026, 10, 12)));

        assertThat(banco.lecturasDelRadar).extracting(l -> l.persona()).containsExactly(MENTORA);
        assertThat(banco.consultas).hasSize(2);
        assertThat(banco.consultas).allSatisfy(c -> {
            assertThat(c.personaId()).isEqualTo(MENTORA);
            assertThat(c.grupoId()).isNull();
            assertThat(c.mentorId()).isNull();
        });
    }

    @Test
    @DisplayName("D-318: el Radar, los huecos y los cierres de un mentor sin su programa activado son 404 sin leer nada; y un MENTOR que los pide, 403")
    void radarHuecosYCierresSinProgramaActivado() {
        banco.programa(MENTORA, UserRole.MENTOR, LocalDate.of(2026, 9, 1), false);

        assertThatThrownBy(() -> radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(LIDER, MENTORA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> huecos.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(LIDER, MENTORA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> cierres.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(LIDER, MENTORA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(OTRO_MENTOR, MENTORA.value(), null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(banco.lecturasDelRadar).isEmpty();
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("D-318: la ruta por grupo también da los días de verdad de la mentora de ESE grupo, igual que por la puerta sin grupo")
    void laRutaDelGrupoDaLosDiasDeLaMentora() {
        mentoraConSuPrograma();

        SemanaDeLaPersona porGrupo = semana.semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, MENTORA.value(), null));
        SemanaDeLaPersona sinGrupo = semanaDe(LIDER, MENTORA, null);

        assertThat(porGrupo.grupoId()).isEqualTo(FENIX);
        assertThat(porGrupo.color()).isEqualTo(ColorDeLaFila.NO_SE_MIDE);
        assertThat(porGrupo.mentor()).as("él es el mentor del grupo: nadie a quien avisar").isNull();
        assertThat(porGrupo.dias()).isEqualTo(sinGrupo.dias());
        assertThat(porGrupo.dias().stream().mapToInt(d -> d.obligaciones().size()).sum()).isEqualTo(2);
        assertThat(porGrupo.resumen()).isEqualTo(sinGrupo.resumen());
    }

    @Test
    @DisplayName("D-318: por el grupo solo se abre la mentora de ESE grupo; la de otro grupo, o sin programa activado, es 404")
    void laRutaDelGrupoSoloAbreAlMentorDeEseGrupo() {
        mentoraConSuPrograma();
        UUID aurora = UUID.fromString("00000000-0000-0000-0000-00000000f005");
        banco.grupoConMentor(aurora, "Grupo Aurora", OTRO_MENTOR, "Otro Mentor");
        banco.programa(OTRO_MENTOR, UserRole.MENTOR, LocalDate.of(2026, 9, 1), false);

        assertThatThrownBy(() -> semana.semanaDe(new ConsultaSemanaDePersona(LIDER, FENIX, OTRO_MENTOR.value(), null)))
                .as("el mentor de otro grupo").isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> semana.semanaDe(new ConsultaSemanaDePersona(LIDER, aurora, OTRO_MENTOR.value(), null)))
                .as("sin su programa activado").isInstanceOf(NoSuchElementException.class);
        assertThat(banco.consultas).isEmpty();
    }

    // ── el Radar, los huecos y los cierres ──────────────────────────────────

    @Test
    @DisplayName("el Radar de alguien sin grupo se lee con su id, sin registro de consulta propio (como en el grupo)")
    void radarSinGrupo() {
        radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(LIDER, BETO.value(), null));
        radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(ADMIN, CARLA.value(), null));

        assertThat(banco.lecturasDelRadar).extracting(l -> l.persona()).containsExactly(BETO, CARLA);
        assertThat(banco.lecturasDelRadar).extracting(l -> l.tamanoPagina()).containsOnly(RadarDePersonaService.TAMANO_PAGINA);
        assertThat(banco.consultas).isEmpty();
    }

    @Test
    @DisplayName("034: sin grupo, con el interruptor apagado el lider recibe «no disponible» y no se lee el Radar; Administracion sigue leyendo")
    void radarSinGrupoConElInterruptorApagado() {
        banco.interruptores.apagarElRadarEnFichasPara(LIDER);

        assertThatThrownBy(() -> radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(LIDER, BETO.value(), null)))
                .isInstanceOf(com.renaser.os.shared.domain.FuncionalidadNoDisponibleException.class);
        assertThat(banco.lecturasDelRadar).isEmpty();

        radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(ADMIN, BETO.value(), null));
        assertThat(banco.lecturasDelRadar).extracting(l -> l.persona()).containsExactly(BETO);
    }

    @Test
    @DisplayName("el Radar: 403 para quien no es del liderazgo y 404 para quien no se muestra, sin leer nada")
    void radarSinPermisoONoSeMuestra() {
        assertThatThrownBy(() -> radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(OTRO_MENTOR, BETO.value(), null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> radar.radarDeUnaPersona(new ConsultaRadarDeUnaPersona(LIDER, MENTORA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(banco.lecturasDelRadar).isEmpty();
    }

    @Test
    @DisplayName("los huecos de alguien sin grupo se leen y dejan su consulta con el grupo en null; con grupo, con grupo y mentor")
    void huecosSinGrupoYConGrupo() {
        huecos.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(LIDER, BETO.value(), LocalDate.of(2026, 10, 12)));
        huecos.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(LIDER, ANA.value(), null));

        assertThat(banco.consultas).hasSize(2);
        ConsultaDeFicha deBeto = banco.consultas.get(0);
        assertThat(deBeto.personaId()).isEqualTo(BETO);
        assertThat(deBeto.grupoId()).isNull();
        assertThat(deBeto.mentorId()).isNull();
        assertThat(deBeto.semanaDesde()).isEqualTo(LocalDate.of(2026, 10, 12));
        ConsultaDeFicha deAna = banco.consultas.get(1);
        assertThat(deAna.grupoId()).isEqualTo(FENIX);
        assertThat(deAna.mentorId()).isEqualTo(MENTORA);
        // Sin lunes pedido, el que de verdad se miró: la semana en curso de la persona.
        assertThat(deAna.semanaDesde()).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("los cierres de alguien sin grupo se leen y dejan su consulta con el grupo en null; con grupo, con grupo y mentor")
    void cierresSinGrupoYConGrupo() {
        cierres.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(ADMIN, BETO.value(), LocalDate.of(2026, 10, 12)));
        cierres.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(LIDER, ANA.value(), null));

        assertThat(banco.consultas).hasSize(2);
        assertThat(banco.consultas.get(0).rolConsultor()).isEqualTo(UserRole.ADMIN);
        assertThat(banco.consultas.get(0).grupoId()).isNull();
        assertThat(banco.consultas.get(1).grupoId()).isEqualTo(FENIX);
        assertThat(banco.consultas.get(1).mentorId()).isEqualTo(MENTORA);
    }

    @Test
    @DisplayName("huecos y cierres: 403 y 404 no leen ni registran nada")
    void huecosYCierresRechazos() {
        assertThatThrownBy(() -> huecos.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(OTRO_MENTOR, BETO.value(), null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> huecos.huecosDeUnaPersona(new ConsultaHuecosDeUnaPersona(LIDER, MENTORA.value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> cierres.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(GUIA, BETO.value(), null)))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> cierres.cierresDeUnaPersona(new ConsultaCierresDeUnaPersona(LIDER, id("ff").value(), null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(banco.consultas).isEmpty();
    }
}
