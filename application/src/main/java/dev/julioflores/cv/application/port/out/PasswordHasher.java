package dev.julioflores.cv.application.port.out;

public interface PasswordHasher {

    /** Hash autodescriptivo (algoritmo, parámetros y sal dentro) de la contraseña. */
    String hash(String password);

    /** {@code false} si no coincide o si el hash está corrupto o es de otro algoritmo: nunca lanza. */
    boolean verify(String password, String hash);

    /** {@code true} si el hash se hizo con menos coste que el vigente y conviene rehacerlo tras un login correcto. */
    boolean needsRehash(String hash);
}
