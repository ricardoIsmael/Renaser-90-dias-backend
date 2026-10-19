package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformeEspejoSombraUseCase;
import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformesSemanalesUseCase;
import com.renaser.os.rag.domain.model.espejosombra.SemanaDelInforme;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Barrido horario del Espejo Sombra (E-560). Antes corría una vez por semana, el lunes 03:00 UTC con la fecha del
 * servidor (domingo 22:00 en Lima); ahora corre cada hora y {@link SemanaDelInforme} decide, con la zona de cada participante, a quién ya
 * le toca (regla 02 §1 y §2).
 *
 * <p>Sin {@code @Transactional} (regla 02 §4): {@code generar} llama a la IA y no puede retener una conexión (C-1);
 * las lecturas y el guardado ya son transacciones cortas propias. Un participante que falla no detiene el barrido.
 * El padrón se recorre por lotes: la zona se pide en UNA consulta por lote, nunca una por persona.
 *
 * <p><b>Las cuentas de prueba de «ver como» no tienen informe</b> (D-273, decisión del coordinador del 2026-10-14): cada
 * informe es una llamada a la IA y nadie lo lee. El aprendiz de prueba sí está en el padrón de inscritos (los hábitos
 * de su programa se arman como los de cualquiera), así que se descarta acá, con UNA lectura de resúmenes por lote.
 */
@Service
public class BarridoDeInformesSemanalesService implements GenerarInformesSemanalesUseCase {

    private static final Logger log = LoggerFactory.getLogger(BarridoDeInformesSemanalesService.class);

    static final int TAMANO_LOTE = 200;

    /**
     * Quien aún no activó su programa no tiene zona resuelta por el finder; su columna vale este default
     * ({@code participantes_programa.timezone}) y hoy es la zona de todo el padrón.
     */
    private static final ZoneId ZONA_DEL_PADRON = ZoneId.of("America/Lima");

    private final GenerarInformeEspejoSombraUseCase generarInforme;
    private final ParticipacionProgramaFinder participaciones;
    private final ProgramasActivadosFinder programas;
    private final UserSummaryFinder usuarios;
    private final Clock clock;

    public BarridoDeInformesSemanalesService(GenerarInformeEspejoSombraUseCase generarInforme,
                                              ParticipacionProgramaFinder participaciones,
                                              ProgramasActivadosFinder programas, UserSummaryFinder usuarios,
                                              Clock clock) {
        this.generarInforme = generarInforme;
        this.participaciones = participaciones;
        this.programas = programas;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    public void generarLosQueTocan() {
        Instant ahora = clock.now();
        List<UserId> padron = participaciones.participantesInscritosActivos();
        for (int desde = 0; desde < padron.size(); desde += TAMANO_LOTE) {
            recorrer(padron.subList(desde, Math.min(desde + TAMANO_LOTE, padron.size())), ahora);
        }
    }

    private void recorrer(List<UserId> lote, Instant ahora) {
        Map<UserId, ProgramaActivado> programasDelLote = programas.deVarios(lote);
        Set<UserId> deCuentasDePrueba = usuarios.findByIds(lote).values().stream()
                .filter(UserSummary::esCuentaDePrueba).map(UserSummary::id).collect(Collectors.toSet());
        for (UserId participante : lote) {
            if (!deCuentasDePrueba.contains(participante)) {
                generarSinTumbarElBarrido(participante, zonaDe(programasDelLote.get(participante)), ahora);
            }
        }
    }

    private static ZoneId zonaDe(ProgramaActivado programa) {
        return programa != null ? programa.zona() : ZONA_DEL_PADRON;
    }

    private void generarSinTumbarElBarrido(UserId participante, ZoneId zona, Instant ahora) {
        try {
            SemanaDelInforme.quePideGenerarseEn(zona, ahora)
                    .ifPresent(semana -> generarInforme.generar(participante, semana.inicio()));
        } catch (RuntimeException e) {
            log.error("[rag.BarridoDeInformesSemanales] fallo generando informe. participante={}: {}",
                    participante, e.getMessage(), e);
        }
    }
}
