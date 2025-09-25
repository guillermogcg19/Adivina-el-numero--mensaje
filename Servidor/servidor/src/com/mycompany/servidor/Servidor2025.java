package com.mycompany.servidor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Servidor2025 {

    // ====== Archivos de datos ======
    private static final File MENSAJES_DIR = new File("mensajes");
    private static final File BLOQUEOS_DIR = new File("bloqueos"); // lista de bloqueados por usuario
    private static final File USUARIOS_FILE = new File("usuarios.txt");
    private static final File INVITADOS_FILE = new File("invitados.txt");
    // === NUEVO: base de archivos compartidos por usuario
    private static final File FILES_DIR = new File("archivos");

    static {
        try { if (!MENSAJES_DIR.exists()) MENSAJES_DIR.mkdirs(); } catch (Exception ignored) {}
        try { if (!BLOQUEOS_DIR.exists()) BLOQUEOS_DIR.mkdirs(); } catch (Exception ignored) {}
        try { if (!USUARIOS_FILE.exists()) USUARIOS_FILE.createNewFile(); } catch (IOException ignored) {}
        try { if (!INVITADOS_FILE.exists()) INVITADOS_FILE.createNewFile(); } catch (IOException ignored) {}
        // === NUEVO
        try { if (!FILES_DIR.exists()) FILES_DIR.mkdirs(); } catch (Exception ignored) {}
    }

    // ====== Modelo de mensaje ======
    // Formato persistido: id|timestamp|from|to|estado|texto
    private static class Mensaje {

        enum Estado { NORMAL, EDITADO, ELIMINADO }

        long id;
        LocalDateTime ts;
        String from;
        String to;
        Estado estado;
        String texto;

        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        Mensaje(long id, LocalDateTime ts, String from, String to, Estado estado, String texto) {
            this.id = id; this.ts = ts; this.from = from; this.to = to; this.estado = estado; this.texto = texto;
        }

        static String esc(String s) { return s.replace("\\", "\\\\").replace("|", "\\|"); }

        static String des(String s) {
            StringBuilder out = new StringBuilder(); boolean esc = false;
            for (char c : s.toCharArray()) {
                if (esc) { out.append(c); esc = false; }
                else if (c == '\\') esc = true;
                else out.append(c);
            }
            return out.toString();
        }

        String serializar() {
            return id + "|" + FMT.format(ts) + "|" + esc(from) + "|" + esc(to) + "|" + estado + "|" + esc(texto);
        }

        static Mensaje parsear(String linea) {
            String[] parts = new String[6];
            int campos = 0;
            StringBuilder cur = new StringBuilder();
            boolean esc = false;
            for (char c : linea.toCharArray()) {
                if (esc) { cur.append(c); esc = false; }
                else if (c == '\\') { esc = true; }
                else if (c == '|' && campos < 5) { parts[campos++] = cur.toString(); cur.setLength(0); }
                else { cur.append(c); }
            }
            parts[campos] = cur.toString();

            return new Mensaje(
                    Long.parseLong(parts[0]),
                    LocalDateTime.parse(parts[1], FMT),
                    des(parts[2]),
                    des(parts[3]),
                    Estado.valueOf(parts[4]),
                    des(parts[5])
            );
        }

        String textoParaMostrar() {
            if (estado == Estado.ELIMINADO) return "[mensaje eliminado]";
            if (estado == Estado.EDITADO)   return texto + " (editado)";
            return texto;
        }
    }

    // ====== Helpers de ID corto ======
    private static String shortId(long id) {
        // Base36, en mayúsculas, muy corto (p.ej., 123456789 -> "21I3V9")
        return Long.toString(id, 36).toUpperCase();
    }

    private static Long parseFlexibleId(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        s = s.trim();
        try { // intenta como número completo
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            try { // intenta como base36
                return Long.parseLong(s.toLowerCase(), 36);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    // ====== Inbox ======
    private static File archivoInbox(String usuario) { return new File(MENSAJES_DIR, usuario + ".txt"); }

    private static synchronized List<Mensaje> cargarMensajes(String usuario) {
        List<Mensaje> out = new ArrayList<>();
        File f = archivoInbox(usuario);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) continue;
                try { out.add(Mensaje.parsear(l)); } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
        return out;
    }

    private static synchronized void guardarMensajes(String usuario, List<Mensaje> mensajes) {
        File f = archivoInbox(usuario);
        try {
            f.getParentFile().mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, StandardCharsets.UTF_8, false))) {
                for (Mensaje m : mensajes) pw.println(m.serializar());
            }
        } catch (IOException e) {
            System.out.println("Error guardando inbox de " + usuario + ": " + e.getMessage());
        }
    }

    private static synchronized void vaciarInbox(String usuario) {
        File f = archivoInbox(usuario);
        try (PrintWriter pw = new PrintWriter(new FileWriter(f, StandardCharsets.UTF_8, false))) {
            // truncado
        } catch (IOException ignored) {}
    }

    private static synchronized void archivarInbox(String usuario) {
        File f = archivoInbox(usuario);
        if (f.exists()) {
            File bak = new File(MENSAJES_DIR, usuario + ".bak");
            if (bak.exists()) bak = new File(MENSAJES_DIR, usuario + "-" + System.currentTimeMillis() + ".bak");
            if (!f.renameTo(bak)) vaciarInbox(usuario); // fallback
        }
    }

    // ====== Usuarios ======
    private static synchronized void guardarUsuario(String usuario, String password) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USUARIOS_FILE, true))) {
            pw.println(usuario + "," + password + ",ACTIVO");
        }
        // === NUEVO: preparar carpeta de archivos para ese usuario
        new File(FILES_DIR, usuario).mkdirs();
    }

    private static boolean validarUsuario(String usuario, String password) {
        try (BufferedReader br = new BufferedReader(new FileReader(USUARIOS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 3);
                String estado = (p.length >= 3) ? p[2].trim() : "ACTIVO";
                if (p.length >= 2 && p[0].equals(usuario) && p[1].equals(password) && "ACTIVO".equalsIgnoreCase(estado)) {
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

    private static boolean esUsuarioActivo(String usuario) {
        try (BufferedReader br = new BufferedReader(new FileReader(USUARIOS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 3);
                if (p.length >= 1 && p[0].trim().equals(usuario)) {
                    String estado = (p.length >= 3) ? p[2].trim() : "ACTIVO";
                    return "ACTIVO".equalsIgnoreCase(estado);
                }
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

    private static synchronized boolean marcarEstadoUsuario(String usuario, String nuevoEstado) {
        List<String> todas = new ArrayList<>();
        boolean cambiado = false;
        try (BufferedReader br = new BufferedReader(new FileReader(USUARIOS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", 3);
                if (p.length >= 1 && p[0].trim().equals(usuario)) {
                    String nombre = p[0].trim();
                    String pass = (p.length >= 2) ? p[1].trim() : "";
                    todas.add(nombre + "," + pass + "," + nuevoEstado);
                    cambiado = true;
                } else {
                    if (p.length == 2) {
                        todas.add(p[0].trim() + "," + p[1].trim() + ",ACTIVO");
                    } else {
                        todas.add(line);
                    }
                }
            }
        } catch (IOException ignored) {}

        if (cambiado) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(USUARIOS_FILE, false))) {
                for (String l : todas) pw.println(l);
            } catch (IOException ignored) {}
        }
        return cambiado;
    }

    private static synchronized boolean darDeBajaPropia(String usuario, String password) {
        if (validarUsuario(usuario, password) && esUsuarioActivo(usuario)) {
            boolean ok = marcarEstadoUsuario(usuario, "BAJA");
            if (ok) archivarInbox(usuario);
            return ok;
        }
        return false;
    }

    // ====== Bloqueos ======
    private static File archivoBloqueos(String usuario) { return new File(BLOQUEOS_DIR, usuario + ".txt"); }

    private static synchronized List<String> cargarBloqueados(String usuario) {
        List<String> res = new ArrayList<>();
        File f = archivoBloqueos(usuario);
        if (!f.exists()) return res;
        try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (!l.isEmpty()) res.add(l);
            }
        } catch (IOException ignored) {}
        return res;
    }

    // receptor NO quiere recibir de emisor
    private static synchronized boolean bloquear(String receptor, String emisor) {
        List<String> lista = cargarBloqueados(receptor);
        if (!lista.contains(emisor)) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(archivoBloqueos(receptor), StandardCharsets.UTF_8, false))) {
                lista.add(emisor);
                for (String u : lista) pw.println(u);
            } catch (IOException ignored) {}
            return true;
        }
        return false;
    }

    private static synchronized boolean desbloquear(String receptor, String emisor) {
        List<String> lista = cargarBloqueados(receptor);
        if (lista.remove(emisor)) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(archivoBloqueos(receptor), StandardCharsets.UTF_8, false))) {
                for (String u : lista) pw.println(u);
            } catch (IOException ignored) {}
            return true;
        }
        return false;
    }

    private static boolean estaBloqueado(String receptor, String emisor) {
        List<String> lista = cargarBloqueados(receptor);
        return lista.contains(emisor);
    }

    // Usuarios a los que "from" puede escribir (activos, no él mismo y que NO han bloqueado a "from")
    private static List<String> usuariosDisponiblesPara(String from) {
        List<String> base = listarUsuariosRegistrados();
        List<String> out = new ArrayList<>();
        for (String u : base) {
            if (u.equals(from)) continue;
            if (!esUsuarioActivo(u)) continue;
            if (estaBloqueado(u, from)) continue; // el destinatario bloqueó a "from"
            out.add(u);
        }
        return out;
    }

    // === NUEVO: destinatarios a los que YA les envié mensajes ===
    private static List<String> destinatariosConMensajesDe(String from) {
        Set<String> dests = new LinkedHashSet<>();
        for (String u : listarUsuariosRegistrados()) {
            if (u.equals(from)) continue;
            for (Mensaje m : cargarMensajes(u)) {
                if (from.equals(m.from)) {
                    dests.add(u);
                    break;
                }
            }
        }
        return new ArrayList<>(dests);
    }

    // === NUEVO: mensajes que yo envié a un destinatario (no eliminados si se desea) ===
    private static List<Mensaje> mensajesEnviadosA(String from, String dest) {
        List<Mensaje> res = new ArrayList<>();
        for (Mensaje m : cargarMensajes(dest)) {
            if (from.equals(m.from)) res.add(m);
        }
        return res;
    }

    // === NUEVO: helpers de archivos .txt de usuarios (servidor) ===
    private static File userFilesDir(String user) { return new File(FILES_DIR, user); }
    private static List<String> listTextFiles(String user) {
        List<String> out = new ArrayList<>();
        File dir = userFilesDir(user);
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".txt"));
        if (files != null) for (File f : files) out.add(f.getName());
        return out;
    }
    private static void enviarArchivoATramo(PrintWriter out, File f) {
        out.println("FILE_BEGIN " + f.getName());
        try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) out.println(l);
        } catch (IOException e) {
            out.println("[ERROR] Leyendo archivo: " + e.getMessage());
        }
        out.println("FILE_END");
    }

    // ====== Tracking de conectados ======
    private static final ConcurrentMap<String, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    // ====== Mensajería ======
    private static final AtomicLong MSG_SEQ = new AtomicLong(System.currentTimeMillis());

    private static synchronized long enviarMensajeUsuario(String from, String to, String texto) {
        if (estaBloqueado(to, from)) return -1L; // rechazado: to bloqueó a from
        Mensaje m = new Mensaje(MSG_SEQ.getAndIncrement(), LocalDateTime.now(), from, to, Mensaje.Estado.NORMAL, texto);
        List<Mensaje> lista = cargarMensajes(to);
        lista.add(m);
        guardarMensajes(to, lista);

        ClientHandler ch = ONLINE.get(to);
        if (ch != null) ch.safeSend("Tienes un nuevo mensaje de " + from + ". Usa '2' para abrir tu bandeja.");
        return m.id;
    }

    private static synchronized boolean editarMensaje(String editor, String destinatario, long id, String nuevoTexto) {
        List<Mensaje> lista = cargarMensajes(destinatario);
        for (Mensaje m : lista) {
            if (m.id == id && m.from.equals(editor) && m.estado != Mensaje.Estado.ELIMINADO) {
                m.texto = nuevoTexto;
                m.estado = Mensaje.Estado.EDITADO;
                guardarMensajes(destinatario, lista);
                return true;
            }
        }
        return false;
    }

    private static synchronized boolean borrarMensaje(String editor, String destinatario, long id) {
        List<Mensaje> lista = cargarMensajes(destinatario);
        for (Mensaje m : lista) {
            if (m.id == id && m.from.equals(editor) && m.estado != Mensaje.Estado.ELIMINADO) {
                m.estado = Mensaje.Estado.ELIMINADO;
                m.texto = "";
                guardarMensajes(destinatario, lista);
                return true;
            }
        }
        return false;
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
                            "  /online            -> lista usuarios conectados\n" +
                            "  /usuarios          -> lista usuarios registrados\n" +
                            "  /enviar <u> <txt>  -> envia mensaje a un usuario\n" +
                            "  /broadcast <txt>   -> envia mensaje a todos\n" +
                            "  /bannear <usuario> -> da de baja cualquier cuenta\n" +
                            "  /reactivar <user>  -> reactiva cuenta dada de baja\n"
                    );
                    continue;
                }

                if (line.equalsIgnoreCase("/online")) {
                    System.out.println("Conectados (" + ONLINE.size() + "): " + ONLINE.keySet());
                    continue;
                }

                if (line.equalsIgnoreCase("/usuarios")) {
                    List<String> u = listarUsuariosRegistrados();
                    System.out.println("Usuarios registrados (" + u.size() + "): " + u);
                    continue;
                }

                if (line.startsWith("/enviar ")) {
                    String rest = line.substring(8).trim();
                    int sp = rest.indexOf(' ');
                    if (sp <= 0) { System.out.println("Uso: /enviar <usuario> <texto>"); continue; }
                    String usuario = rest.substring(0, sp).trim();
                    String texto = rest.substring(sp + 1).trim();
                    if (!existeUsuarioRegistrado(usuario)) { System.out.println("Usuario no registrado: " + usuario); continue; }
                    long id = enviarMensajeUsuario("[ADMIN]", usuario, texto);
                    if (id == -1L) System.out.println("No enviado: el usuario ha bloqueado a [ADMIN].");
                    else System.out.println("Mensaje enviado a " + usuario + " (id=" + id + ")");
                    continue;
                }

                if (line.startsWith("/broadcast ")) {
                    String texto = line.substring(11).trim();
                    if (texto.isEmpty()) { System.out.println("Uso: /broadcast <texto>"); continue; }
                    List<String> users = listarUsuariosRegistrados();
                    int ok = 0;
                    for (String u : users) if (enviarMensajeUsuario("[ADMIN]", u, texto) != -1L) ok++;
                    System.out.println("Broadcast enviado a " + ok + " usuarios (no bloqueados).");
                    continue;
                }

                if (line.startsWith("/bannear ")) {
                    String usuario = line.substring(9).trim();
                    if (usuario.isEmpty()) { System.out.println("Uso: /bannear <usuario>"); continue; }
                    if (!existeUsuarioRegistrado(usuario)) { System.out.println("No existe: " + usuario); continue; }
                    if (!esUsuarioActivo(usuario)) { System.out.println("Ya estaba en BAJA: " + usuario); continue; }
                    boolean ok = marcarEstadoUsuario(usuario, "BAJA");
                    if (ok) {
                        archivarInbox(usuario);
                        ClientHandler ch = ONLINE.remove(usuario);
                        if (ch != null) ch.safeSend("Tu cuenta ha sido dada de baja por un administrador.");
                        System.out.println("Usuario " + usuario + " dado de baja.");
                    } else System.out.println("No se pudo dar de baja.");
                    continue;
                }

                if (line.startsWith("/reactivar ")) {
                    String usuario = line.substring(11).trim();
                    if (usuario.isEmpty()) { System.out.println("Uso: /reactivar <usuario>"); continue; }
                    if (!existeUsuarioRegistrado(usuario)) { System.out.println("No existe: " + usuario); continue; }
                    boolean ok = marcarEstadoUsuario(usuario, "ACTIVO");
                    System.out.println(ok ? "Usuario reactivado: " + usuario : "No se pudo reactivar.");
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
        Thread admin = new Thread(Servidor2025::adminLoop, "ADMIN-CONSOLE");
        admin.setDaemon(true);
        admin.start();

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
        private String usuarioMostrado; // lo que ve el usuario (con "(Invitado)" si aplica)
        private String usuarioBase;     // nombre sin sufijo
        private final Random rand = new Random();

        ClientHandler(Socket socket) { this.socket = socket; }

        void safeSend(String msg) { try { if (out != null) out.println(msg); } catch (Exception ignored) {} }

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

                usuarioBase = usuarioMostrado.endsWith(" (Invitado)")
                        ? usuarioMostrado.substring(0, usuarioMostrado.indexOf(" (Invitado)"))
                        : usuarioMostrado;

                try { archivoInbox(usuarioBase).createNewFile(); } catch (IOException ignored) {}
                ONLINE.put(usuarioBase, this);

                out.println("Bienvenido " + usuarioMostrado + " al sistema.");
                loopMenu();

            } catch (IOException e) {
                // cierre cliente
            } finally {
                if (usuarioBase != null) ONLINE.remove(usuarioBase);
            }
        }

        // ====== Autenticación ======
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
                    if (nuevoUsuario == null || nuevaPassword == null || nuevoUsuario.trim().isEmpty()) return null;
                    if (existeUsuarioRegistrado(nuevoUsuario.trim())) { out.println("Usuario ya existe."); return null; }
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
                        out.println("Usuario/clave incorrectos o cuenta en BAJA.");
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

        // ====== Menu ======
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
                out.println("6) Editar mensaje enviado");
                out.println("7) Borrar mensaje enviado");
                out.println("8) Ver mis enviados hacia un usuario");
                out.println("9) Bloquear usuario (no recibir de...)");
                out.println("10) Desbloquear usuario");
                out.println("11) Dar de baja mi cuenta");
                out.println("12) Ver mi lista de bloqueados");
                // === NUEVO:
                out.println("13) Ver archivos .txt de un usuario");
                out.println("14) Descargar .txt de un usuario");
                out.println("Elige opcion:");

                String op = in.readLine();
                if (op == null) break;

                switch (op.trim()) {
                    case "1": juegoAdivina(); break;
                    case "2": mostrarBandeja(); break;
                    case "3": out.println("Gracias. Adios!"); seguir = false; break;
                    case "4": enviarMensajeAUsuario(); break;
                    case "5": verConectados(); break;
                    case "6": editarMensajeFlujo(); break;
                    case "7": borrarMensajeFlujo(); break;
                    case "8": verMisEnviadosFlujo(); break;
                    case "9": bloquearUsuarioFlujo(); break;
                    case "10": desbloquearUsuarioFlujo(); break;
                    case "11": darDeBajaPropiaFlujo(); break;
                    case "12": verBloqueadosFlujo(); break;
                    // === NUEVO:
                    case "13": verArchivosUsuarioFlujo(); break;
                    case "14": descargarArchivoUsuarioFlujo(); break;
                    default: out.println("Opcion invalida.");
                }
            }
        }

        // ====== Bandeja ======
        private void mostrarBandeja() throws IOException {
            List<Mensaje> msgs = cargarMensajes(usuarioBase);
            if (msgs.isEmpty()) { out.println("Tu bandeja esta vacia."); return; }
            out.println("Mensajes (" + msgs.size() + "):");
            for (Mensaje m : msgs) {
                String hora = m.ts.toLocalTime().withNano(0).toString();
                out.println(" [" + shortId(m.id) + "] " + hora + " " + m.from + ": " + m.textoParaMostrar());
            }
            out.println("Quieres vaciar la bandeja? (si/no)");
            String r = in.readLine();
            if (r != null && r.trim().equalsIgnoreCase("si")) {
                vaciarInbox(usuarioBase);
                out.println("Bandeja vaciada.");
            } else {
                out.println("Bandeja conservada.");
            }
        }

        // ====== Enviar / Editar / Borrar / Ver enviados ======
        private void enviarMensajeAUsuario() throws IOException {
            List<String> disponibles = usuariosDisponiblesPara(usuarioBase);
            if (disponibles.isEmpty()) { out.println("No hay usuarios disponibles para enviar."); return; }
            out.println("Usuarios disponibles:");
            for (String u : disponibles) out.println(" - " + u);

            out.println("Usuario destinatario:");
            String dest = in.readLine();
            if (dest == null || dest.trim().isEmpty()) { out.println("Cancelado: destinatario vacio."); return; }
            dest = dest.trim();

            if (!disponibles.contains(dest)) {
                out.println("No puedes enviar a '" + dest + "' (no activo o te bloqueó).");
                return;
            }

            out.println("Escribe el mensaje:");
            String texto = in.readLine();
            if (texto == null || texto.trim().isEmpty()) { out.println("Cancelado: mensaje vacio."); return; }

            long id = enviarMensajeUsuario(usuarioBase, dest, texto.trim());
            if (id == -1L) out.println("No se envió: '" + dest + "' te tiene bloqueado.");
            else out.println("Mensaje enviado a " + dest + ". id=" + id + " (corto " + shortId(id) + ")");
        }

        private void verConectados() {
            out.println("Conectados (" + ONLINE.size() + "): " + ONLINE.keySet());
        }

        // === NUEVO flujo: elegir destinatario de tu lista + usar ID corto ===
        private void editarMensajeFlujo() throws IOException {
            List<String> dests = destinatariosConMensajesDe(usuarioBase);
            if (dests.isEmpty()) { out.println("No has enviado mensajes a nadie aún."); return; }
            out.println("Has enviado mensajes a:");
            for (String d : dests) out.println(" - " + d);

            out.println("Editar mensaje hacia (usuario):");
            String dest = in.readLine();
            if (dest == null || dest.trim().isEmpty() || !dests.contains(dest.trim())) { out.println("Cancelado."); return; }
            dest = dest.trim();

            List<Mensaje> lista = mensajesEnviadosA(usuarioBase, dest);
            if (lista.isEmpty()) { out.println("No hay mensajes hacia " + dest + "."); return; }

            out.println("Tus mensajes a " + dest + ":");
            for (Mensaje m : lista) {
                if (m.estado != Mensaje.Estado.ELIMINADO) {
                    String hora = m.ts.toLocalTime().withNano(0).toString();
                    out.println(" [" + shortId(m.id) + "] " + hora + " -> " + m.textoParaMostrar());
                }
            }

            out.println("ID del mensaje a editar (corto o completo):");
            String sId = in.readLine();
            Long id = parseFlexibleId(sId);
            if (id == null) { out.println("ID inválido."); return; }

            out.println("Nuevo texto:");
            String nuevo = in.readLine();
            if (nuevo == null) { out.println("Cancelado."); return; }

            boolean ok = editarMensaje(usuarioBase, dest, id, nuevo.trim());
            out.println(ok ? "OK Mensaje editado." : "ERROR No se pudo editar (revisa id).");
        }

        private void borrarMensajeFlujo() throws IOException {
            List<String> dests = destinatariosConMensajesDe(usuarioBase);
            if (dests.isEmpty()) { out.println("No has enviado mensajes a nadie aún."); return; }
            out.println("Has enviado mensajes a:");
            for (String d : dests) out.println(" - " + d);

            out.println("Borrar mensaje hacia (usuario):");
            String dest = in.readLine();
            if (dest == null || dest.trim().isEmpty() || !dests.contains(dest.trim())) { out.println("Cancelado."); return; }
            dest = dest.trim();

            List<Mensaje> lista = mensajesEnviadosA(usuarioBase, dest);
            if (lista.isEmpty()) { out.println("No hay mensajes hacia " + dest + "."); return; }

            out.println("Tus mensajes a " + dest + ":");
            for (Mensaje m : lista) {
                if (m.estado != Mensaje.Estado.ELIMINADO) {
                    String hora = m.ts.toLocalTime().withNano(0).toString();
                    out.println(" [" + shortId(m.id) + "] " + hora + " -> " + m.textoParaMostrar());
                }
            }

            out.println("ID del mensaje a borrar (corto o completo):");
            String sId = in.readLine();
            Long id = parseFlexibleId(sId);
            if (id == null) { out.println("ID inválido."); return; }

            boolean ok = borrarMensaje(usuarioBase, dest, id);
            out.println(ok ? "OK Mensaje borrado." : "ERROR No se pudo borrar (revisa id).");
        }

        private void verMisEnviadosFlujo() throws IOException {
            out.println("Ver mis enviados hacia (usuario):");
            String dest = in.readLine();
            if (dest == null || dest.trim().isEmpty()) { out.println("Cancelado."); return; }
            dest = dest.trim();

            List<Mensaje> lista = cargarMensajes(dest);
            boolean alguno = false;
            for (Mensaje m : lista) {
                if (m.from.equals(usuarioBase)) {
                    alguno = true;
                    out.println(" [" + shortId(m.id) + "] " + m.estado + " -> " + m.textoParaMostrar());
                }
            }
            if (!alguno) out.println("No hay mensajes enviados a " + dest + ".");
        }

        // ====== Bloqueo / Desbloqueo / Ver bloqueados ======
        private void bloquearUsuarioFlujo() throws IOException {
            out.println("Bloquear (no recibir de):");
            String emisor = in.readLine();
            if (emisor == null || emisor.trim().isEmpty()) { out.println("Cancelado."); return; }
            emisor = emisor.trim();

            if (!existeUsuarioRegistrado(emisor)) { out.println("No existe el usuario: " + emisor); return; }
            if (emisor.equals(usuarioBase)) { out.println("No puedes bloquearte a ti mismo."); return; }

            boolean ok = bloquear(usuarioBase, emisor);
            out.println(ok ? ("Bloqueado: ya no recibirás mensajes de " + emisor) : ("Ya estaba bloqueado: " + emisor));
        }

        private void desbloquearUsuarioFlujo() throws IOException {
            out.println("Desbloquear (volver a recibir de):");
            String emisor = in.readLine();
            if (emisor == null || emisor.trim().isEmpty()) { out.println("Cancelado."); return; }
            emisor = emisor.trim();

            boolean ok = desbloquear(usuarioBase, emisor);
            out.println(ok ? ("Desbloqueado: " + emisor) : ("No estaba bloqueado: " + emisor));
        }

        private void verBloqueadosFlujo() {
            List<String> lista = cargarBloqueados(usuarioBase);
            if (lista.isEmpty()) out.println("No tienes usuarios bloqueados.");
            else {
                out.println("Bloqueados:");
                for (String u : lista) out.println(" - " + u);
            }
        }

        // ====== Auto-baja ======
        private void darDeBajaPropiaFlujo() throws IOException {
            out.println("¿Confirma dar de baja su cuenta? (si/no)");
            String conf = in.readLine();
            if (conf == null || !conf.trim().equalsIgnoreCase("si")) { out.println("Cancelado."); return; }

            boolean esInvitado = usuarioMostrado.endsWith(" (Invitado)");
            if (esInvitado) {
                archivarInbox(usuarioBase);
                out.println("Tu sesión de invitado ha sido dada de baja. Cerrando sesión...");
                ONLINE.remove(usuarioBase);
                return;
            }

            out.println("Escribe tu contraseña para confirmar:");
            String pass = in.readLine();
            if (pass == null) { out.println("Cancelado."); return; }

            boolean ok = darDeBajaPropia(usuarioBase, pass.trim());
            if (ok) {
                out.println("Tu cuenta ha sido dada de baja. Cerrando sesión...");
                ONLINE.remove(usuarioBase);
            } else {
                out.println("No se pudo dar de baja (contraseña incorrecta o ya en BAJA).");
            }
        }

        // ====== NUEVO: Ver/descargar .txt de usuario ======
        private void verArchivosUsuarioFlujo() throws IOException {
            out.println("Usuario a consultar sus .txt (en el servidor):");
            String u = in.readLine();
            if (u == null || u.trim().isEmpty()) { out.println("Cancelado."); return; }
            u = u.trim();
            if (!existeUsuarioRegistrado(u)) { out.println("No existe el usuario."); return; }
            List<String> files = listTextFiles(u);
            if (files.isEmpty()) out.println("No hay .txt en 'archivos/" + u + "/'");
            else {
                out.println("Archivos .txt de " + u + ":");
                for (String f : files) out.println(" - " + f);
            }
        }

        private void descargarArchivoUsuarioFlujo() throws IOException {
            out.println("Descargar .txt de (usuario):");
            String from = in.readLine();
            if (from == null || from.trim().isEmpty()) { out.println("Cancelado."); return; }
            from = from.trim();
            if (!existeUsuarioRegistrado(from)) { out.println("No existe el usuario."); return; }

            List<String> files = listTextFiles(from);
            if (files.isEmpty()) { out.println("Ese usuario no tiene .txt en el servidor."); return; }
            out.println("Disponibles en archivos/" + from + "/:");
            for (String f : files) out.println(" - " + f);

            out.println("Nombre exacto del archivo (.txt):");
            String fname = in.readLine();
            if (fname == null || fname.trim().isEmpty()) { out.println("Cancelado."); return; }
            fname = fname.trim();

            File src = new File(userFilesDir(from), fname);
            if (!src.exists() || !src.isFile()) { out.println("No existe ese archivo."); return; }

            enviarArchivoATramo(out, src); // el cliente debe guardar el stream entre FILE_BEGIN/FILE_END
        }

        // ====== Juego ======
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
                    if (entrada.isEmpty()) { out.println("Escribe un numero valido. No pierdes intento."); continue; }
                    try {
                        int intento = Integer.parseInt(entrada);
                        intentos++;
                        if (intento == numeroSecreto) { out.println("Adivinaste en " + intentos + " intento(s)!"); ok = true; }
                        else if (intento < numeroSecreto) out.println("Mas alto. Quedan " + (maxIntentos - intentos) + " intentos.");
                        else out.println("Mas bajo. Quedan " + (maxIntentos - intentos) + " intentos.");
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
