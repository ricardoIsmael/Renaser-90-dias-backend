package com.renaser.os.onboarding.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ProcesarValidacionV90UseCase;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.ResultadoValidacionV90;
import com.renaser.os.onboarding.application.ports.out.media.SaveMediaPort;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Prueba de la corrección de C-1 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html)
 * contra Postgres real (Testcontainers) — a propósito SIN {@code @Transactional} de
 * conveniencia a nivel de clase, a diferencia de otros tests de este estilo en el repo
 * (ej. {@code GrabacionV90PersistenceAdapterTest}, {@code AccountDeletionIntegrationTest}):
 * esa anotación abriría una transacción ambiente ANTES de invocar el caso de uso bajo
 * prueba, y entonces {@link TransactionSynchronizationManager#isActualTransactionActive()}
 * daría {@code true} sin importar si {@code procesar()} tiene o no su propio
 * {@code @Transactional} — la prueba pasaría igual con el código viejo (el que causaba
 * C-1) y con el nuevo, que es exactamente la trampa que señala el encargo: "si pasa con
 * los dos, no prueba nada".
 *
 * <p>Por eso se autowirea el caso de uso por su interfaz pública (bean real, con el proxy
 * de Spring) en vez de instanciar {@code ProcesarValidacionV90Service} con {@code new}:
 * sin el proxy, ninguna versión de {@code @Transactional} tendría efecto y la comparación
 * vieja-vs-nueva tampoco significaría nada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcesarValidacionV90ServiceTransaccionIT {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private ProcesarValidacionV90UseCase procesarUseCase;
    @Autowired
    private SaveGrabacionV90Port saveGrabacionPort;
    @Autowired
    private LoadGrabacionV90Port loadGrabacionPort;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private SaveMediaPort saveMediaPort;
    @MockitoBean
    private ValidacionIAPort validacionIAPort;

    private UserId usuarioId;

    /**
     * Inserta la fila de {@code usuarios} directamente por SQL nativo (mismo criterio que
     * {@code GrabacionV90PersistenceAdapterTest}: el módulo {@code onboarding} no puede
     * importar el dominio interno de {@code users}, solo {@code users.api}) — pero en una
     * transacción PROPIA, manejada a mano con {@link TransactionTemplate} y NO con
     * {@code @Transactional} de test: esta última se revertiría sola al terminar el método
     * (o, si se pusiera a nivel de clase, dejaría una transacción ambiente activa durante
     * todo el test, arruinando la aserción central de esta clase).
     */
    @BeforeEach
    void seedUsuario() {
        usuarioId = UserId.of(UUID.randomUUID());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", usuarioId.value())
                .setParameter("email", usuarioId + "@renaser.test")
                .setParameter("nombre", "Fixture " + usuarioId)
                .executeUpdate());
    }

    /** Misma secuencia que {@code GrabacionV90Service.solicitarValidacion} ya deja commiteada
     * antes de despachar el @Async: audio grabado, un intento de validación arrancado
     * (estado PROCESANDO). {@code saveGrabacionPort.guardar} corre en su propia transacción
     * corta (Spring Data JPA), sin necesidad de envolverla acá. */
    private long seedGrabacionProcesando() {
        // grabaciones_v90.media_id tiene FK real contra medias_onboarding: hace falta una
        // fila real, no un id inventado (un mediaId fijo viola grabaciones_v90_media_id_fkey
        // contra Postgres real, a diferencia del test unitario con mocks).
        MediaOnboarding media = MediaOnboarding.registrar(usuarioId, "v90", "v90_mente_0", ClaseMedia.AUDIO,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/" + usuarioId + "/audio/uuid-1", "audio/aac", 2048L,
                null, null, CLOCK);
        long mediaId = saveMediaPort.guardar(media).id();

        GrabacionV90 g = GrabacionV90.crearSlot(usuarioId, "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);
        g.marcarGrabada(mediaId, null, "transcripcion", CLOCK);
        g.procesarIntentoDeValidacion(CLOCK);
        return saveGrabacionPort.guardar(g).id();
    }

    @Test
    @DisplayName("procesar(): la IA se llama SIN ninguna transacción Spring activa")
    void laLlamadaALaIaOcurreFueraDeLaTransaccion() {
        long grabacionId = seedGrabacionProcesando();
        AtomicBoolean transaccionActivaDuranteLaIa = new AtomicBoolean(true);
        when(validacionIAPort.validar(any())).thenAnswer(inv -> {
            transaccionActivaDuranteLaIa.set(TransactionSynchronizationManager.isActualTransactionActive());
            return ResultadoValidacionV90.noDisponible();
        });

        procesarUseCase.procesar(usuarioId, grabacionId);

        assertThat(transaccionActivaDuranteLaIa).isFalse();
    }

    @Test
    @DisplayName("procesar(): el veredicto se persiste igual que antes, con la IA fuera de la transacción")
    void persisteElVeredictoDeLaIa() {
        long grabacionId = seedGrabacionProcesando();
        when(validacionIAPort.validar(any()))
                .thenReturn(new ResultadoValidacionV90(ResultadoValidacionV90.Estado.APROBADA, "{\"ok\":true}"));

        procesarUseCase.procesar(usuarioId, grabacionId);

        GrabacionV90 recargada = loadGrabacionPort.porId(grabacionId).orElseThrow();
        assertThat(recargada.estadoIa()).isEqualTo(EstadoIAv90.APROBADA);
    }

    @Test
    @DisplayName("procesar(): si la IA lanza, el registro NO queda atrapado en PROCESANDO")
    void unFalloDeLaIaNoDejaElRegistroAtrapado() {
        long grabacionId = seedGrabacionProcesando();
        when(validacionIAPort.validar(any())).thenThrow(new RuntimeException("timeout simulado de Gemini"));

        procesarUseCase.procesar(usuarioId, grabacionId);

        GrabacionV90 recargada = loadGrabacionPort.porId(grabacionId).orElseThrow();
        assertThat(recargada.estadoIa()).isNotEqualTo(EstadoIAv90.PROCESANDO);
        // Un solo intento consumido, todavia quedan 2 de los 3 -> vuelve a PENDIENTE (reintentable),
        // no cae todavia a REVISION_MANUAL.
        assertThat(recargada.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
    }
}
