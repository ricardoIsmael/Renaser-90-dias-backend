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

import java.time.LocalDate;
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
        asegurarParticipante(usuarioId);
        jdbc.update("""
                INSERT INTO renaser.contratos_fase (participante_id, fase, bucket, ruta_firma)
                VALUES (?, 'FASE_2_DESARROLLO', 'onboarding-signatures', ?)
                """, usuarioId, rutaFirma);
    }

    /** {@code participantes_programa} lleva el {@code usuario_id} de PK, asi que se inserta una
     * sola vez por usuario aunque la prueba siembre varias cosas que cuelgan de el. */
    private void asegurarParticipante(UUID usuarioId) {
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id) VALUES (?)
                ON CONFLICT (usuario_id) DO NOTHING
                """, usuarioId);
    }

    /** La bitacora nocturna: {@code audio_ruta} la guarda el servidor tal como viene en el cuerpo
     * ({@code UpsertJournalEntryRequest.audioPath}), sin mirar de quien es el objeto. */
    private void nuevaBitacoraCon(UUID participanteId, String audioRuta) {
        asegurarParticipante(participanteId);
        jdbc.update("""
                INSERT INTO renaser.entradas_diario
                    (participante_id, fecha, tipo, contenido_texto, audio_bucket, audio_ruta)
                VALUES (?, ?, 'BITACORA_NOCTURNA', 'una bitacora', 'renaser-files', ?)
                """, participanteId, java.sql.Date.valueOf(diaLibre()), audioRuta);
    }

    /** El Santuario roto: {@code evidencia_salida_ruta} tampoco se valida
     * ({@code RomperSantuarioRequest.evidenciaRuta} entra directo a {@code sesion.romper}). */
    private void nuevaSesionDeSantuarioCon(UUID participanteId, String evidenciaRuta) {
        asegurarParticipante(participanteId);
        UUID habitoId = jdbc.queryForObject(
                "SELECT id FROM renaser.habitos WHERE ambito = 'SISTEMA' ORDER BY id LIMIT 1", UUID.class);
        UUID registroId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.registros_habito
                    (id, participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia, estado)
                VALUES (?, ?, ?, ?, 10, 'DISCIPLINA', 'FALLIDO')
                """, registroId, participanteId, habitoId, java.sql.Date.valueOf(diaLibre()));
        jdbc.update("""
                INSERT INTO renaser.sesiones_bloqueo
                    (registro_habito_id, estado, iniciada_en, duracion_minima_min,
                     motivo_salida, evidencia_salida_bucket, evidencia_salida_ruta)
                VALUES (?, 'ROTA', now(), 30, 'SALIDA_TEMPRANA', 'renaser-files', ?)
                """, registroId, evidenciaRuta);
    }

    /** Las dos tablas de arriba son UNIQUE por (participante, ..., fecha): cada fila sembrada se
     * lleva un dia distinto para que dos de la misma prueba no choquen. */
    private LocalDate diaLibre() {
        return LocalDate.of(2026, 9, 21).plusDays(diaSembrado++);
    }

    private int diaSembrado;

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

    /**
     * La regresion del 2026-09-21. Nada obliga hoy a que una clave {@code muro/} sea unica, asi
     * que la publicacion de OTRA persona puede usar la misma: esa fila no cae con la cascada de
     * {@code usuarios} —cuelga de {@code publicaciones_muro.autor_id} del otro— y la purga le
     * dejaba la foto en 404 a un tercero que nunca pidio ninguna baja.
     *
     * <p>Muerde en las dos direcciones a proposito: si la rama nueva no estuviera, {@code otra}
     * no aparece (se borraba el objeto de un tercero); si estuviera sin el
     * {@code autor_id <> :usuarioId}, {@code sola} tambien aparece y la purga no borraria nunca
     * una foto del Muro.
     */
    @Test
    void unaFotoUsadaEnLaPublicacionDeOtroQuedaMarcadaComoReferenciada() {
        UUID purgado = nuevoUsuario("Purgado", null);
        UUID ajeno = nuevoUsuario("Ajeno", null);
        String otra = "muro/fotos/" + purgado + "/tambien-en-la-de-otro";
        String sola = "muro/fotos/" + purgado + "/sola";
        nuevaPublicacionCon(purgado, otra, sola);
        nuevaPublicacionCon(ajeno, otra);

        var referenciadas = adaptador.referenciadasPorTerceros(UserId.of(purgado), List.of(otra, sola));

        assertThat(referenciadas).containsExactly(otra);
    }

    /**
     * {@code entradas_diario.audio_ruta} la guarda el servidor tal como viene en el cuerpo, sin
     * validar de quien es el objeto — el barrido de guards del 2026-09-18 no la alcanzo. La
     * bitacora de otro participante sobrevive a esta purga y sigue apuntando al objeto.
     */
    @Test
    void laBitacoraDeOtroParticipanteNoRetieneLaClave() {
        UUID purgado = nuevoUsuario("Purgado", null);
        UUID ajeno = nuevoUsuario("Ajeno", null);
        String enLaBitacoraDeOtro = "evidencia-habitos/" + purgado + "/registro/audio";
        String sola = "evidencia-habitos/" + purgado + "/registro/sola";
        nuevaBitacoraCon(ajeno, enLaBitacoraDeOtro);

        var referenciadas = adaptador.referenciadasPorTerceros(
                UserId.of(purgado), List.of(enLaBitacoraDeOtro, sola));

        // NO la retiene, y es deliberado. Las candidatas ya vienen filtradas por ClavesDeCuenta,
        // asi que esa ruta es demostrablemente del purgado; el servidor nunca emite para una
        // persona una clave que otra pondria legitimamente en su bitacora. Contarla dejaria que
        // cualquiera impida la baja de otro escribiendo una cadena en su propio diario, y la baja
        // de cuenta es un requisito de tienda: retener de mas es el dano que hay que evitar.
        assertThat(referenciadas).isEmpty();
    }

    /** Mismo criterio en {@code sesiones_bloqueo.evidencia_salida_ruta}, la otra columna de ruta
     * que el cliente llena sin que nadie la mire. */
    @Test
    void elSantuarioDeOtroParticipanteNoRetieneLaClave() {
        UUID purgado = nuevoUsuario("Purgado", null);
        UUID ajeno = nuevoUsuario("Ajeno", null);
        String enElSantuarioDeOtro = "dia-sin-celular/" + purgado + "/salida";
        String sola = "dia-sin-celular/" + purgado + "/sola";
        nuevaSesionDeSantuarioCon(ajeno, enElSantuarioDeOtro);

        var referenciadas = adaptador.referenciadasPorTerceros(
                UserId.of(purgado), List.of(enElSantuarioDeOtro, sola));

        // Mismo razonamiento que en la bitacora: columna que el cliente llena sin validar, asi
        // que una referencia cruzada ahi es abuso o es bug, nunca un caso legitimo.
        assertThat(referenciadas).isEmpty();
    }

    /**
     * El otro lado de las tres ramas nuevas: lo del propio purgado cae con la cascada, asi que no
     * es una referencia que sobreviva. Sin este {@code <> :usuarioId} la purga se bloquearia a si
     * misma y no borraria absolutamente nada.
     */
    @Test
    void loPropioNoCuentaComoReferenciaDeUnTercero() {
        UUID purgado = nuevoUsuario("Purgado", null);
        String enSuMuro = "muro/fotos/" + purgado + "/suya";
        String enSuBitacora = "evidencia-habitos/" + purgado + "/registro/suyo";
        String enSuSantuario = "dia-sin-celular/" + purgado + "/suya";
        nuevaPublicacionCon(purgado, enSuMuro);
        nuevaBitacoraCon(purgado, enSuBitacora);
        nuevaSesionDeSantuarioCon(purgado, enSuSantuario);

        assertThat(adaptador.referenciadasPorTerceros(UserId.of(purgado),
                List.of(enSuMuro, enSuBitacora, enSuSantuario))).isEmpty();
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
