package com.renaser.os.community;

import com.renaser.os.EventosPendientes;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase;
import com.renaser.os.calendar.application.ports.in.evento.CrearEventoUseCase.CrearEventoCommand;
import com.renaser.os.calendar.application.ports.in.evento.ListarEventosParaVisorUseCase;
import com.renaser.os.calendar.application.ports.in.evento.ObtenerEventoUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.chat.application.ports.in.conversacion.ConsultarComunidadDeLaFormacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase.CrearConversacionDirectaCommand;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase.ConversacionResumen;
import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase.CompartirPublicacionCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase.EscribirComentarioCommand;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.ArchivoEntrada;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.PublicarCommand;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase.ReaccionarCommand;
import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase.CrearTestimonioCommand;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase.PromoverPublicacionCommand;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.ConsultarRankingUseCase.EntradaRanking;
import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase.FijarTipoCommand;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase.TrasladarCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La Comunidad por formación (spec 024, D-301) contra Postgres de verdad: lo que solo se ve con la base y todos los módulos juntos. Cada superficie se prueba
 * con dos cuentas en formaciones distintas —una de la Escuela y otra del Diplomado— y el personal: todo lo de una es invisible para la otra, y viceversa, con
 * 0 excepciones (SC-001 a SC-008). Administración ve las tres y elige en cuál crea.
 *
 * <p>Sin {@code @Transactional}: los casos de uso abren la suya y los efectos entre módulos (el traslado) llegan por eventos después del commit. El reloj es
 * el de pruebas, en una madrugada UTC (03:00Z = 22:00 del día anterior en Lima, regla 03).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ComunidadPorFormacionIT {

    private static final Instant AHORA = Instant.parse("2027-03-10T03:00:00Z");
    private static final Instant INICIO_DEL_EVENTO = AHORA.plus(Duration.ofHours(2));
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate CORTE = LocalDate.of(2027, 3, 9);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    // Muro y testimonios
    @Autowired
    private PublicarUseCase publicar;
    @Autowired
    private ConsultarFeedUseCase feed;
    @Autowired
    private ConsultarComentariosUseCase comentarios;
    @Autowired
    private EscribirComentarioUseCase comentar;
    @Autowired
    private ReaccionarUseCase reaccionar;
    @Autowired
    private CrearTestimonioUseCase crearTestimonio;
    @Autowired
    private PromoverPublicacionATestimonioUseCase promover;
    @Autowired
    private ConsultarTestimoniosUseCase testimonios;
    // Eventos
    @Autowired
    private CrearEventoUseCase crearEvento;
    @Autowired
    private ListarEventosParaVisorUseCase listarEventos;
    @Autowired
    private ObtenerEventoUseCase obtenerEvento;
    @Autowired
    private ConfirmarAsistenciaUseCase confirmar;
    @Autowired
    private GenerarRecordatoriosUseCase generarRecordatorios;
    // Cursos
    @Autowired
    private ConsultarMisCursosUseCase misCursos;
    @Autowired
    private ConsultarCursoDetalleUseCase detalleDelCurso;
    // Tribu
    @Autowired
    private ListarConversacionesUseCase conversaciones;
    @Autowired
    private CrearConversacionDirectaUseCase crearDirecto;
    @Autowired
    private EnviarMensajeUseCase enviarMensaje;
    @Autowired
    private CompartirPublicacionUseCase compartir;
    @Autowired
    private ConfigurarComunidadDeLaFormacionUseCase configurarComunidad;
    @Autowired
    private ConsultarComunidadDeLaFormacionUseCase verComunidad;
    @Autowired
    private TrasladarAprendizDeFormacionUseCase trasladar;
    // Ranking
    @Autowired
    private GenerarSnapshotRankingUseCase generarRanking;
    @Autowired
    private ConsultarRankingUseCase ranking;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> eventos = new ArrayList<>();
    private final List<String> cursos = new ArrayList<>();
    private UserId admin;
    private UserId ana;
    private UserId ana2;
    private UserId beto;
    private UserId mentor;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        usuarios.clear();
        eventos.clear();
        cursos.clear();
        admin = usuario("ADMIN", "Admin Uno");
        ana = aprendiz("Ana de la Escuela", Formacion.ESCUELA, 90, 5);
        ana2 = aprendiz("Ana Dos de la Escuela", Formacion.ESCUELA, 90, 5);
        beto = aprendiz("Beto del Diplomado", Formacion.DIPLOMADO, 365, 40);
        mentor = usuario("MENTOR", "Mentor de dos mundos");
        asignar(mentor, Formacion.ESCUELA, true);
        asignar(mentor, Formacion.DIPLOMADO, false);
    }

    @AfterEach
    void limpiar() {
        // Un traslado o un aviso en curso sigue escribiendo sobre las cuentas de la prueba: se espera antes de borrarlas.
        EventosPendientes.esperarQueTerminen(jdbc);
        eventos.forEach(id -> jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", id));
        cursos.forEach(id -> jdbc.update("DELETE FROM renaser.cursos WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.testimonios WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.ranking_aprendices WHERE fecha = ?", CORTE);
        jdbc.update("UPDATE renaser.formaciones SET tipos_de_comunidad = ARRAY['GENERAL','GRUPO','SOPORTE','DIRECTO']");
    }

    // -------------------------------------------------------------------------------------------------------------- Muro

    @Test
    @DisplayName("Muro: cada formación ve solo sus publicaciones; Administración lee Todas o la que elige, cada fila con la suya")
    void elMuroNoSeMezcla() {
        PublicacionId deAna = publicacion(ana, "Mi primera publicación en la Escuela", null);
        PublicacionId deBeto = publicacion(beto, "Mi primera publicación en el Diplomado", null);

        assertThat(idsDelFeed(ana, null)).containsExactly(deAna);
        assertThat(idsDelFeed(beto, null)).containsExactly(deBeto);
        assertThat(idsDelFeed(admin, null)).contains(deAna, deBeto);
        assertThat(idsDelFeed(admin, Formacion.DIPLOMADO)).containsExactly(deBeto);
        assertThat(idsDelFeed(admin, Formacion.ESCUELA)).contains(deAna).doesNotContain(deBeto);
        // Pedir una formación que no se ve no revela nada (el parámetro de la app no abre nada).
        assertThat(idsDelFeed(ana, Formacion.DIPLOMADO)).isEmpty();
        assertThat(feed.contarMisPublicaciones(ana)).isEqualTo(1);
    }

    @Test
    @DisplayName("Muro: la invitación de Hoy nombra al último autor de SU formación, no al de otra más reciente")
    void elUltimoAutorEsDeMiFormacion() {
        publicacion(ana, "Primero Ana", null);
        reloj.avanzar(Duration.ofMinutes(5));
        publicacion(beto, "Después Beto", null);

        assertThat(feed.ultimoAutor(ana)).contains("Ana de la Escuela");
        assertThat(feed.ultimoAutor(beto)).contains("Beto del Diplomado");
    }

    @Test
    @DisplayName("Muro: una publicación de otra formación es como si no existiera: comentarios, reacciones y «quién reaccionó» dan el mismo 404")
    void unaPublicacionAjenaEsInexistente() {
        PublicacionId deAna = publicacion(ana, "Solo para la Escuela", null);
        PublicacionId inexistente = PublicacionId.of(UUID.randomUUID());

        assertThat(comentarios.pagina(ana, deAna, null).comentarios()).isEmpty();
        assertThatThrownBy(() -> comentarios.pagina(beto, deAna, null)).isInstanceOf(NoSuchElementException.class)
                .hasMessageStartingWith("Publicacion no encontrada");
        assertThatThrownBy(() -> comentar.escribir(new EscribirComentarioCommand(beto, deAna, "Me cuelo desde el Diplomado")))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> reaccionar.reaccionar(new ReaccionarCommand(beto, deAna, TipoReaccion.ME_GUSTA)))
                .isInstanceOf(NoSuchElementException.class);
        // Y el mismo mensaje que el de un id que nunca existió.
        assertThatThrownBy(() -> comentarios.pagina(beto, inexistente, null)).isInstanceOf(NoSuchElementException.class)
                .hasMessageStartingWith("Publicacion no encontrada");
        // Dentro de su formación, todo funciona.
        comentar.escribir(new EscribirComentarioCommand(ana2, deAna, "Qué bueno, Ana"));
        assertThat(reaccionar.reaccionar(new ReaccionarCommand(ana2, deAna, TipoReaccion.ME_GUSTA)).reaccionado()).isTrue();
    }

    @Test
    @DisplayName("Muro: Administración no pertenece a una formación, así que elige en cuál publica; los demás publican en la suya")
    void administracionEligeDondePublica() {
        assertThatThrownBy(() -> publicacion(admin, "Aviso general", null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Elige la formación");
        PublicacionId delAdmin = publicacion(admin, "Aviso para el Diplomado", Formacion.DIPLOMADO);

        assertThat(idsDelFeed(beto, null)).contains(delAdmin);
        assertThat(idsDelFeed(ana, null)).doesNotContain(delAdmin);
        // Un aprendiz no puede publicar en una formación que no es la suya.
        assertThatThrownBy(() -> publicacion(ana, "Me cuelo en el Diplomado", Formacion.DIPLOMADO))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ---------------------------------------------------------------------------------------------------- Testimonios

    @Test
    @DisplayName("Testimonios: el escrito a mano queda donde Administración lo pone; el promovido, en la formación de su publicación")
    void losTestimoniosSonDeSuFormacion() {
        crearTestimonio.crear(new CrearTestimonioCommand(admin, "Marta Quispe", "Egresada", "Cambió mi forma de trabajar.", 5, Formacion.DIPLOMADO));
        PublicacionId deAna = publicacion(ana, "Logré mi objetivo de la semana", null);
        promover.promover(new PromoverPublicacionCommand(admin, deAna, 5));

        assertThat(nombresDeTestimonios(beto, null)).contains("Marta Quispe").doesNotContain("Ana de la Escuela");
        assertThat(nombresDeTestimonios(ana, null)).contains("Ana de la Escuela").doesNotContain("Marta Quispe");
        assertThat(nombresDeTestimonios(admin, null)).contains("Marta Quispe", "Ana de la Escuela");
        assertThat(nombresDeTestimonios(admin, Formacion.DIPLOMADO)).contains("Marta Quispe").doesNotContain("Ana de la Escuela");
        assertThatThrownBy(() -> crearTestimonio.crear(new CrearTestimonioCommand(admin, "Luis", "Egresado", "Sin elegir formación", 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------------------------------------------- Eventos

    @Test
    @DisplayName("Eventos: «todos» son todos los de la formación; lista, detalle y «Voy» de otra formación son inexistentes")
    void losEventosSonDeSuFormacion() {
        EventoId delDiplomado = evento(Formacion.DIPLOMADO, false);
        EventoId deLaEscuela = evento(Formacion.ESCUELA, false);

        assertThat(idsDeEventos(beto, null)).contains(delDiplomado.value()).doesNotContain(deLaEscuela.value());
        assertThat(idsDeEventos(ana, null)).contains(deLaEscuela.value()).doesNotContain(delDiplomado.value());
        assertThat(idsDeEventos(admin, null)).contains(delDiplomado.value(), deLaEscuela.value());
        assertThat(idsDeEventos(admin, Formacion.DIPLOMADO)).contains(delDiplomado.value()).doesNotContain(deLaEscuela.value());
        assertThat(obtenerEvento.obtener(beto, delDiplomado).evento().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThatThrownBy(() -> obtenerEvento.obtener(ana, delDiplomado)).isInstanceOf(NoSuchElementException.class)
                .hasMessageStartingWith("Evento no encontrado");
        assertThatThrownBy(() -> confirmar.confirmar(ana, delDiplomado, INICIO_DEL_EVENTO, EstadoConfirmacion.ASISTE))
                .isInstanceOf(NoSuchElementException.class);
        confirmar.confirmar(beto, delDiplomado, INICIO_DEL_EVENTO, EstadoConfirmacion.ASISTE);
        assertThatThrownBy(() -> evento(null, false)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Elige la formación");
    }

    @Test
    @DisplayName("Eventos: los avisos y recordatorios llegan solo a las personas de la formación del evento")
    void losAvisosDeUnEventoNoCruzanFormaciones() {
        EventoId delDiplomado = evento(Formacion.DIPLOMADO, true);

        generarRecordatorios.generar(AHORA);

        assertThat(recordatoriosDe(delDiplomado, beto)).isPositive();
        assertThat(recordatoriosDe(delDiplomado, ana)).isZero();
        assertThat(recordatoriosDe(delDiplomado, ana2)).isZero();
    }

    // -------------------------------------------------------------------------------------------------------- Cursos

    @Test
    @DisplayName("Cursos: el catálogo es el de la formación (ni como «bloqueado»); un curso de otra formación es inexistente por id")
    void losCursosSonDeSuFormacion() {
        String delDiplomado = curso("diplomado-it", Formacion.DIPLOMADO, null);
        String deLaEscuela = curso("escuela-it", Formacion.ESCUELA, null);
        String futuro = curso("futuro-it", Formacion.DIPLOMADO, 300);

        assertThat(idsDeCursos(beto, null)).contains(delDiplomado).doesNotContain(deLaEscuela, futuro);
        assertThat(idsDeCursos(ana, null)).contains(deLaEscuela).doesNotContain(delDiplomado, futuro);
        assertThat(idsDeCursos(admin, Formacion.DIPLOMADO)).contains(delDiplomado).doesNotContain(deLaEscuela);
        assertThat(detalleDelCurso.detalle(beto, CursoId.of(delDiplomado)).curso().id().value()).isEqualTo(delDiplomado);
        assertThatThrownBy(() -> detalleDelCurso.detalle(ana, CursoId.of(delDiplomado))).isInstanceOf(NoSuchElementException.class)
                .hasMessageStartingWith("Curso no encontrado");
    }

    // ---------------------------------------------------------------------------------------------------------- Tribu

    @Test
    @DisplayName("Tribu: cada formación tiene SU comunidad general (una sola), con el nombre de la Escuela intacto")
    void unaComunidadGeneralPorFormacion() {
        List<ConversacionResumen> deAna = conversaciones.listar(ana);
        List<ConversacionResumen> deBeto = conversaciones.listar(beto);

        assertThat(generales(deAna)).hasSize(1).first().satisfies(g -> {
            assertThat(g.conversacion().formacion()).isEqualTo(Formacion.ESCUELA);
            assertThat(g.nombre()).isEqualTo("Formación Renaser Global");
        });
        assertThat(generales(deBeto)).hasSize(1).first().satisfies(g -> {
            assertThat(g.conversacion().formacion()).isEqualTo(Formacion.DIPLOMADO);
            assertThat(g.nombre()).isEqualTo("Diplomado Renaser Global");
        });
        assertThat(generales(conversaciones.listar(admin, null))).extracting(r -> r.conversacion().formacion())
                .contains(Formacion.ESCUELA, Formacion.DIPLOMADO);
        // Personal con dos formaciones: solo la activa.
        assertThat(generales(conversaciones.listar(mentor))).extracting(r -> r.conversacion().formacion())
                .containsExactly(Formacion.ESCUELA);
        // Una comunidad general por formación: la base lo garantiza.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.conversaciones WHERE tipo = 'GLOBAL' AND formacion = 'DIPLOMADO'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Tribu: un directo solo se abre entre personas que comparten formación, y sigue la misma regla al leerlo")
    void losDirectosNoCruzanFormaciones() {
        assertThatThrownBy(() -> crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(ana, beto)))
                .isInstanceOf(NoSuchElementException.class);
        var conAna2 = crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(ana, ana2));
        assertThat(conAna2.formacion()).isEqualTo(Formacion.ESCUELA);
        // Administración se escribe con cualquiera: el directo con Beto nace en el Diplomado, con clave propia.
        var conAdmin = crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(beto, admin));
        assertThat(conAdmin.formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(conAdmin.claveDirecta()).endsWith(":DIPLOMADO");

        enviarMensaje.enviar(texto(ana, conAna2.id(), "Hola Ana Dos"));
        assertThat(idsDeConversaciones(ana2, null)).contains(conAna2.id().value());
        assertThatThrownBy(() -> enviarMensaje.enviar(texto(beto, conAna2.id(), "Me cuelo en una conversación de la Escuela")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Tribu: una publicación solo se comparte en una conversación de su misma formación")
    void compartirSoloEnMiFormacion() {
        PublicacionId deAna = publicacion(ana, "Para compartir", null);
        var directoEscuela = crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(ana, ana2));
        // Administración ve las tres formaciones: su directo con Beto nace en el Diplomado, el único mundo que comparten.
        var directoDiplomado = crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(admin, beto));
        assertThat(directoDiplomado.formacion()).isEqualTo(Formacion.DIPLOMADO);

        assertThat(compartir.compartir(new CompartirPublicacionCommand(ana, directoEscuela.id(), deAna.value()))).isNotNull();
        // La publicación es de la Escuela: no se comparte en una conversación del Diplomado ni la ve Beto (404 igual que un inexistente).
        assertThatThrownBy(() -> compartir.compartir(new CompartirPublicacionCommand(admin, directoDiplomado.id(), deAna.value())))
                .isInstanceOf(NoSuchElementException.class).hasMessageStartingWith("Publicacion no encontrada");
    }

    @Test
    @DisplayName("Tipos de comunidad: apagar «Directos» en el Diplomado los esconde ahí sin tocar la Escuela ni borrar nada; prenderlo los devuelve")
    void apagarUnTipoEsconde() {
        var directo = crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(beto, admin));
        assertThat(idsDeConversaciones(beto, null)).contains(directo.id().value());

        configurarComunidad.fijar(new FijarTipoCommand(admin, Formacion.DIPLOMADO, TipoDeComunidad.DIRECTO, false));

        assertThat(idsDeConversaciones(beto, null)).doesNotContain(directo.id().value());
        assertThatThrownBy(() -> enviarMensaje.enviar(texto(beto, directo.id(), "Con el tipo apagado"))).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(beto, admin)))
                .isInstanceOf(NotAuthorizedException.class);
        var conAna2 = crearDirecto.obtenerOCrear(new CrearConversacionDirectaCommand(ana, ana2));
        assertThat(idsDeConversaciones(ana, null)).contains(conAna2.id().value());
        var tipos = verComunidad.consultar(admin, Formacion.DIPLOMADO);
        assertThat(tipos).filteredOn(t -> t.tipo() == TipoDeComunidad.DIRECTO).first().satisfies(t -> {
            assertThat(t.prendido()).isFalse();
            assertThat(t.conversaciones()).isGreaterThanOrEqualTo(1);
        });

        configurarComunidad.fijar(new FijarTipoCommand(admin, Formacion.DIPLOMADO, TipoDeComunidad.DIRECTO, true));
        assertThat(idsDeConversaciones(beto, null)).contains(directo.id().value());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?", Integer.class, directo.id().value()))
                .isZero();
    }

    @Test
    @DisplayName("Tipos de comunidad: solo Administración y Alquimista los prenden o apagan")
    void soloAdministracionConfigura() {
        for (UserId sinPermiso : List.of(ana, beto, mentor)) {
            assertThatThrownBy(() -> configurarComunidad.fijar(new FijarTipoCommand(sinPermiso, Formacion.DIPLOMADO, TipoDeComunidad.SOPORTE, false)))
                    .isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> verComunidad.consultar(sinPermiso, Formacion.DIPLOMADO)).isInstanceOf(NotAuthorizedException.class);
        }
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", admin.value());
        assertThatThrownBy(() -> configurarComunidad.fijar(new FijarTipoCommand(admin, Formacion.DIPLOMADO, TipoDeComunidad.SOPORTE, false)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("Traslado: el aprendiz deja la comunidad general de la anterior y entra a la de la nueva; lo que escribió se queda donde lo escribió")
    void trasladarCambiaDeComunidad() {
        PublicacionId deAna = publicacion(ana, "Esto se queda en la Escuela", null);
        assertThat(generales(conversaciones.listar(ana))).extracting(r -> r.conversacion().formacion()).containsExactly(Formacion.ESCUELA);
        jdbc.update("UPDATE renaser.participantes_programa SET programa_completado = true, fecha_inicio = DATE '2026-10-01' WHERE usuario_id = ?",
                ana.value());

        trasladar.trasladar(new TrasladarCommand(admin, ana, Formacion.DIPLOMADO, 90, false, null));

        esperar("Ana entra a la comunidad general del Diplomado y sale de la de la Escuela", () ->
                generales(conversaciones.listar(ana)).stream().allMatch(r -> r.conversacion().formacion() == Formacion.DIPLOMADO)
                        && !generales(conversaciones.listar(ana)).isEmpty());
        assertThat(idsDelFeed(ana, null)).doesNotContain(deAna);
        assertThat(idsDelFeed(ana2, null)).contains(deAna);
        assertThat(feed.contarMisPublicaciones(ana)).isZero();
    }

    // -------------------------------------------------------------------------------------------------------- Ranking

    @Test
    @DisplayName("Ranking: cada formación compara solo a los suyos y la posición es dentro de ella")
    void elRankingEsPorFormacion() {
        ponerPuntos(ana, 900_000);
        ponerPuntos(ana2, 800_000);
        ponerPuntos(beto, 700_000);
        generarRanking.generar(TipoRanking.LEAGUE, CORTE);

        List<EntradaRanking> deAna = ranking.consultar(ana, TipoRanking.LEAGUE, CORTE);
        List<EntradaRanking> deBeto = ranking.consultar(beto, TipoRanking.LEAGUE, CORTE);

        assertThat(deAna).extracting(EntradaRanking::formacion).containsOnly(Formacion.ESCUELA);
        assertThat(deBeto).extracting(EntradaRanking::formacion).containsOnly(Formacion.DIPLOMADO);
        assertThat(deAna).extracting(EntradaRanking::participanteId).contains(ana, ana2).doesNotContain(beto);
        assertThat(deBeto).extracting(EntradaRanking::participanteId).contains(beto).doesNotContain(ana, ana2);
        assertThat(posicionDe(deAna, ana)).isEqualTo(1);
        assertThat(posicionDe(deAna, ana2)).isEqualTo(2);
        assertThat(posicionDe(deBeto, beto)).as("Beto es el primero del Diplomado aunque tenga menos puntos que Ana").isEqualTo(1);
        // Administración: Todas, cada fila con su formación y SU posición; o la que elige.
        List<EntradaRanking> todas = ranking.consultar(admin, TipoRanking.LEAGUE, CORTE, null);
        assertThat(posicionDe(todas, beto)).isEqualTo(1);
        assertThat(posicionDe(todas, ana)).isEqualTo(1);
        assertThat(ranking.consultar(admin, TipoRanking.LEAGUE, CORTE, Formacion.DIPLOMADO)).extracting(EntradaRanking::participanteId)
                .contains(beto).doesNotContain(ana);
        // Pedir una formación que no se ve no revela nada.
        assertThat(ranking.consultar(ana, TipoRanking.LEAGUE, CORTE, Formacion.DIPLOMADO)).isEmpty();
    }

    @Test
    @DisplayName("Migración: todo lo que existía es de la Escuela (SC-009) y lo que se crea sin decir formación también")
    void loExistenteEsDeLaEscuela() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.eventos (id, titulo, inicia_en, timezone, tipo_ubicacion, tipo_audiencia, estado, tipo_evento) "
                + "VALUES (?, 'Sin formación', now(), 'America/Lima', 'MEET', 'TODOS', 'PUBLICADO', 'ESPONTANEO')", id);
        eventos.add(id);
        assertThat(jdbc.queryForObject("SELECT formacion FROM renaser.eventos WHERE id = ?", String.class, id)).isEqualTo("ESCUELA");
        assertThat(jdbc.queryForList("SELECT tipos_de_comunidad::text FROM renaser.formaciones ORDER BY orden", String.class))
                .allMatch(t -> t.contains("GENERAL") && t.contains("GRUPO") && t.contains("SOPORTE") && t.contains("DIRECTO"));
    }

    // -------------------------------------------------------------------------------------------------------- apoyo

    private PublicacionId publicacion(UserId autor, String texto, Formacion formacion) {
        var media = List.of(new ArchivoEntrada("wall", "muro/fotos/" + autor.value() + "/" + UUID.randomUUID(), "image/jpeg"));
        return PublicacionId.of(publicar.publicar(new PublicarCommand(autor, texto, media, null, formacion)).publicacion().id().value());
    }

    private List<PublicacionId> idsDelFeed(UserId visor, Formacion formacion) {
        return feed.feed(visor, null, null, formacion).publicaciones().stream().map(v -> v.publicacion().id()).toList();
    }

    private List<String> nombresDeTestimonios(UserId visor, Formacion formacion) {
        return testimonios.listarDestacados(visor, formacion).stream().map(v -> v.testimonio().nombre()).toList();
    }

    private EventoId evento(Formacion formacion, boolean avisarAlGuardar) {
        EventoId id = crearEvento.crear(new CrearEventoCommand(admin, "Sesión " + UUID.randomUUID().toString().substring(0, 6), null,
                INICIO_DEL_EVENTO, 60, LIMA, TipoUbicacion.MEET, "https://meet.google.com/abc-defg-hij", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.SESION_ESPECIAL, avisarAlGuardar, false, false, null, Set.of(), List.of(), formacion)).evento().id();
        eventos.add(id.value());
        return id;
    }

    private List<UUID> idsDeEventos(UserId visor, Formacion formacion) {
        return listarEventos.listar(visor, AHORA, AHORA.plus(Duration.ofDays(3)), formacion).stream()
                .map(o -> o.evento().id().value()).toList();
    }

    private int recordatoriosDe(EventoId evento, UserId usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.recordatorios_evento WHERE evento_id = ? AND usuario_id = ?",
                Integer.class, evento.value(), usuario.value());
    }

    private String curso(String id, Formacion formacion, Integer diaDesbloqueo) {
        jdbc.update("INSERT INTO renaser.cursos (id, slug, titulo, publicado, acceso, dia_desbloqueo, formacion) "
                + "VALUES (?, ?, ?, true, 'ABIERTO', ?, ?)", id, id, "Curso " + id, diaDesbloqueo, formacion.codigo());
        cursos.add(id);
        return id;
    }

    private List<String> idsDeCursos(UserId visor, Formacion formacion) {
        return misCursos.misCursos(visor, formacion).stream().map(c -> c.curso().id().value()).toList();
    }

    private static List<ConversacionResumen> generales(List<ConversacionResumen> lista) {
        return lista.stream().filter(r -> r.conversacion().tipo() == TipoConversacion.GLOBAL).toList();
    }

    private List<UUID> idsDeConversaciones(UserId visor, Formacion formacion) {
        return conversaciones.listar(visor, formacion).stream().map(r -> r.conversacion().id().value()).toList();
    }

    private static EnviarMensajeCommand texto(UserId autor, ConversacionId conversacion, String texto) {
        return new EnviarMensajeCommand(autor, conversacion, TipoMensaje.TEXTO, texto, null, null, null, null, null, null, OrigenMedia.CLIENTE);
    }

    private static int posicionDe(List<EntradaRanking> entradas, UserId persona) {
        return entradas.stream().filter(e -> e.participanteId().equals(persona)).findFirst().orElseThrow().posicion();
    }

    private void ponerPuntos(UserId persona, int puntos) {
        jdbc.update("INSERT INTO renaser.puntajes_participante (participante_id, coherencia, puntos_liga, racha_actual, racha_maxima) "
                + "VALUES (?, 80, ?, 0, 0) ON CONFLICT (participante_id) DO UPDATE SET puntos_liga = EXCLUDED.puntos_liga",
                persona.value(), puntos);
    }

    private void asignar(UserId persona, Formacion formacion, boolean activa) {
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, ?, ?) "
                + "ON CONFLICT (usuario_id, formacion) DO UPDATE SET activa = EXCLUDED.activa", persona.value(), formacion.codigo(), activa);
    }

    private UserId usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')", id, id + "@comunidad024.test", nombre, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId aprendiz(String nombre, Formacion formacion, int duracion, int dia) {
        UserId id = usuario("APRENDIZ", nombre);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone, "
                + "formacion, duracion_dias) VALUES (?, DATE '2027-03-01', ?, TIMESTAMPTZ '2027-02-28 12:00:00+00', 'America/Lima', ?, ?)",
                id.value(), dia, formacion.codigo(), duracion);
        return id;
    }

    private static void esperar(String que, BooleanSupplier condicion) {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("No llegó a cumplirse: " + que);
    }
}
