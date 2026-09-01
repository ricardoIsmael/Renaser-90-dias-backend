package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountRequestPersistenceAdapterTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Autowired
    private AccountRequestPersistenceAdapter adapter;

    private static AccountRequestId nuevoId() {
        return AccountRequestId.of(UUID.randomUUID());
    }

    @Test
    void guardaYRecuperaUnaSolicitudPendiente() {
        UserId usuarioId = UserId.of(UUID.randomUUID());
        AccountRequest request = AccountRequest.submit(nuevoId(), usuarioId, new Email("aspirante@renaser.com"),
                "Ana Aspirante", "+51999999999", "Lima", "203.0.113.5", null, CLOCK);

        var saved = adapter.save(request);

        AccountRequest loaded = adapter.byId(saved.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(AccountRequestStatus.PENDING);
        assertThat(loaded.email()).isEqualTo(new Email("aspirante@renaser.com"));
        assertThat(loaded.phone()).isEqualTo("+51999999999");
    }

    /**
     * D-61 + migracion V14: la prueba de que el INSERT con {@code telefono} NULL <b>llega a
     * Postgres</b>. Sin la migracion esto falla en la base, no en Java — {@code solicitudes_cuenta
     * .telefono} era {@code NOT NULL} desde el baseline V1, asi que aunque el dominio y el comando
     * acepten el null, la fila no entraria. Por eso es de integracion y no unitaria: lo que se
     * verifica es que la restriccion de la columna se levanto de verdad.
     */
    @Test
    void guardaUnaSolicitudSinTelefono() {
        AccountRequest request = AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()),
                new Email("sintelefono@renaser.com"), "Ana Sin Telefono", null, "Lima",
                "203.0.113.6", null, CLOCK);

        adapter.save(request);

        AccountRequest loaded = adapter.byId(request.id()).orElseThrow();
        assertThat(loaded.phone()).isNull();
        assertThat(loaded.status()).isEqualTo(AccountRequestStatus.PENDING);
    }

    @Test
    void traduceLosTresEstadosEnAmbasDirecciones() {
        UserId adminId = UserId.of(UUID.randomUUID());
        com.renaser.os.users.domain.model.user.User admin =
                com.renaser.os.users.domain.model.user.User.rehydrate(adminId, new Email("admin@renaser.com"),
                        com.renaser.os.users.api.UserRole.ADMIN,
                        com.renaser.os.users.api.UserStatus.ACTIVE, "Admin", null, null, null, null);

        AccountRequest approved = AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()),
                new Email("a@renaser.com"), "A", "+51900000001", null, null, null, CLOCK);
        approved.approve(admin, UserId.of(UUID.randomUUID()), CLOCK);
        adapter.save(approved);
        assertThat(adapter.byId(approved.id()).orElseThrow().status()).isEqualTo(AccountRequestStatus.APPROVED);

        AccountRequest rejected = AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()),
                new Email("b@renaser.com"), "B", "+51900000002", null, null, null, CLOCK);
        rejected.reject(admin, "Datos incompletos", CLOCK);
        adapter.save(rejected);
        AccountRequest loadedRejected = adapter.byId(rejected.id()).orElseThrow();
        assertThat(loadedRejected.status()).isEqualTo(AccountRequestStatus.REJECTED);
        assertThat(loadedRejected.rejectionReason()).isEqualTo("Datos incompletos");
    }

    /**
     * A-7: el {@code (proveedor, sujeto)} tiene que sobrevivir el viaje a Postgres y volver —
     * es el dato con el que {@code approve()} crea despues la {@code IdentidadExterna}. Antes de
     * la migracion V12 esas dos columnas no existian y el dato se perdia entre el alta y la
     * aprobacion, que es exactamente lo que dejaba a la persona sin poder volver a entrar.
     */
    @Test
    void guardaYRecuperaElOrigenSocialDeUnaSolicitud() {
        OrigenSocial origen = new OrigenSocial(ProveedorIdentidad.GOOGLE, "google-sub-persistido");
        AccountRequest request = AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()),
                new Email("social@renaser.com"), "Sofia Social", "+51900000010", "Lima", null, origen, CLOCK);

        adapter.save(request);

        AccountRequest recuperada = adapter.byId(request.id()).orElseThrow();
        assertThat(recuperada.origenSocial()).isEqualTo(origen);
    }

    /** La consulta que usa el login social: se resuelve por identidad, nunca por correo. */
    @Test
    void encuentraLaSolicitudPorOrigenSocial() {
        OrigenSocial origen = new OrigenSocial(ProveedorIdentidad.APPLE, "apple-sub-buscado");
        AccountRequest request = AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()),
                new Email("buscada@renaser.com"), "Bruno Buscado", "+51900000011", null, null, origen, CLOCK);
        adapter.save(request);

        assertThat(adapter.porOrigenSocial(origen).orElseThrow().id()).isEqualTo(request.id());
    }

    /** El mismo sujeto emitido por dos proveedores distintos NO es la misma persona. */
    @Test
    void elMismoSujetoEnOtroProveedorNoEncuentraLaSolicitud() {
        String mismoSujeto = "sujeto-repetido-entre-proveedores";
        adapter.save(AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()), new Email("g@renaser.com"), "G",
                "+51900000012", null, null, new OrigenSocial(ProveedorIdentidad.GOOGLE, mismoSujeto), CLOCK));

        assertThat(adapter.porOrigenSocial(new OrigenSocial(ProveedorIdentidad.APPLE, mismoSujeto))).isEmpty();
    }

    /** Un alta por formulario no tiene origen social, y eso es un estado valido, no un dato faltante. */
    @Test
    void unaSolicitudPorFormularioVuelveSinOrigenSocial() {
        AccountRequest request = AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()),
                new Email("formulario@renaser.com"), "Fabi Formulario", "+51900000013", null, null, null, CLOCK);

        adapter.save(request);

        assertThat(adapter.byId(request.id()).orElseThrow().origenSocial()).isNull();
    }

    @Test
    void cuentaSolicitudesRecientesPorIpParaElRateLimit() {
        String ip = "198.51.100.7";
        adapter.save(AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()), new Email("uno@renaser.com"), "Uno",
                "+51900000003", null, ip, null, CLOCK));
        adapter.save(AccountRequest.submit(nuevoId(), UserId.of(UUID.randomUUID()), new Email("dos@renaser.com"), "Dos",
                "+51900000004", null, ip, null, CLOCK));

        long count = adapter.countSubmittedFromIpSince(ip, CLOCK.now().minusSeconds(3600));

        assertThat(count).isEqualTo(2);
    }
}
