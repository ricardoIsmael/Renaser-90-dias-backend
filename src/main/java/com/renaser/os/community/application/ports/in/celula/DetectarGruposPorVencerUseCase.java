package com.renaser.os.community.application.ports.in.celula;

/**
 * Barre los grupos programados y avisa al administrador de los que estan por cerrar.
 *
 * <p>Sin comando: no lo invoca una persona, lo dispara el barrido diario. El resultado dice
 * cuantos avisos se publicaron, que es lo unico que el scheduler necesita para su linea de log.
 */
public interface DetectarGruposPorVencerUseCase {

    int avisarDeLosQueVencen();
}
