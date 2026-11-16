package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.CuotasDeTodosUseCase;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.PagoAnotado;
import com.renaser.os.onboarding.application.ports.out.pagos.CronogramasDeTodosPort;
import com.renaser.os.onboarding.application.ports.out.pagos.CronogramasDeTodosPort.CronogramaGuardado;
import com.renaser.os.onboarding.application.ports.out.pagos.CronogramasDeTodosPort.FiltroDeCuotas;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las cuotas de todas las personas con cronograma (D-338), para RenaserOs. Una página cuesta un número fijo de consultas, sin importar cuántas personas traiga: los ids
 * que cumplen el pedido (una), el recorte por formación (una, solo si se pide), los cronogramas de la página (dos), los nombres (una) y los correos (una). Nunca llama
 * a la lectura de una persona en un bucle.
 *
 * <p>Se pagina sobre la lista de ids, ordenada por id: con miles de personas son unos pocos miles de UUID en memoria, y así el recorte por formación —que pregunta a
 * {@code users}, dueño de la formación de cada persona— se aplica ANTES de cortar la página (recortar una página ya cortada dejaría páginas con huecos).
 */
@Service
public class CuotasDeTodosService implements CuotasDeTodosUseCase {

    private final GuardiaDePagos guardia;
    private final CronogramasDeTodosPort cronogramas;
    private final ArmadorDeVistaDePagos armador;
    private final AlcanceDeFormacionFinder alcances;
    private final UserSummaryFinder usuarios;
    private final CorreosDeUsuariosFinder correos;

    public CuotasDeTodosService(GuardiaDePagos guardia, CronogramasDeTodosPort cronogramas, ArmadorDeVistaDePagos armador, AlcanceDeFormacionFinder alcances,
                                UserSummaryFinder usuarios, CorreosDeUsuariosFinder correos) {
        this.guardia = guardia;
        this.cronogramas = cronogramas;
        this.armador = armador;
        this.alcances = alcances;
        this.usuarios = usuarios;
        this.correos = correos;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeCuotas listar(PedidoDeCuotas pedido) {
        guardia.exigirQuePuedaAdministrar(pedido.actor());
        FiltroDeCuotas filtro = new FiltroDeCuotas(pedido.desde(), pedido.hasta(), pedido.estado());
        List<UserId> todas = deLaFormacion(pedido.actor(), pedido.formacion(), cronogramas.personasConCronograma(filtro));
        List<UserId> pagina = todas.stream().skip((long) pedido.pagina() * pedido.tamano()).limit(pedido.tamano()).toList();
        return new PaginaDeCuotas(filas(pagina, filtro), todas.size(), pedido.pagina(), pedido.tamano());
    }

    /** «Todas» no recorta (también entra quien no es de ninguna formación); una formación, como el resto de las listas de Administración (spec 024). */
    private List<UserId> deLaFormacion(UserId actor, Formacion formacion, List<UserId> personas) {
        if (formacion == null || personas.isEmpty()) {
            return personas;
        }
        Set<UserId> dentro = alcances.dentroDelAlcance(personas, alcances.de(actor).acotadoA(formacion));
        return personas.stream().filter(dentro::contains).toList();
    }

    private List<PagosDeUnaPersona> filas(List<UserId> pagina, FiltroDeCuotas filtro) {
        if (pagina.isEmpty()) {
            return List.of();
        }
        Map<UserId, CronogramaGuardado> guardados = cronogramas.deLasPersonas(pagina);
        Map<UserId, String> nombres = nombresDe(pagina, guardados);
        Map<UserId, String> correosDeLaPagina = correos.correosDe(pagina);
        return pagina.stream().filter(guardados::containsKey)
                .map(persona -> new PagosDeUnaPersona(persona, nombres.get(persona), correosDeLaPagina.get(persona),
                        recortado(armador.conCronograma(guardados.get(persona).pago(), guardados.get(persona).cuotas(), nombres), filtro)))
                .toList();
    }

    /** Los nombres de las personas de la página y de quien marcó cada una de sus cuotas, en una sola consulta. */
    private Map<UserId, String> nombresDe(List<UserId> pagina, Map<UserId, CronogramaGuardado> guardados) {
        Set<UserId> ids = new HashSet<>(pagina);
        guardados.values().stream().flatMap(g -> g.cuotas().stream()).map(Cuota::estadoCambiadoPor).filter(Objects::nonNull).forEach(ids::add);
        return usuarios.findByIds(ids).values().stream().filter(r -> r.fullName() != null)
                .collect(Collectors.toMap(UserSummary::id, UserSummary::fullName));
    }

    /** Solo las cuotas que cumplen el pedido; la confirmación ya se calculó sobre el cronograma entero. */
    private static PagoAnotado recortado(PagoAnotado pago, FiltroDeCuotas filtro) {
        if (!filtro.recorta()) {
            return pago;
        }
        return new PagoAnotado(pago.modalidad(), pago.moneda(), pago.cuotas().stream().filter(vista -> filtro.cumple(vista.cuota())).toList(),
                pago.confirmacion(), pago.confirmadaEn(), pago.nombreConfirmado());
    }
}
