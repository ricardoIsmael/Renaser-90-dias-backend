package com.renaser.os.leadership;

import com.renaser.os.leadership.application.ports.out.GuardarDocumentoSemanalPort;
import com.renaser.os.leadership.application.ports.out.LeerDocumentoSemanalPort;
import com.renaser.os.leadership.application.ports.out.LeerDocumentoSemanalPort.Buscado;
import com.renaser.os.leadership.domain.model.documento.DescargaDeDocumento;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal.Contenido;
import com.renaser.os.leadership.domain.model.documento.TipoDeDocumento;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las dos tablas de la V199 (spec 056, FR-039 y FR-040) con Postgres de verdad: se guarda un documento una sola vez aunque lo pidan dos
 * a la vez, cada pedido deja su fila y el borrado de una cuenta (D-243) se lleva los documentos de esa persona.
 */
class DocumentosSemanalesIT extends EscenarioDelLiderIT {

    private static final String HUELLA = "a".repeat(64);
    private static final LocalDate SABADO = LocalDate.of(2026, 11, 14);
    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 20);

    @Autowired
    GuardarDocumentoSemanalPort guardar;
    @Autowired
    LeerDocumentoSemanalPort leer;
    @Autowired
    Clock reloj;
    @Autowired
    List<BorradoDeDatosDeCuenta> borrados;

    private final List<UUID> personas = new ArrayList<>();

    @AfterEach
    void limpiarLosDocumentos() {
        personas.add(ana);
        personas.add(mentora);
        personas.forEach(persona -> {
            jdbc.update("DELETE FROM renaser.descargas_de_documentos WHERE documento_id IN "
                    + "(SELECT id FROM renaser.documentos_semanales WHERE persona_id = ?)", persona);
            jdbc.update("DELETE FROM renaser.documentos_semanales WHERE persona_id = ?", persona);
        });
    }

    private DocumentoSemanal documento(UUID persona, TipoDeDocumento tipo, String huella) {
        personas.add(persona);
        return DocumentoSemanal.generar(UUID.randomUUID(),
                new Contenido(UserId.of(persona), tipo, SABADO, VIERNES, huella, UserId.of(lider)), reloj);
    }

    private Buscado buscado(DocumentoSemanal documento) {
        return new Buscado(documento.personaId(), documento.tipo(), documento.periodoInicio(), documento.huella());
    }

    @Test
    @DisplayName("spec 056 FR-040: la V199 crea documentos_semanales y descargas_de_documentos con las columnas del modelo de datos")
    void lasDosTablas() {
        List<String> documentos = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'renaser' AND table_name = 'documentos_semanales'", String.class);
        List<String> descargas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'renaser' AND table_name = 'descargas_de_documentos'", String.class);

        assertThat(documentos).containsExactlyInAnyOrder("id", "persona_id", "tipo", "periodo_inicio", "periodo_fin", "huella", "ruta",
                "generado_en", "generado_por");
        assertThat(descargas).containsExactlyInAnyOrder("id", "documento_id", "actor_id", "reutilizado", "pedido_en");
    }

    @Test
    @DisplayName("spec 056 FR-039: lo que se guarda se lee igual por persona, tipo, período y huella; con otra huella o tipo no hay nada")
    void loQueSeGuardaSeLee() {
        DocumentoSemanal guardado = guardar.insertar(documento(ana, TipoDeDocumento.APRENDIZ, HUELLA));

        DocumentoSemanal leido = leer.existente(buscado(guardado)).orElseThrow();

        assertThat(leido.id()).isEqualTo(guardado.id());
        assertThat(leido.ruta()).isEqualTo("reportes/" + ana + "/2026-11-14-APRENDIZ-aaaaaaaa.html");
        assertThat(leido.generadoEn()).isEqualTo(guardado.generadoEn());
        assertThat(leido.generadoPor()).isEqualTo(UserId.of(lider));
        assertThat(leido.periodoFin()).isEqualTo(VIERNES);
        assertThat(leer.existente(new Buscado(leido.personaId(), TipoDeDocumento.APRENDIZ, SABADO, "b".repeat(64)))).isEmpty();
        assertThat(leer.existente(new Buscado(leido.personaId(), TipoDeDocumento.MENTOR, SABADO, HUELLA))).isEmpty();
        assertThat(leer.existente(new Buscado(leido.personaId(), TipoDeDocumento.APRENDIZ, SABADO.plusDays(7), HUELLA))).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-039: si los datos cambian sale otro documento con otra huella y el anterior se queda: se guardan para siempre")
    void cadaHuellaEsOtroDocumento() {
        DocumentoSemanal primero = guardar.insertar(documento(ana, TipoDeDocumento.APRENDIZ, HUELLA));
        DocumentoSemanal segundo = guardar.insertar(documento(ana, TipoDeDocumento.APRENDIZ, "b".repeat(64)));

        assertThat(segundo.id()).isNotEqualTo(primero.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.documentos_semanales WHERE persona_id = ?", Long.class, ana)).isEqualTo(2L);
    }

    @Test
    @DisplayName("spec 056 FR-039: dos pedidos a la vez del mismo documento dejan UNA fila y los dos reciben el mismo, sin error")
    void dosPedidosALaVez() throws Exception {
        DocumentoSemanal uno = documento(ana, TipoDeDocumento.APRENDIZ, HUELLA);
        DocumentoSemanal otro = documento(ana, TipoDeDocumento.APRENDIZ, HUELLA);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<DocumentoSemanal> primero = () -> {
                salida.await();
                return guardar.insertar(uno);
            };
            Callable<DocumentoSemanal> segundo = () -> {
                salida.await();
                return guardar.insertar(otro);
            };
            Future<DocumentoSemanal> a = hilos.submit(primero);
            Future<DocumentoSemanal> b = hilos.submit(segundo);
            salida.countDown();

            assertThat(a.get().id()).isEqualTo(b.get().id());
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.documentos_semanales WHERE persona_id = ?", Long.class, ana)).isEqualTo(1L);
    }

    @Test
    @DisplayName("spec 056 FR-040: cada pedido deja su fila de descarga con quién, cuándo y si el documento se reutilizó")
    void cadaPedidoQuedaRegistrado() {
        DocumentoSemanal guardado = guardar.insertar(documento(ana, TipoDeDocumento.APRENDIZ, HUELLA));

        guardar.registrarDescarga(DescargaDeDocumento.registrar(UUID.randomUUID(), guardado, UserId.of(lider), false, reloj));
        guardar.registrarDescarga(DescargaDeDocumento.registrar(UUID.randomUUID(), guardado, UserId.of(mentora), true, reloj));

        List<java.util.Map<String, Object>> filas = jdbc.queryForList(
                "SELECT actor_id, reutilizado FROM renaser.descargas_de_documentos WHERE documento_id = ? ORDER BY reutilizado", guardado.id());
        assertThat(filas).extracting(f -> f.get("actor_id")).containsExactly(lider, mentora);
        assertThat(filas).extracting(f -> f.get("reutilizado")).containsExactly(false, true);
        assertThat(jdbc.queryForObject("SELECT pedido_en FROM renaser.descargas_de_documentos WHERE actor_id = ?",
                java.sql.Timestamp.class, lider).toInstant()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("spec 056 FR-040: la base no deja una descarga de un documento que no existe ni una huella que no es un SHA-256")
    void laBaseCuidaLoSuyo() {
        DocumentoSemanal guardado = documento(ana, TipoDeDocumento.APRENDIZ, HUELLA);

        assertThatThrownBy(() -> guardar.registrarDescarga(DescargaDeDocumento.registrar(UUID.randomUUID(), guardado, UserId.of(lider), false, reloj)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.documentos_semanales (id, persona_id, tipo, periodo_inicio, periodo_fin, "
                + "huella, ruta, generado_en, generado_por) VALUES (?, ?, 'APRENDIZ', DATE '2026-11-14', DATE '2026-11-20', ?, 'x', now(), ?)",
                UUID.randomUUID(), ana, "A".repeat(64), lider)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.documentos_semanales (id, persona_id, tipo, periodo_inicio, periodo_fin, "
                + "huella, ruta, generado_en, generado_por) VALUES (?, ?, 'OTRO', DATE '2026-11-14', DATE '2026-11-20', ?, 'x', now(), ?)",
                UUID.randomUUID(), ana, HUELLA, lider)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("spec 056 FR-040: al borrar para siempre una cuenta (D-243) se van sus documentos y las descargas, y se avisa de sus archivos")
    void alBorrarLaCuentaSeVanSusDocumentos() {
        DocumentoSemanal deAna = guardar.insertar(documento(ana, TipoDeDocumento.APRENDIZ, HUELLA));
        DocumentoSemanal deLaMentora = guardar.insertar(documento(mentora, TipoDeDocumento.MENTOR, HUELLA));
        guardar.registrarDescarga(DescargaDeDocumento.registrar(UUID.randomUUID(), deAna, UserId.of(lider), false, reloj));
        guardar.registrarDescarga(DescargaDeDocumento.registrar(UUID.randomUUID(), deLaMentora, UserId.of(ana), false, reloj));
        BorradoDeDatosDeCuenta delLiderazgo = borrados.stream()
                .filter(b -> b.getClass().getSimpleName().equals("BorradoDeCuentaEnLeadershipAdapter")).findFirst().orElseThrow();

        Set<String> archivos = delLiderazgo.archivosDe(UserId.of(ana));
        delLiderazgo.borrarDatosDe(UserId.of(ana));

        assertThat(archivos).containsExactly(deAna.ruta());
        assertThat(leer.existente(buscado(deAna))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.descargas_de_documentos WHERE documento_id = ?", Long.class, deAna.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.descargas_de_documentos WHERE actor_id = ?", Long.class, ana))
                .as("lo que Ana descargó de otra persona tampoco la nombra").isZero();
        assertThat(leer.existente(buscado(deLaMentora))).as("el documento de otra persona se queda").isPresent();
    }
}
