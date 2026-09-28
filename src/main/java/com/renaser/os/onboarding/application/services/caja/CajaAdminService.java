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
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La lista del Admin, su descarga y el detalle de una caja (D-219).
 *
 * <p><b>Se arma el padrón entero en memoria</b> (página por página, en lote) y después se filtra: las
 * pestañas necesitan el conteo de TODOS los estados, y el estado se deriva, así que no hay columna por la que
 * filtrar en SQL. Con el padrón de hoy (decenas de aprendices, cientos a lo sumo) son pocas consultas; si
 * creciera a miles, esto es lo que hay que materializar.
 */
@Service
public class CajaAdminService implements ConsultarCajasUseCase, VerCajaUseCase {

    private static final int TAMANO_MAXIMO = 200;

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
        List<ResumenDeCaja> todas = resumenes();
        Map<EstadoCaja, Integer> conteos = new EnumMap<>(EstadoCaja.class);
        for (EstadoCaja estado : EstadoCaja.values()) {
            if (estado != EstadoCaja.NO_APLICA) {
                conteos.put(estado, (int) todas.stream().filter(r -> r.estado() == estado).count());
            }
        }
        String buscado = normalizado(filtro.q());
        List<ResumenDeCaja> filtradas = todas.stream()
                .filter(r -> filtro.estado() == null ? r.estado() != EstadoCaja.NO_APLICA : r.estado() == filtro.estado())
                .filter(r -> buscado.isEmpty() || normalizado(r.nombre()).contains(buscado))
                .toList();
        int tamano = Math.clamp(filtro.tamano(), 1, TAMANO_MAXIMO);
        int desde = Math.max(0, filtro.pagina()) * tamano;
        List<ResumenDeCaja> pagina = desde >= filtradas.size() ? List.of()
                : filtradas.subList(desde, Math.min(filtradas.size(), desde + tamano));
        return new PaginaDeCajas(pagina, filtradas.size(), conteos);
    }

    @Override
    public List<ResumenDeCaja> exportar(UserId actorId) {
        guardia.exigirAdmin(actorId);
        return resumenes().stream().filter(r -> r.estado() != EstadoCaja.NO_APLICA).toList();
    }

    @Override
    public DetalleDeCaja ver(UserId actorId, UserId aprendizId) {
        guardia.exigirAdmin(actorId);
        return lectura.detalle(padron.deAprendiz(aprendizId));
    }

    private List<ResumenDeCaja> resumenes() {
        Map<UserId, String> grupos = gruposDeHoy();
        return padron.todas().stream()
                .map(c -> resumen(c, grupos.get(c.caja().aprendizId())))
                .sorted(Comparator.comparing(r -> normalizado(r.nombre())))
                .toList();
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
