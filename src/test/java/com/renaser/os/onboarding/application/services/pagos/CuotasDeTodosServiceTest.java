package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase.PaginaDeCuotas;
import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase.PagosDeUnaPersona;
import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase.PedidoDeCuotas;
import com.renaser.os.onboarding.application.ports.out.pagos.CronogramasDeTodosPort;
import com.renaser.os.onboarding.domain.model.pagos.CronogramaDePagos;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeConfirmacion;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La lectura de las cuotas de todos (D-338): mismas reglas de acceso que la ficha, en lote y sin una consulta por persona. */
class CuotasDeTodosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-10T15:00:00Z");

    private PagosEnMemoria.Actores actores;
    private Cronogramas cronogramas;
    private AlcanceDeFormacionFinder alcances;
    private UserSummaryFinder usuarios;
    private CorreosDeUsuariosFinder correos;
    private CuotasDeTodosService servicio;
    private UserId admin;
    private final Map<UserId, String> nombres = new HashMap<>();

    @BeforeEach
    void preparar() {
        actores = new PagosEnMemoria.Actores();
        cronogramas = new Cronogramas();
        alcances = mock(AlcanceDeFormacionFinder.class);
        usuarios = mock(UserSummaryFinder.class);
        correos = mock(CorreosDeUsuariosFinder.class);
        when(usuarios.findByIds(anyCollection())).thenAnswer(i -> resumenes(i.getArgument(0)));
        when(correos.correosDe(anyCollection())).thenAnswer(i -> ((Collection<UserId>) i.getArgument(0)).stream()
                .collect(Collectors.toMap(id -> id, id -> id.value() + "@renaser.test")));
        servicio = new CuotasDeTodosService(new GuardiaDePagos(actores), cronogramas, new ArmadorDeVistaDePagos(new PagosEnMemoria.Cuotas(), actores), alcances,
                usuarios, correos);
        admin = actores.cuenta("Ana Admin", true, false);
        nombres.put(admin, "Ana Admin");
    }

    @Test
    @DisplayName("solo Administración y Alquimista activos: cualquier otro rol o una cuenta suspendida es 403 y no se lee nada")
    void soloAdministracion() {
        UserId mentor = actores.cuenta("Mentor", false, false);
        UserId suspendida = actores.cuenta("Admin suspendida", true, true);
        persona("Beto", cuota("2026-11-15", EstadoDeCuota.PENDIENTE));

        for (UserId actor : List.of(mentor, suspendida)) {
            assertThatThrownBy(() -> servicio.listar(pedido(actor, 0, 50))).isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(cronogramas.lecturasDeIds).isZero();
        verify(usuarios, never()).findByIds(anyCollection());
    }

    @Test
    @DisplayName("varias personas en una lectura, con su nombre, su correo, sus cuotas y quién marcó cada una; los cronogramas, los nombres y los correos se leen una sola vez")
    void variasPersonasEnUnaLectura() {
        UserId beto = persona("Beto", cuota("2026-11-15", EstadoDeCuota.PENDIENTE), cuota("2026-12-15", EstadoDeCuota.PENDIENTE));
        UserId carla = persona("Carla", pagada("2026-10-15", admin));
        UserId dario = persona("Darío");

        PaginaDeCuotas pagina = servicio.listar(pedido(admin, 0, 50));

        assertThat(pagina.total()).isEqualTo(3);
        assertThat(pagina.contenido()).extracting(PagosDeUnaPersona::persona).containsExactlyElementsOf(ordenadas(beto, carla, dario));
        PagosDeUnaPersona deCarla = fila(pagina, carla);
        assertThat(deCarla.nombre()).isEqualTo("Carla");
        assertThat(deCarla.correo()).isEqualTo(carla.value() + "@renaser.test");
        assertThat(deCarla.pago().moneda()).isEqualTo(Moneda.PEN);
        assertThat(deCarla.pago().cuotas()).singleElement().satisfies(vista -> {
            assertThat(vista.cuota().pagada()).isTrue();
            assertThat(vista.marcadaPor()).isEqualTo("Ana Admin");
        });
        assertThat(fila(pagina, beto).pago().cuotas()).extracting(v -> v.cuota().venceEl().toString()).containsExactly("2026-11-15", "2026-12-15");
        assertThat(fila(pagina, dario).pago().cuotas()).as("«con cronograma» sin cuotas todavía también sale").isEmpty();
        assertThat(cronogramas.lecturasDeCronogramas).isEqualTo(1);
        verify(usuarios, times(1)).findByIds(anyCollection());
        verify(correos, times(1)).correosDe(anyCollection());
        verify(alcances, never()).dentroDelAlcance(anyCollection(), any());
    }

    @Test
    @DisplayName("pagina sobre las personas: total de todas, la página pedida en orden estable y vacía más allá del final; solo se leen los cronogramas de la página")
    void pagina() {
        List<UserId> todas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            todas.add(persona("Persona " + i, cuota("2026-11-1" + i, EstadoDeCuota.PENDIENTE)));
        }
        List<UserId> enOrden = ordenadas(todas.toArray(UserId[]::new));

        PaginaDeCuotas segunda = servicio.listar(pedido(admin, 1, 2));
        PaginaDeCuotas fuera = servicio.listar(pedido(admin, 3, 2));

        assertThat(segunda.total()).isEqualTo(5);
        assertThat(segunda.pagina()).isEqualTo(1);
        assertThat(segunda.tamano()).isEqualTo(2);
        assertThat(segunda.contenido()).extracting(PagosDeUnaPersona::persona).containsExactly(enOrden.get(2), enOrden.get(3));
        assertThat(cronogramas.ultimasPedidas).containsExactlyInAnyOrder(enOrden.get(2), enOrden.get(3));
        assertThat(fuera.contenido()).isEmpty();
        assertThat(fuera.total()).isEqualTo(5);
    }

    @Test
    @DisplayName("formation: solo las personas de esa formación (pregunta a users una vez, con el alcance acotado) y el total cuenta solo a esas")
    void filtraPorFormacion() {
        UserId deEscuela = persona("Escuela", cuota("2026-11-15", EstadoDeCuota.PENDIENTE));
        persona("Diplomado", cuota("2026-11-15", EstadoDeCuota.PENDIENTE));
        when(alcances.de(admin)).thenReturn(AlcanceDeFormacion.todas());
        when(alcances.dentroDelAlcance(anyCollection(), any())).thenReturn(Set.of(deEscuela));

        PaginaDeCuotas pagina = servicio.listar(new PedidoDeCuotas(admin, 0, 50, Formacion.ESCUELA, null, null, null));

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.contenido()).extracting(PagosDeUnaPersona::persona).containsExactly(deEscuela);
        verify(alcances).dentroDelAlcance(anyCollection(), org.mockito.ArgumentMatchers.eq(AlcanceDeFormacion.de(Formacion.ESCUELA)));
    }

    @Test
    @DisplayName("desde/hasta y estado recortan las cuotas y las personas; la confirmación se sigue calculando sobre el cronograma entero")
    void filtraPorFechaYEstado() {
        List<Cuota> deBeto = List.of(cuota("2026-10-15", EstadoDeCuota.PENDIENTE), cuota("2026-11-15", EstadoDeCuota.PENDIENTE), cuota("2026-12-15", EstadoDeCuota.PENDIENTE));
        String huella = CronogramaDePagos.guardado(Moneda.PEN, deBeto).huella();
        UserId beto = persona("Beto", PagoDeLaPersona.conCronograma(Moneda.PEN).confirmado("Beto", huella, AHORA), deBeto);
        persona("Carla", cuota("2027-03-15", EstadoDeCuota.PENDIENTE));
        persona("Darío", pagada("2026-11-20", admin));

        PaginaDeCuotas noviembrePendientes = servicio.listar(new PedidoDeCuotas(admin, 0, 50, null, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"),
                EstadoDeCuota.PENDIENTE));

        assertThat(noviembrePendientes.total()).isEqualTo(1);
        PagosDeUnaPersona fila = noviembrePendientes.contenido().getFirst();
        assertThat(fila.persona()).isEqualTo(beto);
        assertThat(fila.pago().cuotas()).extracting(v -> v.cuota().venceEl().toString()).containsExactly("2026-11-15");
        assertThat(fila.pago().confirmacion()).as("la huella es la del cronograma entero, no la de lo filtrado").isEqualTo(EstadoDeConfirmacion.CONFIRMADA);
    }

    @Test
    @DisplayName("el pedido se valida: page negativo, size fuera de 1..200 y desde posterior a hasta son 400")
    void pedidoInvalido() {
        assertThatThrownBy(() -> pedido(admin, -1, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pedido(admin, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pedido(admin, 0, 201)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoDeCuotas(admin, 0, 50, null, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-11-01"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------------------------------ ayudas

    private static PedidoDeCuotas pedido(UserId actor, int pagina, int tamano) {
        return new PedidoDeCuotas(actor, pagina, tamano, null, null, null, null);
    }

    private UserId persona(String nombre, Cuota... cuotas) {
        return persona(nombre, PagoDeLaPersona.conCronograma(Moneda.PEN), List.of(cuotas));
    }

    private UserId persona(String nombre, PagoDeLaPersona pago, List<Cuota> cuotas) {
        UserId id = UserId.of(UUID.randomUUID());
        nombres.put(id, nombre);
        cronogramas.guardados.put(id, new CronogramasDeTodosPort.CronogramaGuardado(pago, cuotas));
        return id;
    }

    private static Cuota cuota(String fecha, EstadoDeCuota estado) {
        return new Cuota(UUID.randomUUID(), LocalDate.parse(fecha), new BigDecimal("500"), estado, null, estado == EstadoDeCuota.PAGADA ? AHORA : null, AHORA);
    }

    private static Cuota pagada(String fecha, UserId quien) {
        return new Cuota(UUID.randomUUID(), LocalDate.parse(fecha), new BigDecimal("500"), EstadoDeCuota.PAGADA, quien, AHORA, AHORA);
    }

    private static List<UserId> ordenadas(UserId... ids) {
        return java.util.Arrays.stream(ids).sorted(Comparator.comparing(UserId::value)).toList();
    }

    private static PagosDeUnaPersona fila(PaginaDeCuotas pagina, UserId persona) {
        return pagina.contenido().stream().filter(f -> f.persona().equals(persona)).findFirst().orElseThrow();
    }

    private Map<UserId, UserSummary> resumenes(Collection<UserId> ids) {
        return ids.stream().filter(nombres::containsKey)
                .collect(Collectors.toMap(id -> id, id -> new UserSummary(id, nombres.get(id), null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }

    /** Como el adaptador: los ids ordenados (por el UUID, igual que Postgres) y los cronogramas de las personas pedidas, contando cuántas lecturas hubo. */
    private static final class Cronogramas implements CronogramasDeTodosPort {
        final Map<UserId, CronogramaGuardado> guardados = new HashMap<>();
        int lecturasDeIds;
        int lecturasDeCronogramas;
        List<UserId> ultimasPedidas = List.of();

        @Override
        public List<UserId> personasConCronograma(FiltroDeCuotas filtro) {
            lecturasDeIds++;
            return guardados.entrySet().stream()
                    .filter(e -> !filtro.recorta() || e.getValue().cuotas().stream().anyMatch(filtro::cumple))
                    .map(Map.Entry::getKey).sorted(Comparator.comparing(UserId::value)).toList();
        }

        @Override
        public Map<UserId, CronogramaGuardado> deLasPersonas(Collection<UserId> personas) {
            lecturasDeCronogramas++;
            ultimasPedidas = List.copyOf(personas);
            return personas.stream().filter(guardados::containsKey).collect(Collectors.toMap(p -> p, guardados::get));
        }
    }
}
