package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RespuestasPorClaveJdbcAdapter#mediaDe} contra Postgres real (D-253): el {@code media_id} de la
 * respuesta de ESE usuario a ESA clave, y nada más. Usa las preguntas del catálogo que siembra V10.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RespuestasPorClaveJdbcAdapterTest {

    @Autowired
    private RespuestasPorClaveJdbcAdapter adapter;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;

    private UserId ana;
    private UserId luis;

    @BeforeEach
    void seed() {
        ana = usuario();
        luis = usuario();
    }

    @Test
    @DisplayName("mediaDe(): devuelve el media_id de la respuesta de esa persona a esa clave")
    void devuelveElMediaDeLaRespuesta() {
        long firmaDelPacto = media(ana);
        long firmaDeTerminos = media(ana);
        responder(ana, "signature", firmaDelPacto);
        responder(ana, "terms_signature", firmaDeTerminos);

        assertThat(adapter.mediaDe(ana, "signature")).contains(firmaDelPacto);
        assertThat(adapter.mediaDe(ana, "terms_signature")).contains(firmaDeTerminos);
    }

    @Test
    @DisplayName("mediaDe(): vacío sin respuesta, con la respuesta de otra persona o con una clave inexistente")
    void vacioSiNoHayRespuestaPropia() {
        responder(ana, "signature", media(ana));

        assertThat(adapter.mediaDe(luis, "signature")).isEmpty();
        assertThat(adapter.mediaDe(ana, "no_existe")).isEmpty();
    }

    @Test
    @DisplayName("mediaDe(): vacío si el archivo se borró (el FK deja media_id en NULL)")
    void vacioSiElArchivoSeBorro() {
        long firma = media(ana);
        responder(ana, "signature", firma);
        jdbcTemplate.update("DELETE FROM renaser.medias_onboarding WHERE id = ?", firma);

        assertThat(adapter.mediaDe(ana, "signature")).isEmpty();
    }

    // --- textosDeVarios: el WhatsApp que ve Administración (D-320, issue #78) ---

    @Test
    @DisplayName("textosDeVarios(): el texto de la clave de VARIAS personas; no aparece quien no respondió ni lo de otra clave, y nada de las demás personas")
    void textosDeVariasPersonas() {
        UserId sinRespuesta = usuario();
        responderTexto(ana, "whatsapp", "+51 987654321");
        responderTexto(luis, "whatsapp", "+34 600111222");
        responderTexto(luis, "city", "Lima");
        UserId ajena = usuario();
        responderTexto(ajena, "whatsapp", "+51 900000000");

        Map<UserId, String> textos = adapter.textosDeVarios(List.of(ana, luis, sinRespuesta), "whatsapp");

        assertThat(textos).containsOnly(Map.entry(ana, "+51 987654321"), Map.entry(luis, "+34 600111222"));
        assertThat(adapter.textosDeVarios(List.of(luis), "city")).containsOnly(Map.entry(luis, "Lima"));
        assertThat(adapter.textosDeVarios(List.of(ana), "no_existe")).isEmpty();
        assertThat(adapter.textosDeVarios(List.of(), "whatsapp")).isEmpty();
    }

    @Test
    @DisplayName("textosDeVarios(): una respuesta sin texto (solo escala o archivo) no cuenta")
    void unaRespuestaSinTextoNoCuenta() {
        responder(ana, "signature", media(ana));

        assertThat(adapter.textosDeVarios(List.of(ana), "signature")).isEmpty();
    }

    @Test
    @DisplayName("textosDeVarios(): UNA sola sentencia SQL, con 2 personas o con 60 (sin consulta por persona) y devuelve a todas")
    void unaSolaSentenciaSinImportarCuantasPersonas() {
        AtomicInteger sentencias = new AtomicInteger();
        RespuestasPorClaveJdbcAdapter contado = new RespuestasPorClaveJdbcAdapter(JdbcClient.create(contando(sentencias)));
        List<UserId> muchas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            UserId persona = usuario();
            responderTexto(persona, "whatsapp", "+51 9" + String.format("%08d", i));
            muchas.add(persona);
        }

        sentencias.set(0);
        assertThat(contado.textosDeVarios(muchas.subList(0, 2), "whatsapp")).hasSize(2);
        assertThat(sentencias.get()).as("sentencias con 2 personas").isEqualTo(1);

        sentencias.set(0);
        assertThat(contado.textosDeVarios(muchas, "whatsapp")).hasSize(60);
        assertThat(sentencias.get()).as("sentencias con 60 personas").isEqualTo(1);
    }

    /**
     * El mismo DataSource de la prueba, EN LA MISMA transacción (si no, no vería lo sembrado sin confirmar), contando cada
     * sentencia que se prepara.
     */
    private DataSource contando(AtomicInteger sentencias) {
        DataSource enLaTransaccion = new TransactionAwareDataSourceProxy(dataSource);
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, metodo, args) -> {
                    Object resultado = invocar(metodo, enLaTransaccion, args);
                    if (resultado instanceof Connection conexion) {
                        return contandoConexion(conexion, sentencias);
                    }
                    return resultado;
                });
    }

    private Connection contandoConexion(Connection conexion, AtomicInteger sentencias) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("prepareStatement") || metodo.getName().equals("createStatement")) {
                        sentencias.incrementAndGet();
                    }
                    return invocar(metodo, conexion, args);
                });
    }

    private static Object invocar(java.lang.reflect.Method metodo, Object destino, Object[] args) throws Throwable {
        try {
            return metodo.invoke(destino, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void responderTexto(UserId usuario, String clave, String texto) {
        jdbcTemplate.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto)
                SELECT ?, id, ? FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?
                """, usuario.value(), texto, clave);
    }

    private UserId usuario() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        return UserId.of(id);
    }

    private long media(UserId dueno) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO renaser.medias_onboarding (usuario_id, clase, bucket, ruta_storage, mime)
                VALUES (?, 'firma', 'onboarding-media', ?, 'image/png') RETURNING id
                """, Long.class, dueno.value(), "onboarding/" + dueno + "/firma/" + UUID.randomUUID());
    }

    private void responder(UserId usuario, String clave, long mediaId) {
        jdbcTemplate.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, media_id)
                SELECT ?, id, ? FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?
                """, usuario.value(), mediaId, clave);
    }
}
