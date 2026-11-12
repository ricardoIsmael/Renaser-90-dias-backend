package com.renaser.os.community;

import com.renaser.os.EventosPendientes;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PublicacionVista;
import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EscribirComentarioUseCase.EscribirComentarioCommand;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.ArchivoEntrada;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.PublicarCommand;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase.ReaccionarCommand;
import com.renaser.os.community.application.ports.in.sobrenombre.AdministrarSobrenombresUseCase;
import com.renaser.os.community.application.ports.in.sobrenombre.MiSobrenombreUseCase;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException;
import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException.Motivo;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El sobrenombre en el Muro (spec 046, D-331) contra Postgres: lo eligen la persona y Administración, reemplaza el nombre en las
 * publicaciones, los comentarios y las reacciones, y el equipo ve también el nombre real.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SobrenombreEnElMuroIT {

    private static final Instant AHORA = Instant.parse("2027-03-10T15:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;
    @Autowired private MiSobrenombreUseCase mio;
    @Autowired private AdministrarSobrenombresUseCase admin;
    @Autowired private PublicarUseCase publicar;
    @Autowired private ConsultarFeedUseCase feed;
    @Autowired private EscribirComentarioUseCase comentar;
    @Autowired private ConsultarComentariosUseCase comentarios;
    @Autowired private ReaccionarUseCase reaccionar;
    @Autowired private ConsultarReaccionesUseCase reacciones;

    private final List<UUID> usuarios = new ArrayList<>();
    private UserId ana;
    private UserId beto;
    private UserId bea;
    private UserId mentor;
    private UserId administracion;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        usuarios.clear();
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        ana = aprendiz("Ana Pérez " + sufijo, Formacion.ESCUELA);
        beto = aprendiz("Beto Ríos " + sufijo, Formacion.ESCUELA);
        bea = aprendiz("Bea del Diplomado " + sufijo, Formacion.DIPLOMADO);
        mentor = usuario("MENTOR", "Mentora " + sufijo);
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'ESCUELA', true)", mentor.value());
        administracion = usuario("ADMIN", "Admin " + sufijo);
    }

    @AfterEach
    void limpiar() {
        EventosPendientes.esperarQueTerminen(jdbc);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.palabras_prohibidas_sobrenombre WHERE palabra = 'tigre'");
    }

    @Test
    @DisplayName("spec 046 FR-001, FR-002, FR-003: el sobrenombre reemplaza el nombre en publicaciones, comentarios y reacciones; el equipo ve el real")
    void reemplazaElNombreEnElMuro() {
        String apodo = "Fénix " + ana.value().toString().substring(0, 6);
        assertThat(mio.elegir(ana, apodo).sobrenombre()).isEqualTo(apodo);
        PublicacionId post = publicacion(ana);
        comentar.escribir(new EscribirComentarioCommand(ana, post, "Gracias, grupo"));
        reaccionar.reaccionar(new ReaccionarCommand(ana, post, TipoReaccion.ME_GUSTA));

        PublicacionVista paraBeto = vista(beto, post);
        assertThat(paraBeto.autorNombre()).isEqualTo(apodo);
        assertThat(paraBeto.autorNombreReal()).isNull();
        assertThat(comentarios.pagina(beto, post, null).comentarios().get(0).autorNombre()).isEqualTo(apodo);
        assertThat(comentarios.pagina(beto, post, null).comentarios().get(0).autorNombreReal()).isNull();
        assertThat(reacciones.reacciones(beto, post).get(0).nombre()).isEqualTo(apodo);
        assertThat(reacciones.reacciones(beto, post).get(0).nombreReal()).isNull();

        PublicacionVista paraLaMentora = vista(mentor, post);
        assertThat(paraLaMentora.autorNombre()).isEqualTo(apodo);
        assertThat(paraLaMentora.autorNombreReal()).startsWith("Ana Pérez");
        assertThat(comentarios.pagina(mentor, post, null).comentarios().get(0).autorNombreReal()).startsWith("Ana Pérez");
        assertThat(reacciones.reacciones(mentor, post).get(0).nombreReal()).startsWith("Ana Pérez");
    }

    @Test
    @DisplayName("spec 046 FR-004: único dentro de la formación (D6), nunca el nombre real de otra, y cambio cada 30 días (D5)")
    void reglas() {
        String apodo = "León " + ana.value().toString().substring(0, 6);
        mio.elegir(ana, apodo);
        assertThatThrownBy(() -> mio.elegir(beto, apodo.toUpperCase())).isInstanceOf(SobrenombreRechazadoException.class)
                .extracting(e -> ((SobrenombreRechazadoException) e).motivo()).isEqualTo(Motivo.REPETIDO);
        // otra formación: puede usar el mismo
        assertThat(mio.elegir(bea, apodo).sobrenombre()).isEqualTo(apodo);
        String nombreDeBeto = jdbc.queryForObject("SELECT nombre_completo FROM renaser.usuarios WHERE id = ?", String.class, beto.value());
        assertThatThrownBy(() -> mio.elegir(mentor, nombreDeBeto.substring(0, Math.min(20, nombreDeBeto.length()))))
                .isInstanceOf(SobrenombreRechazadoException.class);
        // 30 días
        assertThatThrownBy(() -> mio.elegir(ana, "Otro " + ana.value().toString().substring(0, 6)))
                .isInstanceOf(SobrenombreRechazadoException.class)
                .extracting(e -> ((SobrenombreRechazadoException) e).motivo()).isEqualTo(Motivo.ESPERA);
        // Issue #99: mandar el MISMO otra vez (doble toque, reintento) no es un cambio: responde lo de siempre, sin 409 de espera.
        assertThat(mio.elegir(ana, apodo).sobrenombre()).isEqualTo(apodo);
        assertThat(mio.elegir(ana, "  " + apodo + " ").sobrenombre()).isEqualTo(apodo);
        assertThat(mio.mio(ana).puedeCambiarDesde()).isEqualTo(AHORA.plus(Duration.ofDays(30)));
        reloj.fijar(AHORA.plus(Duration.ofDays(31)));
        assertThat(mio.elegir(ana, "Otro " + ana.value().toString().substring(0, 6)).puedeCambiarDesde()).isNotNull();
    }

    @Test
    @DisplayName("spec 046 FR-001: «Ahora no» deja la invitación descartada; no es obligatorio (D2, D8)")
    void ahoraNo() {
        assertThat(mio.mio(ana).invitacionDescartada()).isFalse();
        assertThat(mio.descartarInvitacion(ana).invitacionDescartada()).isTrue();
        assertThat(mio.mio(ana).sobrenombre()).isNull();
        assertThat(vista(beto, publicacion(ana)).autorNombre()).startsWith("Ana Pérez");
    }

    @Test
    @DisplayName("spec 046 FR-005, FR-006: Administración lo cambia con motivo (sin esperar 30 días), le avisa a la persona y edita las palabras")
    void administracion() {
        mio.elegir(ana, "Lince " + ana.value().toString().substring(0, 6));
        String nuevo = "Puma " + ana.value().toString().substring(0, 6);
        assertThat(admin.cambiar(administracion, ana, nuevo, "Lo pidió por soporte")).isEqualTo(nuevo);
        assertThat(admin.de(administracion, ana)).isEqualTo(nuevo);
        assertThat(jdbc.queryForObject("SELECT motivo FROM renaser.sobrenombres_cambios WHERE usuario_id = ? AND por <> usuario_id",
                String.class, ana.value())).isEqualTo("Lo pidió por soporte");
        EventosPendientes.esperarQueTerminen(jdbc);
        List<String> avisos = jdbc.queryForList("SELECT cuerpo FROM renaser.notificaciones WHERE usuario_id = ?", String.class, ana.value());
        assertThat(avisos).anyMatch(c -> c.equals("Administración cambió tu sobrenombre en el Muro.")).noneMatch(c -> c.contains("soporte"));
        assertThat(admin.listar(administracion, "puma")).anyMatch(f -> f.persona().equals(ana) && f.nombreReal().startsWith("Ana Pérez"));
        assertThat(admin.agregarPalabra(administracion, "Tigre").deAdministracion()).contains("tigre");
        assertThatThrownBy(() -> mio.elegir(beto, "El Tigre")).isInstanceOf(SobrenombreRechazadoException.class);
        assertThat(admin.cambiar(administracion, ana, null, "Pedido de la persona")).isNull();
        assertThat(vista(beto, publicacion(ana)).autorNombre()).startsWith("Ana Pérez");
    }

    @Test
    @DisplayName("spec 046 FR-005, FR-006 (D-349): solo ADMIN y ALCHEMIST administran; el mentor y la guía reciben 403 y no cambian nada")
    void soloModeranAdministran() {
        String apodo = "Cóndor " + ana.value().toString().substring(0, 6);
        mio.elegir(ana, apodo);
        UserId guia = usuario("GUIA_APRENDIZ", "Guía " + ana.value().toString().substring(0, 6));
        UserId alquimista = usuario("ALQUIMISTA", "Alquimista " + ana.value().toString().substring(0, 6));

        for (UserId quien : List.of(mentor, guia)) {
            assertThatThrownBy(() -> admin.de(quien, ana)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> admin.cambiar(quien, ana, "Otro " + ana.value().toString().substring(0, 6), "Sin ser moderador"))
                    .isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> admin.cambiar(quien, ana, null, "Sin ser moderador")).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> admin.listar(quien, "")).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> admin.palabras(quien)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> admin.agregarPalabra(quien, "tigre")).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> admin.quitarPalabra(quien, "tigre")).isInstanceOf(NotAuthorizedException.class);
        }
        // nada cambió: el sobrenombre sigue, la lista de palabras sigue vacía y no quedó constancia de ningún cambio ajeno
        assertThat(mio.mio(ana).sobrenombre()).isEqualTo(apodo);
        assertThat(admin.palabras(administracion).deAdministracion()).doesNotContain("tigre");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.sobrenombres_cambios WHERE usuario_id = ? AND por <> usuario_id",
                Integer.class, ana.value())).isZero();
        // y quienes moderan siguen pudiendo
        assertThat(admin.de(administracion, ana)).isEqualTo(apodo);
        assertThat(admin.de(alquimista, ana)).isEqualTo(apodo);
        assertThat(admin.listar(alquimista, "")).anyMatch(f -> f.persona().equals(ana));
    }

    private PublicacionVista vista(UserId visor, PublicacionId id) {
        Instant cursor = null;
        for (int i = 0; i < 100; i++) {
            var pagina = feed.feed(visor, cursor, null, null);
            for (PublicacionVista v : pagina.publicaciones()) {
                if (v.publicacion().id().equals(id)) {
                    return v;
                }
            }
            cursor = pagina.siguienteCursor();
            if (cursor == null) {
                break;
            }
        }
        throw new AssertionError("No está en el Muro de " + visor);
    }

    private PublicacionId publicacion(UserId autor) {
        var media = List.of(new ArchivoEntrada("wall", "muro/fotos/" + autor.value() + "/" + UUID.randomUUID(), "image/jpeg"));
        return PublicacionId.of(publicar.publicar(new PublicarCommand(autor, "Hola", media, null, null)).publicacion().id().value());
    }

    private UserId usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')", id, id + "@sobrenombre046.test", nombre, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId aprendiz(String nombre, Formacion formacion) {
        UserId id = usuario("APRENDIZ", nombre);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone, "
                + "formacion, duracion_dias) VALUES (?, DATE '2027-03-01', 7, TIMESTAMPTZ '2027-02-28 12:00:00+00', 'America/Lima', ?, 90)",
                id.value(), formacion.codigo());
        return id;
    }
}
