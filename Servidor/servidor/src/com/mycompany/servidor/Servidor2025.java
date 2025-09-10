/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.servidor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import static java.lang.System.in;
import static java.lang.System.out;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

/**
 *
 * @author Guillermo
 */
public class Servidor2025 {

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(9090)) {
            System.out.println("Servidor en puerto 9090. Esperando conexiones...");

            while (true) {
                Socket socket = servidor.accept();
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.out.println("Error en el servidor: " + e.getMessage());
        }
    }

   // ====== Handler por cliente ======
private static class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String usuarioMostrado;
    private String usuarioBase;
    private final Random rand = new Random();

    ClientHandler(Socket socket) {
        this.socket = socket;
    }

    void safeSend(String msg) {
        try { if (out != null) out.println(msg); } catch (Exception ignored) {}
    }

    @Override
    public void run() {
        try (Socket s = socket) {
            out = new PrintWriter(s.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(s.getInputStream()));

            // autenticacion ya existente
            usuarioMostrado = autenticarUsuario();
            if (usuarioMostrado == null) {
                out.println("Error en autenticacion. Cerrando conexion...");
                return;
            }

            usuarioBase = usuarioMostrado.endsWith(" (Invitado)")
                    ? usuarioMostrado.substring(0, usuarioMostrado.indexOf(" (Invitado)"))
                    : usuarioMostrado;

            try { archivoInbox(usuarioBase).createNewFile(); } catch (IOException ignored) {}
            ONLINE.put(usuarioBase, this);

            out.println("Bienvenido " + usuarioMostrado + " al sistema.");
            loopMenu();

        } catch (IOException e) {
            // cierre cliente o IO
        } finally {
            if (usuarioBase != null) ONLINE.remove(usuarioBase);
        }
    }

    private String autenticarUsuario() throws IOException {
        out.println("Elige una opcion: (1) Registrarse, (2) Iniciar sesion, (3) Invitado");
        String opcion = in.readLine();
        if (opcion == null) return null;

        switch (opcion.trim()) {
            case "1": {
                out.println("Introduce un nombre de usuario:");
                String nuevoUsuario = in.readLine();
                out.println("Introduce una contrasena:");
                String nuevaPassword = in.readLine();

                if (nuevoUsuario == null || nuevaPassword == null || nuevoUsuario.trim().isEmpty())
                    return null;

                if (existeUsuarioRegistrado(nuevoUsuario.trim())) {
                    out.println("Usuario ya existe.");
                    return null;
                }

                try { guardarUsuario(nuevoUsuario.trim(), nuevaPassword.trim()); } catch (IOException ignored) {}
                try { archivoInbox(nuevoUsuario.trim()).createNewFile(); } catch (IOException ignored) {}
                out.println("Usuario registrado con exito!");
                return nuevoUsuario.trim();
            }
            case "2": {
                out.println("Introduce tu nombre de usuario:");
                String usuario = in.readLine();
                out.println("Introduce tu contrasena:");
                String password = in.readLine();

                if (usuario != null && password != null && validarUsuario(usuario.trim(), password.trim())) {
                    try { archivoInbox(usuario.trim()).createNewFile(); } catch (IOException ignored) {}
                    out.println("Inicio de sesion exitoso!");
                    return usuario.trim();
                } else {
                    out.println("Usuario o contrasena incorrectos.");
                    return null;
                }
            }
            case "3": {
                out.println("Introduce tu nombre de usuario invitado:");
                String invitado = in.readLine();
                if (invitado != null && !invitado.trim().isEmpty()) {
                    try { guardarInvitado(invitado.trim()); } catch (IOException ignored) {}
                    return invitado.trim() + " (Invitado)";
                }
                return "Invitado (Invitado)";
            }
            default:
                out.println("Opcion invalida.");
                return null;
        }
    }

    // ====== Menu con mensajeria y juego activo ======
    private void loopMenu() throws IOException {
        boolean seguir = true;
        while (seguir) {
            out.println();
            out.println("=== MENU ===");
            out.println("1) Jugar adivina numero");
            out.println("2) Bandeja de entrada");
            out.println("3) Salir");
            out.println("4) Enviar mensaje a usuario");
            out.println("5) Ver conectados");
            out.println("Elige opcion:");

            String op = in.readLine();
            if (op == null) break;

            switch (op.trim()) {
                case "1":
                    juegoAdivina();            // ← ACTIVADO
                    break;
                case "2":
                    mostrarBandeja();
                    break;
                case "3":
                    out.println("Gracias. Adios!");
                    seguir = false;
                    break;
                case "4":
                    enviarMensajeAUsuario();
                    break;
                case "5":
                    verConectados();
                    break;
                default:
                    out.println("Opcion invalida.");
            }
        }
    }

    private void mostrarBandeja() throws IOException {
        List<String> msgs = leerInbox(usuarioBase);
        if (msgs.isEmpty()) {
            out.println("Tu bandeja esta vacia.");
            return;
        }
        out.println("Mensajes (" + msgs.size() + "):");
        int i = 1;
        for (String m : msgs) out.println(" " + (i++) + ") " + m);

        out.println("Quieres vaciar la bandeja? (si/no)");
        String r = in.readLine();
        if (r != null && r.trim().equalsIgnoreCase("si")) {
            vaciarInbox(usuarioBase);
            out.println("Bandeja vaciada.");
        } else {
            out.println("Bandeja conservada.");
        }
    }

    private void enviarMensajeAUsuario() throws IOException {
        out.println("Usuario destinatario:");
        String dest = in.readLine();
        if (dest == null || dest.trim().isEmpty()) {
            out.println("Cancelado: destinatario vacio.");
            return;
        }
        dest = dest.trim();
        if (!existeUsuarioRegistrado(dest)) {
            out.println("Usuario no registrado: " + dest);
            return;
        }
        out.println("Escribe el mensaje:");
        String texto = in.readLine();
        if (texto == null || texto.trim().isEmpty()) {
            out.println("Cancelado: mensaje vacio.");
            return;
        }
        enviarMensajeASingle(dest, "[De " + usuarioBase + "] " + texto.trim());
        out.println("Mensaje enviado a " + dest + ".");
    }

    private void verConectados() {
        out.println("Conectados (" + ONLINE.size() + "): " + ONLINE.keySet());
    }

    // ====== Juego activo ======
    private void juegoAdivina() throws IOException {
        boolean jugarOtra = true;
        while (jugarOtra) {
            int numeroSecreto = rand.nextInt(10) + 1;
            int intentos = 0, maxIntentos = 3;
            boolean ok = false;

            out.println("Adivina un numero del 1 al 10. Tienes 3 intentos.");
            while (intentos < maxIntentos && !ok) {
                String entrada = in.readLine();
                if (entrada == null) return;
                entrada = entrada.trim();
                if (entrada.isEmpty()) {
                    out.println("Escribe un numero valido. No pierdes intento.");
                    continue;
                }
                try {
                    int intento = Integer.parseInt(entrada);
                    intentos++;
                    if (intento == numeroSecreto) {
                        out.println("Adivinaste en " + intentos + " intento(s)!");
                        ok = true;
                    } else if (intento < numeroSecreto) {
                        out.println("Mas alto. Quedan " + (maxIntentos - intentos) + " intentos.");
                    } else {
                        out.println("Mas bajo. Quedan " + (maxIntentos - intentos) + " intentos.");
                    }
                } catch (NumberFormatException e) {
                    out.println("Entrada invalida. No pierdes intento.");
                }
            }
            if (!ok) out.println("Fallaste. El numero era " + numeroSecreto + ".");

            out.println("¿Jugar otra vez? (si/no)");
            String r = in.readLine();
            jugarOtra = (r != null && r.trim().equalsIgnoreCase("si"));
        }
    }
}
}
