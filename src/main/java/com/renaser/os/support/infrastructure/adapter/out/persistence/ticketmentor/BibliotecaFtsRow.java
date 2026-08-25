package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

/** Proyeccion de la query nativa de busqueda full-text (ver SpringDataTicketMentorRepository). */
public interface BibliotecaFtsRow {

    String getDescripcionBloqueo();

    String getRespuestaMentor();
}
