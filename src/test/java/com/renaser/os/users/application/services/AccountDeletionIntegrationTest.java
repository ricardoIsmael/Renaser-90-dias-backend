package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase.RequestAccountDeletionCommand;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.RutasDeAlmacenamientoDeCuentaPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Contra Postgres real (Testcontainers), no mocks — es la prueba que exige el encargo:
 * "crear cuenta -> dar de baja -> purgar -> registrar de nuevo con el mismo email -> funciona".
 *
 * <p>Demuestra que el bug documentado del backend viejo NO se repite aca: alli, borrar una
 * cuenta no liberaba el email porque `wipeTraineeData` no tocaba ni `trainee_profiles` ni
 * `users` ni `account_requests`, y habia que borrar las 4 piezas a mano (ver
 * features/account-deletion/repository.ts#borrarCuenta). Aca, las ~30 FK contra `usuarios`
 * en el baseline son ON DELETE CASCADE (o SET NULL en las de auditoria), asi que un solo
 * DELETE de la fila raiz basta.
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
    private RutasDeAlmacenamientoDeCuentaPort rutasDeAlmacenamientoPort;
    @Autowired
    private AlmacenamientoPort almacenamientoPort;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void crearBajaPurgarYRegistrarDeNuevoConElMismoEmailFunciona() {
        String email = "purgado-" + UUID.randomUUID() + "@renaser.dev";
        FixedClock enElAlta = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId primeraCuentaId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.registerTrainee(primeraCuentaId, new Email(email), "Primera Cuenta"));

        var requestService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), enElAlta, DIAS_DE_GRACIA);
        var estado = requestService.request(new RequestAccountDeletionCommand(primeraCuentaId, "ELIMINAR"));
        assertThat(estado.bajaPendiente()).isTrue();
        assertThat(loadUserPort.byId(primeraCuentaId)).isPresent();

        // Todavia dentro de la gracia: el cron no la toca.
        var purgaTemprana = requestService.purgeExpired();
        assertThat(purgaTemprana.purgadas()).isZero();
        assertThat(loadUserPort.byId(primeraCuentaId)).isPresent();

        // 15 dias despues (gracia de 14 ya vencida).
        FixedClock quinceDiasDespues = FixedClock.at(enElAlta.now().plusSeconds(15L * 24 * 60 * 60));
        var purgeService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), quinceDiasDespues, DIAS_DE_GRACIA);

        var resultadoPurga = purgeService.purgeExpired();

        assertThat(resultadoPurga.purgadas()).isEqualTo(1);
        assertThat(resultadoPurga.fallidas()).isZero();
        assertThat(loadUserPort.byId(primeraCuentaId)).isEmpty();
        assertThat(loadUserPort.byEmail(new Email(email))).isEmpty();

        // El email tiene que estar libre para un alta nueva — este es el bug viejo que no debe repetirse.
        UserId segundaCuentaId = UserId.of(UUID.randomUUID());
        assertThatCode(() -> saveUserPort.save(
                User.registerTrainee(segundaCuentaId, new Email(email), "Segunda Cuenta")))
                .doesNotThrowAnyException();
        assertThat(loadUserPort.byId(segundaCuentaId)).isPresent();
        assertThat(loadUserPort.byEmail(new Email(email)).orElseThrow().id()).isEqualTo(segundaCuentaId);
    }

    @Test
    void cancelarLaBajaAntesDeQueVenzaLaGraciaEvitaLaPurga() {
        String email = "cancelada-" + UUID.randomUUID() + "@renaser.dev";
        FixedClock clock = FixedClock.at(Instant.parse("2026-08-01T10:00:00Z"));
        UserId userId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.registerTrainee(userId, new Email(email), "Se Arrepiente"));
        var service = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), clock, DIAS_DE_GRACIA);
        service.request(new RequestAccountDeletionCommand(userId, "ELIMINAR"));

        service.cancel(userId);

        FixedClock muchoDespues = FixedClock.at(clock.now().plusSeconds(30L * 24 * 60 * 60));
        var purgeService = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                rutasDeAlmacenamientoPort, almacenamientoPort,
                new RequireActiveUserGuard(loadUserPort), muchoDespues, DIAS_DE_GRACIA);

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

        new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort, rutasDeAlmacenamientoPort,
                registrandoBorrados, new RequireActiveUserGuard(loadUserPort), enElAlta, DIAS_DE_GRACIA)
                .request(new RequestAccountDeletionCommand(purgado, "ELIMINAR"));

        FixedClock quinceDiasDespues = FixedClock.at(enElAlta.now().plusSeconds(15L * 24 * 60 * 60));
        var resultado = new AccountDeletionService(loadUserPort, saveUserPort, deleteUserPort,
                rutasDeAlmacenamientoPort, registrandoBorrados, new RequireActiveUserGuard(loadUserPort),
                quinceDiasDespues, DIAS_DE_GRACIA).purgeExpired();

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
