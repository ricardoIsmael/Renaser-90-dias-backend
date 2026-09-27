package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.BienvenidaDeGrupo;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.MarcaDeBienvenidaEnGrupoPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quién espera su bienvenida de grupo y la marca (D-191). No escribe mensajes: eso es de {@code chat}.
 *
 * <p>Solo el grupo ESTABLE: la recepción es el grupo temporal de los primeros días y la atienden
 * guías, no un mentor. Y solo con mentor vigente: sin quien la firme, la pertenencia queda pendiente.
 */
@Service
class BienvenidaDeGrupoService implements BienvenidaDeGrupo {

    private final LoadCelulaPort loadCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final MarcaDeBienvenidaEnGrupoPort marcaPort;
    private final AcompanamientoFinder acompanamientoFinder;

    BienvenidaDeGrupoService(LoadCelulaPort loadCelulaPort, LoadAsignacionesPort loadAsignacionesPort,
                             MarcaDeBienvenidaEnGrupoPort marcaPort, AcompanamientoFinder acompanamientoFinder) {
        this.loadCelulaPort = loadCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.marcaPort = marcaPort;
        this.acompanamientoFinder = acompanamientoFinder;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pendientes> pendientes(UUID grupoId, Instant instante) {
        boolean estable = loadCelulaPort.porId(CelulaId.of(grupoId)).map(c -> !c.esRecepcion()).orElse(false);
        if (!estable) {
            return Optional.empty();
        }
        List<AsignacionCelula> vigentes = loadAsignacionesPort.porCelula(CelulaId.of(grupoId)).stream()
                .filter(a -> a.vigenteEn(instante))
                .toList();
        return mentorQueFirma(vigentes, grupoId, instante)
                .map(mentor -> new Pendientes(grupoId, mentor, aprendicesSinBienvenida(vigentes)));
    }

    @Override
    @Transactional
    public boolean marcarDada(UUID asignacionId, Instant instante) {
        return marcaPort.marcar(asignacionId, instante);
    }

    /** El mentor vigente, si el grupo además está dentro de su periodo (lo decide {@code acompanaVigente}). */
    private Optional<UserId> mentorQueFirma(List<AsignacionCelula> vigentes, UUID grupoId, Instant instante) {
        return vigentes.stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .map(AsignacionCelula::usuarioId)
                .findFirst()
                .filter(mentor -> acompanamientoFinder.acompanaVigente(mentor, grupoId, instante));
    }

    private List<Pendiente> aprendicesSinBienvenida(List<AsignacionCelula> vigentes) {
        List<AsignacionCelula> aprendices = vigentes.stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .toList();
        Set<UUID> sinMarca = marcaPort.sinBienvenida(aprendices.stream().map(a -> a.id().value()).toList());
        return aprendices.stream()
                .filter(a -> sinMarca.contains(a.id().value()))
                .map(a -> new Pendiente(a.id().value(), a.usuarioId()))
                .toList();
    }
}
