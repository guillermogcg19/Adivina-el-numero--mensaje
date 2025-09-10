package com.mycompany.servidor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader; 
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Servidor2025 {

    // ====== Bandejas (inbox) ======
    private static final File MENSAJES_DIR = new File("mensajes");

    static {
        if (!MENSAJES_DIR.exists()) {
            MENSAJES_DIR.mkdirs();
        }
    }

    private static File archivoInbox(String usuario) {
        return new File(MENSAJES_DIR, usuario + ".txt");
    }

    private static List<String> leerInbox(String usuario) {
        List<String> msgs = new ArrayList<>();
        File f = archivoInbox(usuario);
        if (!f.exists()) return msgs;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) msgs.add(l);
        } catch (IOException ignored) {}
        return msgs;
    }

    private static void vaciarInbox(String usuario) {
        File f = archivoInbox(usuario);
        try (PrintWriter pw = new PrintWriter(f)) {
            // truncar archivo
        } catch (IOException ignored) {}
    }

    // ====== Main ======
    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(9090)) {
            System.out.println("Servidor en puerto 9090. Esperando conexiones...");
            while (true) {
                Socket socket = servidor.accept();
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler, "CLIENT-" + socket.getRemoteSocketAddress()).start();
            }
        } catch (IOException e) {
            System.out.println("Error en el servidor: " + e.getMessage());
        }
    }

    // ====== Handler por cliente ======
    private static class ClientHandler implements Runnable {
        private final Socket socket;

        // CAMPOS (NO USAR System.in/out)
        private PrintWriter out;
        private BufferedReader in;
        private String usuarioBase;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                // INICIALIZAR out/in UNA SOLA VEZ AQUI
                out = new PrintWriter(s.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(s.getInputStream()));

                // Pedimos un nombre de usuario para identificar la bandeja
                out.println("Bienvenido. Escribe tu nombre de usuario:");
                String u = in.readLine();                 // <= ESTA ES LA LINEA QUE FALLABA SI in ERA NULL
                if (u == null || u.trim().isEmpty()) {
                    out.println("Usuario invalido. Cerrando conexion.");
                    return;
                }
                usuarioBase = u.trim();

                // Asegurar archivo de bandeja
                try { archivoInbox(usuarioBase).createNewFile(); } catch (IOException ignored) {}

                out.println("Hola " + usuarioBase + ". Conexion establecida.");
                loopMenu();

            } catch (IOException e) {
                // cliente cerro o error IO
            }
        }

        // ====== Menu principal ======
        private void loopMenu() throws IOException {
            boolean seguir = true;
            while (seguir) {
                out.println();
                out.println("=== MENU ===");
                out.println("1) Jugar adivina numero");
                out.println("2) Bandeja de entrada");
                out.println("3) Salir");
                out.println("Elige opcion:");

                String op = in.readLine(); // usa el CAMPO 'in' ya inicializado
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

        // ====== Bandeja ======
        private void mostrarBandeja() throws IOException {
            List<String> msgs = leerInbox(usuarioBase);

            if (msgs.isEmpty()) {
                out.println("Tu bandeja esta vacia.");
                return;
            }

            out.println("Mensajes (" + msgs.size() + "):");
            int i = 1;
            for (String m : msgs) {
                out.println(" " + (i++) + ") " + m);
            }

            out.println("Quieres vaciar la bandeja? (si/no)");
            String r = in.readLine(); // usa el CAMPO 'in'
            if (r != null && r.trim().equalsIgnoreCase("si")) {
                vaciarInbox(usuarioBase);
                out.println("Bandeja vaciada.");
            } else {
                out.println("Bandeja conservada.");
            }
        }
    }
}
