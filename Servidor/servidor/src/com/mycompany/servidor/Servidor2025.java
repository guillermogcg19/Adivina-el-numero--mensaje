package com.mycompany.servidor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Servidor2025 {

    // === Bandejas ===
    private static final File MENSAJES_DIR = new File("mensajes");
    static { if (!MENSAJES_DIR.exists()) MENSAJES_DIR.mkdirs(); }

    private static File archivoInbox(String usuario) {
        return new File(MENSAJES_DIR, usuario + ".txt");
    }
    private static List<String> leerInbox(String usuario) {
        List<String> msgs = new ArrayList<>();
        File f = archivoInbox(usuario);
        if (!f.exists()) return msgs;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l; while ((l = br.readLine()) != null) msgs.add(l);
        } catch (IOException ignored) {}
        return msgs;
    }
    private static void vaciarInbox(String usuario) {
        File f = archivoInbox(usuario);
        try (PrintWriter pw = new PrintWriter(f)) { /* truncar */ } catch (IOException ignored) {}
    }

    // === Online + mensajería para admin/usuarios ===
    private static final ConcurrentMap<String, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    private static synchronized void guardarMensaje(String usuario, String texto) {
        File f = archivoInbox(usuario);
        try {
            f.getParentFile().mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
                pw.println(new Date() + " | " + texto);
            }
        } catch (IOException e) {
            System.out.println("Error guardando mensaje para " + usuario + ": " + e.getMessage());
        }
    }
    private static void enviarMensajeASingle(String usuario, String texto) {
        guardarMensaje(usuario, texto);
        ClientHandler ch = ONLINE.get(usuario);
        if (ch != null) ch.safeSend("Tienes un nuevo mensaje en tu bandeja.");
    }
    private static List<String> usuariosConocidosPorBandeja() {
        List<String> res = new ArrayList<>();
        File[] files = MENSAJES_DIR.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) return res;
        for (File f : files) {
            String name = f.getName();
            res.add(name.substring(0, name.length() - 4));
        }
        return res;
    }

    // === Consola admin ===
    private static void adminLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Consola admin activa. /help para ver comandos.");
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if ("/help".equalsIgnoreCase(line)) {
                    System.out.println("""
                        Comandos:
                          /online                    -> conectados
                          /enviar <usuario> <txt>    -> mensaje a un usuario
                          /broadcast <txt>           -> mensaje a todos los conocidos (por bandeja)
                        """);
                    continue;
                }
                if ("/online".equalsIgnoreCase(line)) {
                    System.out.println("Conectados (" + ONLINE.size() + "): " + ONLINE.keySet());
                    continue;
                }
                if (line.startsWith("/enviar ")) {
                    String rest = line.substring(8).trim();
                    int sp = rest.indexOf(' ');
                    if (sp <= 0) { System.out.println("Uso: /enviar <usuario> <texto>"); continue; }
                    String usuario = rest.substring(0, sp).trim();
                    String texto   = rest.substring(sp + 1).trim();
                    enviarMensajeASingle(usuario, "[ADMIN] " + texto);
                    System.out.println("Mensaje enviado a " + usuario);
                    continue;
                }
                if (line.startsWith("/broadcast ")) {
                    String texto = line.substring(11).trim();
                    List<String> users = usuariosConocidosPorBandeja();
                    for (String u : users) enviarMensajeASingle(u, "[ADMIN] " + texto);
                    System.out.println("Broadcast enviado a " + users.size() + " usuarios.");
                    continue;
                }
                System.out.println("Comando no reconocido. Usa /help.");
            }
        } catch (IOException e) {
            System.out.println("Consola admin cerrada: " + e.getMessage());
        }
    }

    // === Main ===
    public static void main(String[] args) {
        Thread admin = new Thread(Servidor2025::adminLoop, "ADMIN-CONSOLE");
        admin.setDaemon(true);
        admin.start();

        try (ServerSocket servidor = new ServerSocket(9090)) {
            System.out.println("Servidor en puerto 9090. Esperando conexiones...");
            while (true) {
                Socket socket = servidor.accept();
                new Thread(new ClientHandler(socket), "CLIENT-" + socket.getRemoteSocketAddress()).start();
            }
        } catch (IOException e) {
            System.out.println("Error en el servidor: " + e.getMessage());
        }
    }

    // === Cliente ===
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String usuarioBase;
        private final Random rand = new Random();

        ClientHandler(Socket socket) { this.socket = socket; }

        void safeSend(String msg) { try { if (out != null) out.println(msg); } catch (Exception ignored) {} }

        @Override public void run() {
            try (Socket s = socket) {
                out = new PrintWriter(s.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(s.getInputStream()));

                // Identificación simple (aquí luego se mezclará autenticación)
                out.println("Bienvenido. Escribe tu nombre de usuario:");
                String u = in.readLine();
                if (u == null || u.trim().isEmpty()) { out.println("Usuario inválido. Bye."); return; }
                usuarioBase = u.trim();
                try { archivoInbox(usuarioBase).createNewFile(); } catch (IOException ignored) {}
                ONLINE.put(usuarioBase, this);

                out.println("Hola " + usuarioBase + ". Conexion establecida.");
                loopMenu();
            } catch (IOException ignored) {
            } finally {
                if (usuarioBase != null) ONLINE.remove(usuarioBase);
            }
        }

        private void loopMenu() throws IOException {
            boolean seguir = true;
            while (seguir) {
                out.println();
                out.println("=== MENU ===");
                out.println("1) Jugar adivina numero");
                out.println("2) Bandeja de entrada");
                out.println("3) Salir");
                out.println("Elige opcion:");
                String op = in.readLine();
                if (op == null) break;

                switch (op.trim()) {
                    case "1":
                        out.println("Opcion 'Jugar' aun no implementada.");
                        break;
                    case "2":
                        mostrarBandeja();
                        break;
                    case "3":
                        out.println("Gracias. Adios!");
                        seguir = false;
                        break;
                    default:
                        out.println("Opcion invalida.");
                }
            }
        }

        private void mostrarBandeja() throws IOException {
            List<String> msgs = leerInbox(usuarioBase);
            if (msgs.isEmpty()) { out.println("Tu bandeja esta vacia."); return; }
            out.println("Mensajes (" + msgs.size() + "):");
            int i = 1; for (String m : msgs) out.println(" " + (i++) + ") " + m);
            out.println("Quieres vaciar la bandeja? (si/no)");
            String r = in.readLine();
            if (r != null && r.trim().equalsIgnoreCase("si")) { vaciarInbox(usuarioBase); out.println("Bandeja vaciada."); }
            else { out.println("Bandeja conservada."); }
        }
    }
}
