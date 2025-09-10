package com.mycompany.servidor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
<<<<<<< HEAD
<<<<<<< HEAD
import java.io.InputStreamReader; 
=======
import java.io.InputStreamReader;
>>>>>>> admin-consola
=======
import java.io.FileWriter;
import java.io.InputStreamReader;
>>>>>>> autenticación_(registro/login/invitado)
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
<<<<<<< HEAD
<<<<<<< HEAD
import java.util.List;
=======
=======
>>>>>>> autenticación_(registro/login/invitado)
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
<<<<<<< HEAD
>>>>>>> admin-consola

public class Servidor2025 {

    // ====== Bandejas (inbox) ======
    private static final File MENSAJES_DIR = new File("mensajes");
=======

public class Servidor2025 {

    // ====== Archivos de datos ======
    private static final File MENSAJES_DIR  = new File("mensajes");
    private static final File USUARIOS_FILE = new File("usuarios.txt");
    private static final File INVITADOS_FILE = new File("invitados.txt");
>>>>>>> autenticación_(registro/login/invitado)

    static {
        if (!MENSAJES_DIR.exists()) {
            MENSAJES_DIR.mkdirs();
        }
<<<<<<< HEAD
    }

=======
        try { if (!USUARIOS_FILE.exists()) USUARIOS_FILE.createNewFile(); } catch (IOException ignored) {}
        try { if (!INVITADOS_FILE.exists()) INVITADOS_FILE.createNewFile(); } catch (IOException ignored) {}
    }

    // ====== Inbox ======
>>>>>>> autenticación_(registro/login/invitado)
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
<<<<<<< HEAD
            // truncar archivo
        } catch (IOException ignored) {}
    }

<<<<<<< HEAD
    // ====== Main ======
    public static void main(String[] args) {
=======
    // ====== Online tracking ======
    private static final ConcurrentMap<String, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    // ====== Envío de mensajes (admin / enviar usuarios) ======
=======
            // truncar
        } catch (IOException ignored) {}
    }

    // ====== Usuarios ======
    private static synchronized void guardarUsuario(String usuario, String password) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USUARIOS_FILE, true))) {
            pw.println(usuario + "," + password);
        }
    }

    private static boolean validarUsuario(String usuario, String password) {
        try (BufferedReader br = new BufferedReader(new FileReader(USUARIOS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 2);
                if (p.length >= 2 && p[0].equals(usuario) && p[1].equals(password)) {
                    return true;
                }
            }
        } catch (IOException ignored) {}
        return false;
    }

    private static synchronized void guardarInvitado(String invitado) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(INVITADOS_FILE, true))) {
            pw.println(invitado);
        }
    }

    private static boolean existeUsuarioRegistrado(String usuario) {
        try (BufferedReader br = new BufferedReader(new FileReader(USUARIOS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 2);
                if (p.length >= 1 && p[0].trim().equals(usuario)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private static List<String> listarUsuariosRegistrados() {
        List<String> res = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(USUARIOS_FILE))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split(",", 2);
                if (p.length >= 1) {
                    String u = p[0].trim();
                    if (!u.isEmpty()) res.add(u);
                }
            }
        } catch (IOException ignored) {}
        return res;
    }

    // ====== Online tracking ======
    private static final ConcurrentMap<String, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    // ====== Mensajeria (admin/enviar) ======
>>>>>>> autenticación_(registro/login/invitado)
    private static synchronized void guardarMensaje(String usuario, String texto) {
        File f = archivoInbox(usuario);
        try {
            f.getParentFile().mkdirs();
<<<<<<< HEAD
            try (PrintWriter pw = new PrintWriter(new java.io.FileWriter(f, true))) {
=======
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
>>>>>>> autenticación_(registro/login/invitado)
                pw.println(new Date() + " | " + texto);
            }
        } catch (IOException e) {
            System.out.println("Error guardando mensaje para " + usuario + ": " + e.getMessage());
        }
    }

    private static void enviarMensajeASingle(String usuario, String texto) {
        guardarMensaje(usuario, texto);
        ClientHandler ch = ONLINE.get(usuario);
<<<<<<< HEAD
        if (ch != null) {
            ch.safeSend("Tienes un nuevo mensaje en tu bandeja.");
        }
    }

    private static List<String> usuariosConocidos() {
        List<String> res = new ArrayList<>();
        if (!MENSAJES_DIR.exists()) return res;
=======
        if (ch != null) ch.safeSend("Tienes un nuevo mensaje en tu bandeja.");
    }

    private static List<String> usuariosConocidosPorBandeja() {
        List<String> res = new ArrayList<>();
>>>>>>> autenticación_(registro/login/invitado)
        File[] files = MENSAJES_DIR.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) return res;
        for (File f : files) {
            String name = f.getName();
<<<<<<< HEAD
            if (name.endsWith(".txt")) {
                res.add(name.substring(0, name.length() - 4));
            }
=======
            if (name.endsWith(".txt")) res.add(name.substring(0, name.length() - 4));
>>>>>>> autenticación_(registro/login/invitado)
        }
        return res;
    }

    // ====== Consola admin ======
    private static void adminLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Consola admin activa. Escribe /help para ver comandos.");
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("/help")) {
                    System.out.println(
                        "Comandos:\n" +
<<<<<<< HEAD
                        "  /online                  -> lista usuarios conectados\n" +
                        "  /enviar <usuario> <txt>  -> envia mensaje a un usuario\n" +
                        "  /broadcast <txt>         -> envia mensaje a todos los usuarios conocidos\n"
=======
                        "  /online                    -> lista usuarios conectados\n" +
                        "  /usuarios                  -> lista usuarios registrados\n" +
                        "  /enviar <usuario> <txt>    -> envia mensaje a un usuario\n" +
                        "  /broadcast <txt>           -> envia mensaje a todos los registrados\n"
>>>>>>> autenticación_(registro/login/invitado)
                    );
                    continue;
                }

                if (line.equalsIgnoreCase("/online")) {
                    System.out.println("Conectados (" + ONLINE.size() + "): " + ONLINE.keySet());
                    continue;
                }

<<<<<<< HEAD
=======
                if (line.equalsIgnoreCase("/usuarios")) {
                    List<String> u = listarUsuariosRegistrados();
                    System.out.println("Usuarios registrados (" + u.size() + "): " + u);
                    continue;
                }

>>>>>>> autenticación_(registro/login/invitado)
                if (line.startsWith("/enviar ")) {
                    String rest = line.substring(8).trim();
                    int sp = rest.indexOf(' ');
                    if (sp <= 0) {
                        System.out.println("Uso: /enviar <usuario> <texto>");
                        continue;
                    }
                    String usuario = rest.substring(0, sp).trim();
                    String texto = rest.substring(sp + 1).trim();
<<<<<<< HEAD
                    if (usuario.isEmpty() || texto.isEmpty()) {
                        System.out.println("Uso: /enviar <usuario> <texto>");
=======
                    if (!existeUsuarioRegistrado(usuario)) {
                        System.out.println("Usuario no registrado: " + usuario);
>>>>>>> autenticación_(registro/login/invitado)
                        continue;
                    }
                    enviarMensajeASingle(usuario, "[ADMIN] " + texto);
                    System.out.println("Mensaje enviado a " + usuario);
                    continue;
                }

                if (line.startsWith("/broadcast ")) {
                    String texto = line.substring(11).trim();
                    if (texto.isEmpty()) {
                        System.out.println("Uso: /broadcast <texto>");
                        continue;
                    }
<<<<<<< HEAD
                    List<String> users = usuariosConocidos();
                    if (users.isEmpty()) {
                        System.out.println("No hay usuarios conocidos (aun no hay bandejas creadas).");
                        continue;
                    }
                    for (String u : users) {
                        enviarMensajeASingle(u, "[ADMIN] " + texto);
                    }
=======
                    List<String> users = listarUsuariosRegistrados();
                    if (users.isEmpty()) {
                        // fallback si aun no hay usuarios registrados
                        users = usuariosConocidosPorBandeja();
                        System.out.println("No hay usuarios registrados. Usando bandejas existentes: " + users);
                    }
                    for (String u : users) enviarMensajeASingle(u, "[ADMIN] " + texto);
>>>>>>> autenticación_(registro/login/invitado)
                    System.out.println("Broadcast enviado a " + users.size() + " usuarios.");
                    continue;
                }

                System.out.println("Comando no reconocido. Usa /help.");
            }
        } catch (IOException e) {
            System.out.println("Consola admin cerrada: " + e.getMessage());
        }
    }

    // ====== Main ======
    public static void main(String[] args) {
<<<<<<< HEAD
        // Levantar consola admin en hilo aparte
=======
>>>>>>> autenticación_(registro/login/invitado)
        Thread admin = new Thread(Servidor2025::adminLoop, "ADMIN-CONSOLE");
        admin.setDaemon(true);
        admin.start();

<<<<<<< HEAD
>>>>>>> admin-consola
=======
>>>>>>> autenticación_(registro/login/invitado)
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
        private PrintWriter out;
        private BufferedReader in;
<<<<<<< HEAD
        private String usuarioBase;
        private final Random rand = new Random();

        // CAMPOS (NO USAR System.in/out)
        private PrintWriter out;
        private BufferedReader in;
        private String usuarioBase;
=======
        private String usuarioMostrado;
        private String usuarioBase;
        private final Random rand = new Random();
>>>>>>> autenticación_(registro/login/invitado)

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

<<<<<<< HEAD
<<<<<<< HEAD
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

=======
        void safeSend(String msg) {
            try {
                if (out != null) out.println(msg);
            } catch (Exception ignored) {}
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                out = new PrintWriter(s.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(s.getInputStream()));

                // Identificar al usuario (simple)
                out.println("Bienvenido. Escribe tu nombre de usuario:");
                String u = in.readLine();
                if (u == null || u.trim().isEmpty()) {
                    out.println("Usuario invalido. Cerrando conexion.");
                    return;
                }
                usuarioBase = u.trim();

                // Crear/asegurar su bandeja
                try { archivoInbox(usuarioBase).createNewFile(); } catch (IOException ignored) {}

                // Marcar online
                ONLINE.put(usuarioBase, this);

                out.println("Hola " + usuarioBase + ". Conexion establecida.");
                loopMenu();

            } catch (IOException e) {
                // cliente cerro o error IO
=======
        void safeSend(String msg) {
            try { if (out != null) out.println(msg); } catch (Exception ignored) {}
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                out = new PrintWriter(s.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(s.getInputStream()));

                usuarioMostrado = autenticarUsuario();
                if (usuarioMostrado == null) {
                    out.println("Error en autenticacion. Cerrando conexion...");
                    return;
                }

                // usuarioBase sin sufijo (Invitado)
                usuarioBase = usuarioMostrado.endsWith(" (Invitado)")
                        ? usuarioMostrado.substring(0, usuarioMostrado.indexOf(" (Invitado)"))
                        : usuarioMostrado;

                try { archivoInbox(usuarioBase).createNewFile(); } catch (IOException ignored) {}
                ONLINE.put(usuarioBase, this);

                out.println("Bienvenido " + usuarioMostrado + " al sistema.");
                loopMenu();

            } catch (IOException e) {
                // cierre cliente o IO
>>>>>>> autenticación_(registro/login/invitado)
            } finally {
                if (usuarioBase != null) ONLINE.remove(usuarioBase);
            }
        }

<<<<<<< HEAD
>>>>>>> admin-consola
        // ====== Menu principal ======
=======
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

                    guardarUsuario(nuevoUsuario.trim(), nuevaPassword.trim());
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
                        guardarInvitado(invitado.trim());
                        return invitado.trim() + " (Invitado)";
                    }
                    return "Invitado (Invitado)";
                }
                default:
                    out.println("Opcion invalida.");
                    return null;
            }
        }

>>>>>>> autenticación_(registro/login/invitado)
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
<<<<<<< HEAD
                        // Si ya tenias el juego, aqui llama a juegoAdivina();
=======
                        // juegoAdivina();
>>>>>>> autenticación_(registro/login/invitado)
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

<<<<<<< HEAD
        // ====== Bandeja ======
=======
>>>>>>> autenticación_(registro/login/invitado)
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
<<<<<<< HEAD
<<<<<<< HEAD
            String r = in.readLine(); // usa el CAMPO 'in'
=======
            String r = in.readLine();
>>>>>>> admin-consola
=======
            String r = in.readLine();
>>>>>>> autenticación_(registro/login/invitado)
            if (r != null && r.trim().equalsIgnoreCase("si")) {
                vaciarInbox(usuarioBase);
                out.println("Bandeja vaciada.");
            } else {
                out.println("Bandeja conservada.");
            }
        }
<<<<<<< HEAD
<<<<<<< HEAD
=======

        // ====== (Opcional) Juego si ya lo agregaste antes ======
=======

>>>>>>> autenticación_(registro/login/invitado)
        @SuppressWarnings("unused")
        private void juegoAdivina() throws IOException {
            int numeroSecreto = rand.nextInt(10) + 1;
            out.println("Adivina un numero del 1 al 10. Tienes 3 intentos.");
<<<<<<< HEAD

=======
>>>>>>> autenticación_(registro/login/invitado)
            int intentos = 0, maxIntentos = 3;
            boolean ok = false;
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
        }
<<<<<<< HEAD
>>>>>>> admin-consola
=======
>>>>>>> autenticación_(registro/login/invitado)
    }
}
