package com.renaser.os.community.infrastructure.adapter.out.persistence.publicacion;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.api.ReferenciasExternasDeMediaDelMuro;
import com.renaser.os.community.application.ports.out.publicacion.ReferenciasDeMediaDelMuroPort;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El censo de quien mas mira una clave {@code muro/}, contra Postgres de verdad.
 *
 * <p><b>Por que contra Postgres y no con dobles.</b> Lo que se prueba aca no es una regla de
 * negocio —esa vive en {@code PublicacionMuroServiceTest} y se prueba sin base— sino que las
 * consultas <b>encuentran las filas</b>. Un doble diria que si a cualquier cosa; un nombre de
 * columna mal escrito, un {@code renaser.} olvidado (el {@code search_path} de Hikari no incluye
 * el esquema) o un {@code IN} que no liga la lista devuelven "nadie la referencia" en silencio —
 * y esa respuesta en silencio es la que borra del bucket la foto de un tercero, para siempre.
 *
 * <p>Las tres referencias del censo, una por prueba:
 * <ul>
 *   <li>{@code medias_publicacion} de OTRA publicacion (nada exige hoy que la clave sea unica);</li>
 *   <li>{@code testimonios.foto_evento_ruta} — {@code publicacion_muro_id} es
 *       {@code ON DELETE SET NULL}, asi que el testimonio sobrevive al borrado con la ruta
 *       congelada y la vitrina publica la sigue firmando;</li>
 *   <li>{@code mensajes.media_ruta} — compartir al chat no copia el archivo, y el borrado del
 *       mensaje es un tombstone que NO libera la clave.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReferenciasDeMediaDelMuroIT {

    @Autowired
    private ReferenciasDeMediaDelMuroPort enCommunity;
    /** El unico implementador de hoy es el adaptador de `chat`; se inyecta por el contrato. */
    @Autowired
    private List<ReferenciasExternasDeMediaDelMuro> enOtrosModulos;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID autorId;
    private UUID publicacionId;
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> publicaciones = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();
    private final List<UUID> testimonios = new ArrayList<>();

    @BeforeEach
    void seed() {
        usuarios.clear();
        publicaciones.clear();
        conversaciones.clear();
        testimonios.clear();
        autorId = nuevoUsuario();
        publicacionId = nuevaPublicacion(autorId);
    }

    @AfterEach
    void limpiar() {
        testimonios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.testimonios WHERE id = ?", id));
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        publicaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.publicaciones_muro WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("una clave que solo usa esta publicacion no la referencia nadie mas")
    void laClaveExclusivaNoAparece() {
        String clave = claveDelMuro();
        nuevaMedia(publicacionId, clave);

        assertThat(enCommunity.referenciadasFueraDe(List.of(clave), PublicacionId.of(publicacionId))).isEmpty();
        assertThat(referenciadasAfuera(List.of(clave))).isEmpty();
    }

    @Test
    @DisplayName("otra publicacion que use la misma clave la retiene")
    void otraPublicacionRetieneLaClave() {
        String clave = claveDelMuro();
        nuevaMedia(publicacionId, clave);
        nuevaMedia(nuevaPublicacion(autorId), clave);

        assertThat(enCommunity.referenciadasFueraDe(List.of(clave), PublicacionId.of(publicacionId)))
                .containsExactly(clave);
    }

    @Test
    @DisplayName("un testimonio promovido congela la portada y la retiene aunque la publicacion se borre")
    void unTestimonioRetieneLaPortada() {
        String clave = claveDelMuro();
        nuevaMedia(publicacionId, clave);
        nuevoTestimonio(autorId, publicacionId, clave);

        // El testimonio apunta a ESTA publicacion, y aun asi retiene: `publicacion_muro_id` es
        // ON DELETE SET NULL, o sea que la fila sobrevive al borrado con `foto_evento_ruta` intacta.
        assertThat(enCommunity.referenciadasFueraDe(List.of(clave), PublicacionId.of(publicacionId)))
                .containsExactly(clave);
    }

    @Test
    @DisplayName("un mensaje que comparte la foto la retiene, y el tombstone del chat no la libera")
    void unMensajeRetieneLaClaveInclusoBorrado() {
        String compartida = claveDelMuro();
        String compartidaYBorrada = claveDelMuro();
        String soloDelMuro = claveDelMuro();
        UUID conversacionId = nuevaConversacionGlobal();
        nuevoMensaje(conversacionId, autorId, compartida, false);
        nuevoMensaje(conversacionId, autorId, compartidaYBorrada, true);

        assertThat(referenciadasAfuera(List.of(compartida, compartidaYBorrada, soloDelMuro)))
                .as("borrar un mensaje es `eliminado_en`, no un DELETE: la fila sigue y urlDeLectura la firma")
                .containsExactlyInAnyOrder(compartida, compartidaYBorrada);
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private List<String> referenciadasAfuera(List<String> rutas) {
        return enOtrosModulos.stream().flatMap(censo -> censo.referenciadas(rutas).stream()).toList();
    }

    private String claveDelMuro() {
        return "muro/fotos/" + autorId + "/" + UUID.randomUUID();
    }

    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Autor del censo', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        usuarios.add(id);
        return id;
    }

    private UUID nuevaPublicacion(UUID autor) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.publicaciones_muro (id, autor_id, texto)
                VALUES (?, ?, 'publicacion del censo')
                """, id, autor);
        publicaciones.add(id);
        return id;
    }

    /** Se va sola con la cascada de `publicaciones_muro`. */
    private void nuevaMedia(UUID publicacion, String ruta) {
        jdbcTemplate.update("""
                INSERT INTO renaser.medias_publicacion (id, publicacion_id, bucket, ruta_storage, mime, orden)
                VALUES (?, ?, 'wall', ?, 'image/jpeg', (
                    SELECT coalesce(max(orden), -1) + 1 FROM renaser.medias_publicacion WHERE publicacion_id = ?))
                """, UUID.randomUUID(), publicacion, ruta, publicacion);
    }

    private void nuevoTestimonio(UUID usuario, UUID publicacion, String fotoEventoRuta) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.testimonios
                    (id, usuario_id, publicacion_muro_id, nombre, foto_evento_ruta, texto, estrellas, destacado)
                VALUES (?, ?, ?, 'Autor del censo', ?, 'un testimonio', 5, true)
                """, id, usuario, publicacion, fotoEventoRuta);
        testimonios.add(id);
    }

    private UUID nuevaConversacionGlobal() {
        // La GLOBAL es unica por indice parcial: si el arranque ya creo una, se reusa.
        List<UUID> existente = jdbcTemplate.queryForList(
                "SELECT id FROM renaser.conversaciones WHERE tipo = 'GLOBAL'", UUID.class);
        if (!existente.isEmpty()) {
            return existente.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, nombre)
                VALUES (?, CAST('GLOBAL' AS renaser.tipo_conversacion), 'Comunidad')
                """, id);
        conversaciones.add(id);
        return id;
    }

    /** Se va solo con la cascada de `conversaciones` o de `usuarios`. */
    private void nuevoMensaje(UUID conversacionId, UUID emisorId, String mediaRuta, boolean eliminado) {
        jdbcTemplate.update("""
                INSERT INTO renaser.mensajes (id, conversacion_id, emisor_id, tipo, texto,
                                              media_bucket, media_ruta, media_mime, eliminado_en)
                VALUES (?, ?, ?, CAST('IMAGEN' AS renaser.tipo_mensaje), 'compartido del Muro',
                        'wall', ?, 'image/jpeg', ?)
                """, UUID.randomUUID(), conversacionId, emisorId, mediaRuta,
                eliminado ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }
}
