package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.RutasDeAlmacenamientoDeCuentaPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El censo de objetos contra Postgres de verdad, que es donde vive el riesgo real de este
 * arreglo.
 *
 * <p><b>Por que hace falta ademas de las pruebas con dobles.</b> Las de
 * {@code AccountDeletionServiceTest} prueban la POLITICA (que se borra y que no) y son las que
 * fijan las dos direcciones del arreglo. Pero no pueden ver el SQL: la primera version de este
 * adaptador consultaba {@code FROM usuarios} sin calificar el esquema —las tablas viven en
 * {@code renaser}— y las once pruebas con mocks pasaban en verde mientras la purga real fallaba
 * entera. Un camino que borra archivos no se firma con dobles nada mas.
 *
 * <p>{@code @Transactional}: todo lo que se siembra se deshace al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RutasDeAlmacenamientoDeCuentaJdbcAdapterIT {

    @Autowired
    private RutasDeAlmacenamientoDeCuentaPort adaptador;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID nuevoUsuario(String nombre, String avatarUrl) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, avatar_url)
                VALUES (?, ?, ?, 'APRENDIZ', 'ACTIVO', ?)
                """, id, nombre + "-" + id + "@renaser.dev", nombre, avatarUrl);
        return id;
    }

    private UUID nuevaPublicacionCon(UUID autorId, String... rutas) {
        UUID publicacionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.publicaciones_muro (id, autor_id, tipo, texto)
                VALUES (?, ?, 'MANUAL', 'una publicacion')
                """, publicacionId, autorId);
        short orden = 0;
        for (String ruta : rutas) {
            jdbc.update("""
                    INSERT INTO renaser.medias_publicacion (publicacion_id, bucket, ruta_storage, mime, orden)
                    VALUES (?, 'wall', ?, 'image/jpeg', ?)
                    """, publicacionId, ruta, orden++);
        }
        return publicacionId;
    }

    private void nuevoMensajeCon(UUID emisorId, String mediaRuta) {
        UUID conversacionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa)
                VALUES (?, 'DIRECTA', ?)
                """, conversacionId, "clave-" + conversacionId);
        jdbc.update("""
                INSERT INTO renaser.mensajes (conversacion_id, emisor_id, tipo, texto, media_bucket, media_ruta, media_mime)
                VALUES (?, ?, 'IMAGEN', 'compartido del Muro', 'wall', ?, 'image/jpeg')
                """, conversacionId, emisorId, mediaRuta);
    }

    private void nuevoTestimonioCon(String fotoEventoRuta, String avatarUrl) {
        jdbc.update("""
                INSERT INTO renaser.testimonios (nombre, texto, estrellas, destacado, foto_evento_ruta, avatar_url)
                VALUES ('Alguien', 'un testimonio', 5, true, ?, ?)
                """, fotoEventoRuta, avatarUrl);
    }

    private void nuevaFirmaDeFase(UUID usuarioId, String rutaFirma) {
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (?)", usuarioId);
        jdbc.update("""
                INSERT INTO renaser.contratos_fase (participante_id, fase, bucket, ruta_firma)
                VALUES (?, 'FASE_2_DESARROLLO', 'onboarding-signatures', ?)
                """, usuarioId, rutaFirma);
    }

    @Test
    void elCensoJuntaLasRutasDeLaCuentaYNoLasDeOtros() {
        UUID purgado = nuevoUsuario("Purgado", "https://bucket.s3.amazonaws.com/avatares/PLACEHOLDER");
        UUID ajeno = nuevoUsuario("Ajeno", null);
        String firma = "firmas/" + purgado + "/fase_2.svg";
        String fotoPropia = "muro/fotos/" + purgado + "/sola";
        String fotoDelOtro = "muro/fotos/" + ajeno + "/suya";
        nuevaFirmaDeFase(purgado, firma);
        nuevaPublicacionCon(purgado, fotoPropia);
        nuevaPublicacionCon(ajeno, fotoDelOtro);

        List<String> censo = adaptador.candidatas(UserId.of(purgado));

        assertThat(censo).contains("avatares/" + purgado, firma, fotoPropia);
        // Lo del otro no entra ni aunque este en el mismo bucket.
        assertThat(censo).doesNotContain(fotoDelOtro);
    }

    @Test
    void elAvatarSoloSeCensaSiLaCuentaTieneFoto() {
        UUID sinFoto = nuevoUsuario("SinFoto", null);

        assertThat(adaptador.candidatas(UserId.of(sinFoto))).doesNotContain("avatares/" + sinFoto);
    }

    /**
     * La direccion que evita el error: compartir al chat NO copia el archivo, referencia la misma
     * clave. Si el que comparte es otra persona, su mensaje no cae con la cascada de
     * {@code emisor_id} y sigue apuntando al objeto despues de la purga.
     */
    @Test
    void unaFotoDelMuroCompartidaPorOtroQuedaMarcadaComoReferenciada() {
        UUID purgado = nuevoUsuario("Purgado", null);
        UUID ajeno = nuevoUsuario("Ajeno", null);
        String compartida = "muro/fotos/" + purgado + "/compartida";
        String sola = "muro/fotos/" + purgado + "/sola";
        nuevaPublicacionCon(purgado, compartida, sola);
        nuevoMensajeCon(ajeno, compartida);

        var referenciadas = adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of(compartida, sola));

        assertThat(referenciadas).containsExactly(compartida);
    }

    /** El mensaje del propio purgado SI cae con la cascada: no es una referencia que sobreviva. */
    @Test
    void elMensajePropioNoCuentaComoReferenciaDeUnTercero() {
        UUID purgado = nuevoUsuario("Purgado", null);
        String suya = "muro/fotos/" + purgado + "/suya";
        nuevaPublicacionCon(purgado, suya);
        nuevoMensajeCon(purgado, suya);

        assertThat(adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of(suya))).isEmpty();
    }

    /** {@code testimonios.usuario_id} es ON DELETE SET NULL: la fila sobrevive a la purga con la
     * foto congelada, y {@code TestimonioService.aVista} la vuelve a firmar en cada listado. */
    @Test
    void unaFotoPromovidaATestimonioQuedaMarcadaComoReferenciada() {
        UUID purgado = nuevoUsuario("Purgado", null);
        String enVitrina = "muro/fotos/" + purgado + "/en-vitrina";
        String sola = "muro/fotos/" + purgado + "/sola";
        nuevaPublicacionCon(purgado, enVitrina, sola);
        nuevoTestimonioCon(enVitrina, null);

        var referenciadas = adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of(enVitrina, sola));

        assertThat(referenciadas).containsExactly(enVitrina);
    }

    /** El avatar se compara por sufijo: el testimonio guarda la URL permanente, no la clave. */
    @Test
    void elAvatarCongeladoEnUnTestimonioQuedaMarcadoComoReferenciado() {
        UUID purgado = nuevoUsuario("Purgado", null);
        String claveAvatar = "avatares/" + purgado;
        nuevoTestimonioCon(null, "https://s3-renaser90dias.s3.us-east-1.amazonaws.com/" + claveAvatar);

        assertThat(adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of(claveAvatar)))
                .containsExactly(claveAvatar);
    }

    @Test
    void sinNadieQueLasMireNoHayRutasReferenciadas() {
        UUID purgado = nuevoUsuario("Purgado", null);
        String sola = "muro/fotos/" + purgado + "/sola";
        nuevaPublicacionCon(purgado, sola);

        assertThat(adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of(sola))).isEmpty();
        assertThat(adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of())).isEmpty();
    }
}
