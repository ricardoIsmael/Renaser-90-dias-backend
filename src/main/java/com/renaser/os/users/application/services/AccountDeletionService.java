package com.renaser.os.users.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.CancelAccountDeletionUseCase;
import com.renaser.os.users.application.ports.in.user.GetAccountDeletionStatusUseCase;
import com.renaser.os.users.application.ports.in.user.PurgeExpiredAccountsUseCase;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.DeleteAccountRequestPort;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.RutasDeAlmacenamientoDeCuentaPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.ClavesDeCuenta;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Baja de cuenta autogestionada (gap #5, requisito Google Play/Apple) — portada 1:1 de
 * features/account-deletion (backend viejo, Next.js): soft-delete diferido con purga por
 * cron. Ver {@code EstadoBajaCuenta} para la logica pura del plazo de gracia y
 * {@code docs/BITACORA_ERRORES.md}/{@code docs/MODULO_USERS.md} para la decision de los
 * dias de gracia (14, confirmado — no un supuesto: coincide el comentario de
 * {@code usuarios.baja_solicitada_en} en el baseline SQL con {@code DIAS_DE_GRACIA} del
 * backend viejo).
 */
@Service
public class AccountDeletionService implements RequestAccountDeletionUseCase, CancelAccountDeletionUseCase,
        GetAccountDeletionStatusUseCase, PurgeExpiredAccountsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** Google Play (2024) / Apple 5.1.1(v): confirmacion explicita para que ni un retry
     * automatico ni un request suelto borren una cuenta (backend viejo, PALABRA_DE_CONFIRMACION).
     * No sustituye a la reautenticacion, que hace el cliente contra su propia sesion antes
     * de llamar. */
    public static final String PALABRA_CONFIRMACION = "ELIMINAR";

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final DeleteAccountRequestPort deleteAccountRequestPort;
    private final RutasDeAlmacenamientoDeCuentaPort rutasDeAlmacenamientoPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    /**
     * Una transaccion POR CUENTA para los borrados en Postgres de {@link #purgeExpired} — el
     * metodo no es {@code @Transactional} (a proposito: cada cuenta se intenta por separado) y
     * desde el 2026-09-21 son DOS borrados, no uno. Sin este limite, un fallo en el segundo
     * dejaria la cuenta a medio purgar.
     *
     * <p><b>Propagacion REQUIRED, no REQUIRES_NEW</b> como en los barridos de {@code habits}
     * ({@code RachaService.transaccionPropia}). Alli REQUIRES_NEW protege de un
     * {@code @Transactional} envolvente que una sola fila mala pudiera marcar rollback-only;
     * aca no hay tal envolvente — el unico llamador es
     * {@code PurgarCuentasBajaScheduler}, sin transaccion, asi que REQUIRED abre una nueva por
     * cuenta igual — y REQUIRES_NEW ademas suspenderia la transaccion de las pruebas de
     * integracion, que es donde este borrado se verifica contra Postgres de verdad: la
     * transaccion nueva no veria las filas que la prueba todavia no comiteo.
     */
    private final TransactionTemplate transaccionPorCuenta;
    private final Clock clock;
    private final int diasDeGracia;

    public AccountDeletionService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                   DeleteUserPort deleteUserPort,
                                   DeleteAccountRequestPort deleteAccountRequestPort,
                                   RutasDeAlmacenamientoDeCuentaPort rutasDeAlmacenamientoPort,
                                   AlmacenamientoPort almacenamientoPort,
                                   RequireActiveUserGuard requireActiveUserGuard,
                                   PlatformTransactionManager transactionManager,
                                   Clock clock,
                                   @Value("${renaser.users.account-deletion.grace-period-days:14}")
                                   int diasDeGracia) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.deleteUserPort = deleteUserPort;
        this.deleteAccountRequestPort = deleteAccountRequestPort;
        this.rutasDeAlmacenamientoPort = rutasDeAlmacenamientoPort;
        this.almacenamientoPort = almacenamientoPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.transaccionPorCuenta = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.diasDeGracia = diasDeGracia;
    }

    @Override
    @Transactional
    public EstadoBajaCuenta request(RequestAccountDeletionCommand command) {
        if (!PALABRA_CONFIRMACION.equals(command.confirmacion())) {
            throw new IllegalArgumentException(
                    "CONFIRMATION_REQUIRED: la confirmacion debe ser \"" + PALABRA_CONFIRMACION + "\"");
        }
        User user = requireActiveUserGuard.of(command.userId());
        // Idempotente: si ya habia una solicitud, User.solicitarBaja no la reinicia.
        user.solicitarBaja(clock);
        saveUserPort.save(user);
        return EstadoBajaCuenta.de(user.bajaSolicitadaEn(), clock.now(), diasDeGracia);
    }

    @Override
    @Transactional
    public EstadoBajaCuenta cancel(UserId userId) {
        User user = requireActiveUserGuard.of(userId);
        user.cancelarBaja();
        saveUserPort.save(user);
        return EstadoBajaCuenta.sinSolicitud(diasDeGracia);
    }

    @Override
    public EstadoBajaCuenta status(UserId userId) {
        User user = requireActiveUserGuard.of(userId);
        return EstadoBajaCuenta.de(user.bajaSolicitadaEn(), clock.now(), diasDeGracia);
    }

    /**
     * El cron diario (ver {@code PurgarCuentasBajaScheduler}). Cada cuenta se intenta por
     * separado — un fallo puntual no puede dejar sin purgar a las demas (mismo criterio que
     * el cron viejo, features/account-deletion/service.ts#purgarBajasVencidas).
     *
     * <p>En POSTGRES casi todo cuelga de {@link DeleteUserPort#deleteById} — las ~30 FK contra
     * `usuarios` en el baseline son ON DELETE CASCADE (o SET NULL en las de auditoria) y desde
     * D-49 nosotros somos dueños de credenciales/identidades, asi que ese DELETE arrastra el
     * grueso del rastro y libera el email (UNIQUE) de esa tabla.
     *
     * <p><b>Pero `solicitudes_cuenta` no esta en ese grafo.</b> Hasta el 2026-09-21 este metodo
     * daba por sentado que un solo DELETE limpiaba todo, y no: {@code usuario_id} —la unica
     * columna que dice de quien es la solicitud— no tiene FK contra `usuarios`, y las dos que si
     * la tienen ({@code revisada_por}, {@code usuario_creado_id}) son ON DELETE SET NULL, que
     * corta el vinculo y deja la fila entera. Como el alta de autoservicio crea usuario Y
     * solicitud en la misma transaccion ({@code AccountRequestService.submit}, formulario y
     * proveedor social por igual), toda cuenta purgada dejaba atras su correo, su nombre
     * completo, el UUID de la cuenta ya borrada, la IP del registro y —cuando se dieron—
     * telefono, ciudad y el sujeto del proveedor. Encima el correo seguia ocupado: el
     * {@code existsByEmail} que consulta {@code POST /api/v1/account-requests/exists} (endpoint
     * en permitAll) y {@code rejectIfEmailYaRegistrado} no distingue una solicitud viva de la
     * lapida de una cuenta ya purgada, asi que un anonimo seguia obteniendo "si" sobre alguien
     * que pidio ser borrada, y ella no podia volver a darse de alta con su propio correo. Por
     * eso el barrido nombra esa fila explicitamente
     * ({@link DeleteAccountRequestPort#borrarPorUsuario}) en vez de delegarla a las FK.
     *
     * <p><b>Y el bucket tampoco esta en ese grafo</b>, ahi vive el material mas intimo del
     * producto: evidencia de habitos, firmas del Pacto, avatar, audios de onboarding y fotos del
     * Muro. Hasta el 2026-09-21 la purga borraba las filas que guardaban las RUTAS y dejaba los
     * OBJETOS en S3 para siempre — el indice se iba y el archivo se quedaba, exactamente al reves
     * de lo que promete {@link EstadoBajaCuenta} citando a Google Play (2024) y Apple 5.1.1(v).
     * Lo mismo que el backend viejo si hacia a mano (Storage incluido) y que la nota de arriba
     * daba por cubierto: cubria Postgres.
     *
     * <p><b>Lo que NO se borra, y por que.</b> Una misma clave de S3 puede tener mas de un dueno.
     * Compartir una publicacion al chat no copia el archivo — referencia la misma clave — y
     * {@code testimonios} sobrevive a la purga con la foto y el avatar congelados
     * ({@code usuario_id ON DELETE SET NULL}). Borrar por lista dejaria en 404 la foto de un
     * tercero que nunca pidio ninguna baja. Por eso se borra solo lo EXCLUSIVO, con dos filtros
     * (forma de la clave y referencias que sobreviven) y con la duda siempre a favor de no
     * borrar. Lo que queda retenido se registra: un objeto que sobrevive tiene que ser una linea
     * de log, no un silencio.
     */
    @Override
    public ResultadoPurga purgeExpired() {
        var corte = clock.now().minus(diasDeGracia, ChronoUnit.DAYS);
        List<UserId> candidatas = loadUserPort.pendingDeletionUpTo(corte);
        int purgadas = 0;
        int fallidas = 0;
        for (UserId id : candidatas) {
            try {
                // 1. El censo va ANTES del DELETE: la cascada destruye las filas que guardan las
                //    rutas y con ellas la unica forma de saber que objetos habia que borrar. Si
                //    este paso falla, la excepcion sale y la cuenta NO se purga — se reintenta
                //    manana con las filas todavia en pie, que es preferible a borrarla a ciegas.
                List<String> exclusivas = clavesExclusivasDe(id);
                // 2. Los objetos primero y la fila despues, como EventoService.eliminar. El orden
                //    importa por si el proceso muere en el medio: cortado aca, la cuenta sigue
                //    viva y el barrido de manana repite todo (borrar es idempotente). Al reves
                //    —fila primero— un corte dejaria los objetos huerfanos y ya sin nadie que
                //    supiera nombrarlos, que es el defecto que este metodo viene a cerrar.
                for (String clave : exclusivas) {
                    try {
                        almacenamientoPort.borrar(clave);
                    } catch (RuntimeException e) {
                        // Un fallo de S3 no tumba la purga (mismo criterio que EventoService),
                        // pero tampoco se traga: sin esta linea la promesa de borrado vuelve a
                        // quedar sin nadie que la audite.
                        log.error("[users.AccountDeletionService] la cuenta {} se purga pero quedo el objeto {}",
                                id, clave, e);
                    }
                }
                // 3. Los dos borrados de Postgres, en UNA transaccion (`transaccionPorCuenta`):
                //    son dos tablas y este metodo no es @Transactional, asi que sin este limite
                //    un fallo en el segundo dejaria la cuenta a medio purgar.
                transaccionPorCuenta.executeWithoutResult(estado -> {
                    // La solicitud ANTES que el usuario, por el mismo motivo que los objetos van
                    // antes que la fila: la fila de `usuarios` es lo unico que hace que el
                    // barrido de manana vuelva a mirar esta cuenta (`pendingDeletionUpTo` lee de
                    // ahi). Al reves —usuario primero— un corte entre los dos borrados dejaria la
                    // solicitud huerfana PARA SIEMPRE, sin nadie que volviera a nombrarla: el
                    // agujero exacto que este paso cierra.
                    deleteAccountRequestPort.borrarPorUsuario(id);
                    deleteUserPort.deleteById(id);
                });
                purgadas++;
            } catch (RuntimeException e) {
                fallidas++;
                log.error("[users.AccountDeletionService] fallo purgando la cuenta {}", id, e);
            }
        }
        return new ResultadoPurga(purgadas, fallidas);
    }

    /**
     * Las claves que se pueden borrar sin romperle nada a nadie: las de esta cuenta que ademas
     * no referencia ninguna fila que sobreviva a la purga.
     *
     * <p>Dos filtros, y hacen falta los dos. El primero es de FORMA y es puro
     * ({@link ClavesDeCuenta}): descarta lo que no tiene pinta de clave armada por el servidor
     * para este usuario — incluida cualquier ruta que el cliente haya escrito a mano en su
     * bitacora nocturna apuntando al archivo de otro. El segundo es de REFERENCIAS y lo responde
     * la base: de lo que quedo, que sigue mirando un testimonio o el mensaje de otra persona.
     */
    private List<String> clavesExclusivasDe(UserId id) {
        ClavesDeCuenta propias = ClavesDeCuenta.de(id);
        List<String> candidatas = rutasDeAlmacenamientoPort.candidatas(id).stream()
                .filter(propias::contiene)
                .distinct()
                .toList();
        if (candidatas.isEmpty()) {
            return List.of();
        }
        Set<String> deTerceros = rutasDeAlmacenamientoPort.referenciadasPorTerceros(id, candidatas);
        List<String> exclusivas = candidatas.stream().filter(ruta -> !deTerceros.contains(ruta)).toList();
        if (exclusivas.size() < candidatas.size()) {
            log.warn("[users.AccountDeletionService] la cuenta {} deja {} de {} objeto(s) en el bucket: "
                            + "otra fila que sobrevive a la purga los referencia (testimonio, mensaje, "
                            + "publicacion, bitacora o santuario ajeno)",
                    id, candidatas.size() - exclusivas.size(), candidatas.size());
        }
        return exclusivas;
    }
}
