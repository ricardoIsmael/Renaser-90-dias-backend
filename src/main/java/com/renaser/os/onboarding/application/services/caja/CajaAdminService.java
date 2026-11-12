package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.onboarding.application.ports.in.caja.ConsultarCajasUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.DetalleDeCaja;
import com.renaser.os.onboarding.application.ports.in.caja.ResumenDeCaja;
import com.renaser.os.onboarding.application.ports.in.caja.VerCajaUseCase;
import com.renaser.os.onboarding.application.services.caja.CajasDelPadron.CajaConFicha;
import com.renaser.os.onboarding.domain.model.caja.CajaRenaser;
import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.domain.model.caja.PasoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.TipoPasoCaja;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.PaginaAcotada;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La lista del Admin, su descarga y el detalle de una caja (D-219).
 *
 * <p><b>El estado de la caja se deriva, no se guarda</b> (regla 02; «derivar, no guardar», spec 050 Q4 = A): no hay
 * columna por la que filtrar o paginar en SQL, y las pestañas necesitan el conteo de TODOS los estados. Por eso cada lista
 * recorre el padrón entero, pero <b>por lotes y sin juntar las cajas</b> (spec 050, tramo F; FR-033): de cada aprendiz
 * se guarda solo un índice mínimo (id, nombre sin tildes, estado) y las cajas completas ({@link ResumenDeCaja}, con su
 * ficha de envío) se arman únicamente para la página que se muestra o, en la descarga, para el lote que se está
 * escribiendo. Memoria: el índice (cientos de bytes por aprendiz, no kilobytes) más un lote.
 *
 * <p><b>El techo</b> es de costo, no de memoria: cada pedido de la lista y cada descarga deriva el estado de todo el padrón
 * (unas seis consultas por cada {@link CajasDelPadron#TAMANO_LOTE} aprendices). Pasados {@link #AVISAR_DESDE} aprendices
 * se anota un aviso en el registro: es la señal para materializar el estado (la alternativa B de Q4: migración y columna).
 * Las cifras medidas están en {@code docs/MODULOS_A_AVANZAR.md} (D-348).
 */
@Service
public class CajaAdminService implements ConsultarCajasUseCase, VerCajaUseCase {

    /** Desde cuántos aprendices en el padrón se avisa que ya conviene materializar el estado (ver el javadoc de la clase). */
    static final int AVISAR_DESDE = 5_000;

    private static final Logger log = LoggerFactory.getLogger(CajaAdminService.class);

    /** Lo mínimo que se guarda de cada caja para contar, filtrar y ordenar; la caja completa se arma después, por lotes. */
    private record EntradaDelIndice(UserId aprendizId, String nombreNormalizado, EstadoCaja estado) {
    }

    private final GuardiaDeCaja guardia;
    private final CajasDelPadron padron;
    private final LecturaDeCaja lectura;
    private final AcompanamientoFinder acompanamiento;
    private final Clock clock;

    CajaAdminService(GuardiaDeCaja guardia, CajasDelPadron padron, LecturaDeCaja lectura,
                     AcompanamientoFinder acompanamiento, Clock clock) {
        this.guardia = guardia;
        this.padron = padron;
        this.lectura = lectura;
        this.acompanamiento = acompanamiento;
        this.clock = clock;
    }

    @Override
    public PaginaDeCajas listar(UserId actorId, FiltroDeCajas filtro) {
        guardia.exigirAdmin(actorId);
        List<EntradaDelIndice> todas = indice();
        Map<EstadoCaja, Integer> conteos = new EnumMap<>(EstadoCaja.class);
        for (EstadoCaja estado : EstadoCaja.values()) {
            if (estado != EstadoCaja.NO_APLICA) {
                conteos.put(estado, (int) todas.stream().filter(r -> r.estado() == estado).count());
            }
        }
        String buscado = normalizado(filtro.q());
        List<EntradaDelIndice> filtradas = todas.stream()
                .filter(r -> filtro.estado() == null ? r.estado() != EstadoCaja.NO_APLICA : r.estado() == filtro.estado())
                .filter(r -> buscado.isEmpty() || r.nombreNormalizado().contains(buscado))
                .toList();
        PaginaAcotada pagina = PaginaAcotada.recortada(filtro.pagina(), filtro.tamano(), TAMANO_MAXIMO);
        List<UserId> deLaPagina = pagina.desde() >= filtradas.size() ? List.of()
                : filtradas.subList((int) pagina.desde(), (int) Math.min(filtradas.size(), pagina.desde() + pagina.tamano()))
                        .stream().map(EntradaDelIndice::aprendizId).toList();
        return new PaginaDeCajas(resumenesDe(deLaPagina, gruposDeHoy()), filtradas.size(), conteos);
    }

    @Override
    public ExportacionDeCajas exportar(UserId actorId) {
        guardia.exigirAdmin(actorId);
        return escritor -> {
            Map<UserId, String> grupos = gruposDeHoy();
            List<UserId> aplican = indice().stream().filter(r -> r.estado() != EstadoCaja.NO_APLICA)
                    .map(EntradaDelIndice::aprendizId).toList();
            for (int desde = 0; desde < aplican.size(); desde += CajasDelPadron.TAMANO_LOTE) {
                List<UserId> lote = aplican.subList(desde, Math.min(aplican.size(), desde + CajasDelPadron.TAMANO_LOTE));
                escritor.accept(resumenesDe(lote, grupos));
            }
        };
    }

    @Override
    public DetalleDeCaja ver(UserId actorId, UserId aprendizId) {
        guardia.exigirAdmin(actorId);
        return lectura.detalle(padron.deAprendiz(aprendizId));
    }

    /** Recorre el padrón por lotes y guarda lo mínimo de cada caja, ordenado por nombre (estable: igual que el orden de siempre). */
    private List<EntradaDelIndice> indice() {
        List<EntradaDelIndice> indice = new ArrayList<>();
        padron.recorrer(lote -> lote.forEach(c -> indice.add(
                new EntradaDelIndice(c.caja().aprendizId(), normalizado(c.nombre()), c.caja().estado()))));
        indice.sort(Comparator.comparing(EntradaDelIndice::nombreNormalizado));
        if (indice.size() >= AVISAR_DESDE) {
            log.warn("La lista y la descarga de Caja derivaron el estado de {} aprendices en este pedido (aviso desde {}): "
                    + "es hora de materializar el estado (spec 050, Q4, alternativa B).", indice.size(), AVISAR_DESDE);
        }
        return indice;
    }

    /** Las cajas completas de esos aprendices, en ese orden, EN LOTE: la página que se muestra o el lote que se escribe. */
    private List<ResumenDeCaja> resumenesDe(List<UserId> ids, Map<UserId, String> grupos) {
        return padron.deAprendices(ids).stream().map(c -> resumen(c, grupos.get(c.caja().aprendizId()))).toList();
    }

    private static ResumenDeCaja resumen(CajaConFicha leida, String grupo) {
        CajaRenaser caja = leida.caja();
        Instant actualizadoEn = caja.pasos().stream().filter(p -> p.tipo().estadoDelHistorial().isPresent()
                        || p.tipo() == TipoPasoCaja.FOTO || p.tipo() == TipoPasoCaja.COMPROBANTE)
                .map(PasoDeCaja::en).max(Comparator.naturalOrder()).orElse(null);
        return new ResumenDeCaja(caja.aprendizId(), leida.nombre(), grupo, caja.diaDelPrograma(), caja.estado(),
                caja.envioActual(), actualizadoEn, caja.cumplimientoFaseUno().orElse(null), leida.ficha(),
                caja.ultimo(TipoPasoCaja.ENVIADA).map(DatosDelEnvio::desde).orElse(null));
    }

    /** El grupo en que está hoy cada aprendiz, en UNA consulta (el primero, si está en varios). */
    private Map<UserId, String> gruposDeHoy() {
        Map<UserId, String> grupos = new HashMap<>();
        acompanamiento.gruposOperativos(clock.now()).forEach(grupo ->
                grupo.aprendices().forEach(aprendiz -> grupos.putIfAbsent(aprendiz, grupo.nombre())));
        return grupos;
    }

    private static String normalizado(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
