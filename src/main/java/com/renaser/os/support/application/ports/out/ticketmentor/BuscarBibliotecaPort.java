package com.renaser.os.support.application.ports.out.ticketmentor;

import java.util.List;

public interface BuscarBibliotecaPort {

    List<EntradaBiblioteca> buscar(String query, int limite);

    record EntradaBiblioteca(String descripcionBloqueo, String respuestaMentor) {
    }
}
