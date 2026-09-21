package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase.RequestAccountDeletionCommand;
import com.renaser.os.users.application.ports.out.accountrequest.DeleteAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.RutasDeAlmacenamientoDeCuentaPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Postgres real (Testcontainers), no mocks — es la prueba que exige el encargo:
 * "crear cuenta -> dar de baja -> purgar -> registrar de nuevo con el mismo email -> funciona".
 *
 * <p>Demuestra que el bug documentado del backend viejo NO se repite aca: alli, borrar una
 * cuenta no liberaba el email porque `wipeTraineeData` no tocaba ni `trainee_profiles` ni
 * `users` ni `account_requests`, y habia que borrar las 4 piezas a mano (ver
 * features/account-deletion/repository.ts#borrarCuenta).
 *
 * <p><b>El alta va por el camino REAL (2026-09-21), y esa es la correccion que hace valer esta
 * prueba.</b> Hasta hoy la cuenta se creaba con {@code saveUserPort.save(User.registerTrainee(...))},
 * que escribe UNICAMENTE `usuarios`. Pero el alta de verdad
 * ({@code AccountRequestService.submit}, tanto por formulario como por proveedor social) escribe
 * ADEMAS la fila de `solicitudes_cuenta`, y esa fila no cuelga de `usuarios` por ninguna FK
 * borrable: `usuario_id` no tiene FK y las dos que la tienen son ON DELETE SET NULL. O sea que
 * la prueba esquivaba justo la tabla que su propio javadoc nombraba ({@code account_requests}),
 * y por eso el defecto del backend viejo seguia vivo aca —correo, nombre, IP, ciudad, telefono y
 * sujeto del proveedor sobrevivian a la baja, y el correo no volvia a quedar libre— pasando la
 * suite en verde. Ahora se da el alta con submit + approve, que es la unica forma de que la
 * prueba vea el estado que deja una cuenta de autoservicio.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountDeletionIntegrationTest {

    private static final int DIAS_DE_GRACIA = 14;

    @Autowired
    private LoadUserPort loadUserPort;
    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private DeleteUserPort deleteUserPort;
    @Autowired
    private DeleteAccountRequestPort deleteAccountRequestPort;
    @Autowired
    private LoadAccountRequestPort loadAccountRequestPort;
    @Autowired
    private RutasDeAlmacenamientoDeCuentaPort rutasDeAlmacenamientoPort;
    @Autowired
    private AlmacenamientoPort almacenamientoPort;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private SubmitAccountRequestUseCase submitAccountRequest;
    @Autowired
    private ApproveAccountRequestUseCase approveAccountRequest;
    @Autowired
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void crearBajaPurgarYRegistrarDeNuevoConElMismoEmailFunciona() {
        String email = "purgado-" + UUID.randomUUID() + "@renaser.dev";
        FixedClock enElAlta = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId primeraCuentaId = altaRealAprobada(email, "Primera Cuenta");

        // La cuenta de autoservicio nace con DOS filas, no una: este es el estado que la version
        // vieja de esta prueba nunca llegaba a crear.
        assertThat(filasDeSolicitudPara(email)).isEqualTo(1);
        assertThat(loadAccountRequestPort.existePorEmail(new Email(email))).isTrue();

        var requestService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                deleteAccountRequestPort, rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), transactionManager, enElAlta, DIAS_DE_GRACIA);
        var estado = requestService.request(new RequestAccountDeletionCommand(primeraCuentaId, "ELIMINAR"));
        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(loadUserPort.byId(primeraCuentaId)).isPresent();

        // Todavia dentro de la gracia: el cron no la toca.
        var purgaTemprana = requestService.purgeExpired();
        assertThat(purgaTemprana.purgadas()).isZero();
        assertThat(loadUserPort.byId(primeraCuentaId)).isPresent();
        assertThat(filasDeSolicitudPara(email)).isEqualTo(1);

        // 15 dias despues (gracia de 14 ya vencida).
        FixedClock quinceDiasDespues = FixedClock.at(enElAlta.now().plusSeconds(15L * 24 * 60 * 60));
        var purgeService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                deleteAccountRequestPort, rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), transactionManager, quinceDiasDespues, DIAS_DE_GRACIA);

        var resultadoPurga = purgeService.purgeExpired();

        assertThat(resultadoPurga.purgadas()).isEqualTo(1);
        assertThat(resultadoPurga.fallidas()).isZero();
        assertThat(loadUserPort.byId(primeraCuentaId)).isEmpty();
        assertThat(loadUserPort.byEmail(new Email(email))).isEmpty();

        // LA asercion del arreglo: en la base primaria no puede quedar NADA de esa persona. La
        // fila de `solicitudes_cuenta` guardaba correo, nombre completo, el UUID de la cuenta ya
        // borrada, la IP del registro y —cuando se dieron— telefono, ciudad y sujeto del
        // proveedor. Contra el codigo de antes del 2026-09-21 esta linea da 1.
        assertThat(filasDeSolicitudPara(email)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM renaser.solicitudes_cuenta WHERE usuario_id = ?",
                Integer.class, primeraCuentaId.value())).isZero();

        // Y el oraculo publico se apaga: POST /api/v1/account-requests/exists (permitAll) resuelve
        // por aca, asi que mientras la fila viviera le confirmaba a un anonimo que esa persona
        // habia sido usuaria de Renaser.
        assertThat(loadAccountRequestPort.existePorEmail(new Email(email))).isFalse();

        // El email tiene que estar libre para un alta nueva, y por el camino real: es
        // `rejectIfEmailYaRegistrado` —el mismo `existsByEmail`— el que la bloqueaba.
        UserId segundaCuentaId = altaRealAprobada(email, "Segunda Cuenta");
        assertThat(loadUserPort.byId(segundaCuentaId)).isPresent();
        assertThat(loadUserPort.byEmail(new Email(email)).orElseThrow().id()).isEqualTo(segundaCuentaId);
    }

    /**
     * El alta como ocurre en produccion: {@code submit} (que escribe `usuarios` Y
     * `solicitudes_cuenta` en la misma transaccion) y despues {@code approve}, que es lo que deja
     * la cuenta ACTIVE — sin eso no se puede pedir la baja, que exige un usuario activo.
     *
     * <p>El token de verificacion se planta con el mismo puerto que usa el flujo real
     * ({@code VerificacionEmailService} lo emite cuando la persona confirma su codigo de 6
     * digitos); no hay forma de saltearlo, {@code submit} lo exige.
     */
    private UserId altaRealAprobada(String email, String nombre) {
        String token = tokenVerificacionEmailPort.generar(email, Duration.ofMinutes(30));
        // IP distinta en cada alta: el limitador por IP de `submit` vive en Redis, que sobrevive a
        // la transaccion de la prueba (no se deshace con el rollback). Con una IP fija, correr la
        // suite muchas veces contra el mismo contenedor terminaria chocando el limite por hora y
        // haciendo fallar una prueba que no tiene nada que ver con eso.
        String ip = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 255);
        AccountRequestId solicitudId = submitAccountRequest.submit(SubmitAccountRequestCommand.porFormulario(
                email, nombre, "+51 999 888 777", "Lima", token, "Secreta123!clave", ip));
        approveAccountRequest.approve(new ApproveAccountRequestCommand(solicitudId, unAdminActivo()));
        return loadUserPort.byEmail(new Email(email)).orElseThrow().id();
    }

    /** {@code AccountRequest.approve} exige un actor que pueda gestionar roles (ADMIN/ALCHEMIST). */
    private UserId unAdminActivo() {
        UserId adminId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.rehydrate(adminId, new Email("admin-" + adminId + "@renaser.dev"),
                UserRole.ADMIN, UserStatus.ACTIVE, "Admin Que Aprueba", null, null, null, null));
        return adminId;
    }

    private int filasDeSolicitudPara(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.solicitudes_cuenta WHERE email = ?",
                Integer.class, email);
    }

    @Test
    void cancelarLaBajaAntesDeQueVenzaLaGraciaEvitaLaPurga() {
        String email = "cancelada-" + UUID.randomUUID() + "@renaser.dev";
        FixedClock clock = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId userId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.registerTrainee(userId, new Email(email), "Se Arrepiente"));
        var service = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                deleteAccountRequestPort, rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), transactionManager, clock, DIAS_DE_GRACIA);
        service.request(new RequestAccountDeletionCommand(userId, "ELIMINAR"));

        service.cancel(userId);

        FixedClock muchoDespues = FixedClock.at(clock.now().plusSeconds(30L * 24 * 60 * 60));
        var purgeService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                deleteAccountRequestPort, rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), transactionManager, muchoDespues, DIAS_DE_GRACIA);

        var resultado = purgeService.purgeExpired();

        assertThat(resultado.purgadas()).isZero();
        assertThat(loadUserPort.byId(userId)).isPresent();
    }

    /**
     * La prueba de punta a punta del arreglo, con Postgres de verdad: politica y SQL juntos.
     *
     * <p>Las dos direcciones en una sola corrida — lo exclusivo del ex usuario se borra del
     * bucket, y la foto que otra persona comparte en su chat NO, porque compartir al Muro no
     * copia el archivo: referencia la misma clave, y el mensaje de esa otra persona sobrevive a
     * la cascada de {@code emisor_id}. Es el error que ya se cometio dos veces en esta auditoria
     * y el que esta prueba existe para que no vuelva.
     */
    @Test
    void laPurgaBorraLoExclusivoYDejaLaFotoQueOtroComparteEnSuChat() {
        FixedClock enElAlta = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId purgado = UserId.of(UUID.randomUUID());
        UserId ajeno = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.registerTrainee(purgado, new Email("purga-" + purgado + "@renaser.dev"), "Se Va"));
        saveUserPort.save(User.registerTrainee(ajeno, new Email("queda-" + ajeno + "@renaser.dev"), "Se Queda"));

        String avatar = "avatares/" + purgado;
        String fotoSola = "muro/fotos/" + purgado + "/sola";
        String fotoCompartida = "muro/fotos/" + purgado + "/compartida";
        // El avatar se pone por el camino del dominio y no con un UPDATE suelto: Hibernate tiene
        // la entidad en su cache de primer nivel y el save() de `request` la volveria a escribir
        // con el avatar en null, pisando el UPDATE crudo.
        User conFoto = loadUserPort.byId(purgado).orElseThrow();
        conFoto.changeAvatar("https://bucket.s3.amazonaws.com/" + avatar);
        saveUserPort.save(conFoto);
        UUID publicacionId = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.publicaciones_muro (id, autor_id, tipo, texto) "
                + "VALUES (?, ?, 'MANUAL', 'mi muro')", publicacionId, purgado.value());
        short orden = 0;
        for (String ruta : new String[] {fotoSola, fotoCompartida}) {
            jdbc.update("INSERT INTO renaser.medias_publicacion (publicacion_id, bucket, ruta_storage, mime, orden) "
                    + "VALUES (?, 'wall', ?, 'image/jpeg', ?)", publicacionId, ruta, orden++);
        }
        // El otro comparte esa foto a un chat: MISMA clave, no una copia.
        UUID conversacionId = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa) VALUES (?, 'DIRECTA', ?)",
                conversacionId, "clave-" + conversacionId);
        jdbc.update("INSERT INTO renaser.mensajes (conversacion_id, emisor_id, tipo, texto, media_bucket, "
                + "media_ruta, media_mime) VALUES (?, ?, 'IMAGEN', 'mira esto', 'wall', ?, 'image/jpeg')",
                conversacionId, ajeno.value(), fotoCompartida);

        var borrados = new java.util.ArrayList<String>();
        AlmacenamientoPort registrandoBorrados = new AlmacenamientoPortQueRegistra(borrados);

        new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort, deleteAccountRequestPort,
                rutasDeAlmacenamientoPort, registrandoBorrados, new RequireActiveUserGuard(loadUserPort),
                transactionManager, enElAlta, DIAS_DE_GRACIA)
                .request(new RequestAccountDeletionCommand(purgado, "ELIMINAR"));

        FixedClock quinceDiasDespues = FixedClock.at(enElAlta.now().plusSeconds(15L * 24 * 60 * 60));
        var resultado = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                deleteAccountRequestPort, rutasDeAlmacenamientoPort, registrandoBorrados,
                new RequireActiveUserGuard(loadUserPort), transactionManager, quinceDiasDespues,
                DIAS_DE_GRACIA).purgeExpired();

        assertThat(resultado.purgadas()).isEqualTo(1);
        assertThat(resultado.fallidas()).isZero();
        assertThat(loadUserPort.byId(purgado)).isEmpty();
        // Lo suyo y de nadie mas: se va del bucket.
        assertThat(borrados).contains(avatar, fotoSola);
        // Lo que el chat de otro sigue mirando: se queda. Borrarlo dejaria esa conversacion en 404.
        assertThat(borrados).doesNotContain(fotoCompartida);
    }

    /** Doble de {@link AlmacenamientoPort} que solo anota que se mando a borrar. No hace falta
     * Mockito aca: lo unico que interesa es la lista de claves. */
    private record AlmacenamientoPortQueRegistra(java.util.List<String> borrados) implements AlmacenamientoPort {
        @Override
        public java.net.URI firmarSubida(String ruta, String tipoContenido, java.time.Duration validez) {
            return java.net.URI.create("about:blank#" + ruta);
        }

        @Override
        public java.net.URI firmarLectura(String ruta, java.time.Duration validez) {
            return java.net.URI.create("about:blank#" + ruta);
        }

        @Override
        public java.net.URI urlPublica(String ruta) {
            return java.net.URI.create("about:blank#" + ruta);
        }

        @Override
        public void borrar(String ruta) {
            borrados.add(ruta);
        }
    }
}
