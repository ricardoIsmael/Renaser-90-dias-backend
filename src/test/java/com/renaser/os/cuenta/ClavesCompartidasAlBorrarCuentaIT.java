package com.renaser.os.cuenta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los casos del borrado de una cuenta (D-243) en los que la persona comparte algo con otra: los chats de
 * dos, los de grupo, las claves de archivo que nombra otra fila y los testimonios. Postgres de verdad.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ClavesCompartidasAlBorrarCuentaIT {

    @Autowired
    private List<BorradoDeDatosDeCuenta> borrados;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID persona;
    private UUID otra;

    @BeforeEach
    void sembrar() {
        SemillaDeCuenta semilla = new SemillaDeCuenta(jdbc);
        otra = semilla.nuevaPersona();
        persona = semilla.nuevaPersona();
    }

    @Test
    @DisplayName("chat: el directo con otra se borra entero (también lo que escribió la otra) y su archivo se reporta")
    void elChatDirectoSeBorraEntero() {
        UUID directo = conversacion("DIRECTA", null, claveDirecta(persona, otra));
        participa(directo, persona);
        participa(directo, otra);
        mensaje(directo, persona, null, null);
        mensaje(directo, otra, "chat/" + directo + "/de-la-otra.jpg", null);

        assertThat(modulo("Chat").archivosDe(UserId.of(persona))).contains("chat/" + directo + "/de-la-otra.jpg");
        modulo("Chat").borrarDatosDe(UserId.of(persona));

        assertThat(contar("SELECT count(*) FROM renaser.conversaciones WHERE id = ?", directo)).isZero();
        assertThat(contar("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?", directo)).isZero();
        assertThat(contar("SELECT count(*) FROM renaser.conversaciones WHERE clave_directa = ?", "soporte:" + persona))
                .as("su soporte").isZero();
        assertThat(contar("SELECT count(*) FROM renaser.conversaciones WHERE clave_directa = ?", "soporte:" + otra))
                .as("el soporte de la otra").isOne();
    }

    @Test
    @DisplayName("chat: en el grupo se van sus mensajes y su lugar; la respuesta de otra queda sin el mensaje citado")
    void enElGrupoSoloSeVaLoSuyo() {
        UUID grupo = conversacion("CELULA", jdbc.queryForObject(
                "SELECT id FROM renaser.celulas WHERE mentor_id = ?", UUID.class, otra), null);
        participa(grupo, persona);
        participa(grupo, otra);
        UUID suyo = mensaje(grupo, persona, null, null);
        UUID respuesta = mensaje(grupo, otra, null, suyo);

        modulo("Chat").borrarDatosDe(UserId.of(persona));

        assertThat(contar("SELECT count(*) FROM renaser.mensajes WHERE id = ?", suyo)).isZero();
        assertThat(contar("SELECT count(*) FROM renaser.mensajes WHERE id = ? AND respuesta_a_id IS NULL", respuesta))
                .isOne();
        assertThat(contar("SELECT count(*) FROM renaser.participantes_conversacion WHERE conversacion_id = ?", grupo))
                .as("solo queda la otra").isOne();
    }

    @Test
    @DisplayName("chat y Muro: la foto suya que la otra compartió al chat o publicó sigue en uso; su avatar en un testimonio ajeno, también")
    void clavesQueOtrasFilasSiguenNombrando() {
        String fotoDelMuro = "muro/fotos/" + persona + "/foto.jpg";
        String avatar = "avatares/" + persona;
        UUID global = conversacion("GLOBAL", null, null);
        mensaje(global, otra, fotoDelMuro, null);
        jdbc.update("UPDATE renaser.medias_publicacion SET ruta_storage = ? WHERE publicacion_id = "
                + "(SELECT id FROM renaser.publicaciones_muro WHERE autor_id = ?)", fotoDelMuro, otra);
        jdbc.update("UPDATE renaser.testimonios SET avatar_url = ? WHERE usuario_id = ?",
                "https://cdn.renaser.test/" + avatar, otra);
        Set<String> claves = Set.of(fotoDelMuro, avatar, "muro/fotos/" + persona + "/nadie-la-usa.jpg");

        assertThat(modulo("Chat").archivosEnUsoTrasBorrar(UserId.of(persona), claves)).containsExactly(fotoDelMuro);
        assertThat(modulo("Community").archivosEnUsoTrasBorrar(UserId.of(persona), claves))
                .containsExactlyInAnyOrder(fotoDelMuro, avatar);
    }

    @Test
    @DisplayName("Muro: la foto de su testimonio no retiene si el testimonio es de ella; la de un testimonio ajeno sí")
    void testimonioSuyoNoRetieneYAjenoSi() {
        String suFoto = "muro/fotos/" + persona + "/foto.jpg";
        assertThat(modulo("Community").archivosEnUsoTrasBorrar(UserId.of(persona), Set.of(suFoto))).isEmpty();

        jdbc.update("UPDATE renaser.testimonios SET foto_evento_ruta = ? WHERE usuario_id = ?", suFoto, otra);
        assertThat(modulo("Community").archivosEnUsoTrasBorrar(UserId.of(persona), Set.of(suFoto)))
                .containsExactly(suFoto);
    }

    @Test
    @DisplayName("Muro: el testimonio cargado a mano por un Admin no se borra con su cuenta; solo pierde el autor")
    void testimonioAManoDeUnAdminSobrevive() {
        UUID aMano = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.testimonios (id, usuario_id, nombre, texto, estrellas) "
                + "VALUES (?, ?, 'Ana Pérez', 'Me cambió la vida', 5)", aMano, persona);
        jdbc.update("INSERT INTO renaser.reacciones_muro (publicacion_id, usuario_id, tipo) "
                + "SELECT id, ?, 'ME_GUSTA' FROM renaser.publicaciones_muro WHERE autor_id = ?", otra, persona);

        modulo("Community").borrarDatosDe(UserId.of(persona));

        assertThat(contar("SELECT count(*) FROM renaser.testimonios WHERE id = ? AND usuario_id IS NULL", aMano))
                .isOne();
        assertThat(contar("SELECT count(*) FROM renaser.testimonios WHERE usuario_id = ?", persona)).isZero();
        assertThat(contar("SELECT count(*) FROM renaser.reacciones_muro WHERE usuario_id = ?", otra))
                .as("la reacción de la otra a SU publicación cae con la publicación").isOne();
    }

    @Test
    @DisplayName("habits: que la otra escriba en SU bitácora la clave del audio de la persona no la retiene (veto)")
    void bitacoraAjenaNoRetiene() {
        String audio = "bitacora/" + persona + "/audio.m4a";
        jdbc.update("UPDATE renaser.entradas_diario SET audio_ruta = ? WHERE participante_id = ?", audio, otra);

        assertThat(modulo("Habits").archivosDe(UserId.of(persona))).contains(audio);
        assertThat(modulo("Habits").archivosEnUsoTrasBorrar(UserId.of(persona), Set.of(audio))).isEmpty();
    }

    private BorradoDeDatosDeCuenta modulo(String nombre) {
        return borrados.stream()
                .filter(b -> b.getClass().getSimpleName().equals("BorradoDeCuentaEn" + nombre + "Adapter"))
                .findFirst().orElseThrow();
    }

    private UUID conversacion(String tipo, UUID celula, String clave) {
        // La comunidad es una sola (`conversacion_global_unica_uk`) y el Postgres de los IT se comparte entre
        // clases: si otra ya la creó, se usa esa. Sembrarla siempre dependía del orden y rompió el CD (E-497).
        if ("GLOBAL".equals(tipo)) {
            java.util.List<UUID> existente = jdbc.queryForList(
                    "SELECT id FROM renaser.conversaciones WHERE tipo = 'GLOBAL'", UUID.class);
            if (!existente.isEmpty()) {
                return existente.get(0);
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, celula_id, clave_directa) "
                + "VALUES (?, CAST(? AS renaser.tipo_conversacion), ?, ?)", id, tipo, celula, clave);
        return id;
    }

    private void participa(UUID conversacion, UUID usuario) {
        jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, usuario);
    }

    private UUID mensaje(UUID conversacion, UUID emisor, String media, UUID respuestaA) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.mensajes (id, conversacion_id, emisor_id, tipo, texto, media_bucket, "
                        + "media_ruta, respuesta_a_id) VALUES (?, ?, ?, 'TEXTO', 'hola', ?, ?, ?)",
                id, conversacion, emisor, media == null ? null : "renaser", media, respuestaA);
        return id;
    }

    private long contar(String sql, Object parametro) {
        return jdbc.queryForObject(sql, Long.class, parametro);
    }

    private static String claveDirecta(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) <= 0 ? sa + "_" + sb : sb + "_" + sa;
    }
}
