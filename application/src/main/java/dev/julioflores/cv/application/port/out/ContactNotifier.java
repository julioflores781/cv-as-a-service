package dev.julioflores.cv.application.port.out;

import dev.julioflores.cv.domain.ContactMessage;

public interface ContactNotifier {

    /**
     * Avisa al dueño de que hay un mensaje nuevo. El mensaje ya está guardado: un fallo del aviso <b>no debe lanzar</b>
     * (el adaptador lo registra sin datos personales) y no debe bloquear la petición.
     */
    void notify(ContactMessage message);
}
