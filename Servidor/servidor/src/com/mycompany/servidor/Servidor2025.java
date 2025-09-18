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
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Servidor2025 {

    // ====== Archivos de datos ======
    private static final File MENSAJES_DIR = new File("mensajes");
    private static final File USUARIOS_FILE = new File("usuarios.txt");
    private static final File INVITADOS_FILE = new File("invitados.txt");

    static {
        if (!MENSAJES_DIR.exists()) {
            MENSAJES_DIR.mkdirs();
        }
        try {
            if (!USUARIOS_FILE.exists()) {
                USUARIOS_FILE.createNewFile();
            }
        } catch (IOException ignored) {
        }
        try {
            if (!INVITADOS_FILE.exists()) {
                INVITADOS_FILE.createNewFile();
            }
        } catch (IOException ignored) {
        }
    }

    // ====== Modelo de mensaje (una línea por mensaje) ======
// Formato: id|timestamp|from|to|estado|texto
// estado: NORMAL, EDITADO, ELIMINADO
    private static class Mensaje {

        enum Estado {
            NORMAL, EDITADO, ELIMINADO
        }

        long id;
        LocalDateTime ts;
        String from;
        String to;
        Estado estado;
        String texto;

        private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        Mensaje(long id, LocalDateTime ts, String from, String to, Estado estado, String texto) {
            this.id = id;
            this.ts = ts;
            this.from = from;
            this.to = to;
            this.estado = estado;
            this.texto = texto;
        }

        static String esc(String s) {
            return s.replace("\\", "\\\\").replace("|", "\\|");
        }

        static String des(String s) {
            StringBuilder out = new StringBuilder();
            boolean esc = false;
            for (char c : s.toCharArray()) {
                if (esc) {
                    out.append(c);
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else {
                    out.append(c);
                }
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
                if (esc) {
                    cur.append(c);
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '|' && campos < 5) {
                    parts[campos++] = cur.toString();
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
            parts[campos] = cur.toString();

            long id = Long.parseLong(parts[0]);
            LocalDateTime ts = LocalDateTime.parse(parts[1], FMT);
            String from = des(parts[2]);
            String to = des(parts[3]);
            Estado estado = Estado.valueOf(parts[4]);
            String texto = des(parts[5]);
            return new Mensaje(id, ts, from, to, estado, texto);
        }

        String textoParaMostrar() {
            if (estado == Estado.ELIMINADO) {
                return "[mensaje eliminado]";
            }
            if (estado == Estado.EDITADO) {
                return texto + " (editado)";
            }
            return texto;
        }
    }

    // ====== Inbox ======
    private static File archivoInbox(String usuario) {
        return new File(MENSAJES_DIR, usuario + ".txt");
    }

    private static synchronized List<Mensaje> cargarMensajes(String usuario) {
        List<Mensaje> out = new ArrayList<>();
        File f = archivoInbox(usuario);
        if (!f.exists()) {
            return out;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) {
                    continue;
                }
                try {
                    out.add(Mensaje.parsear(l));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static synchronized void guardarMensajes(String usuario, List<Mensaje> mensajes) {
        File f = archivoInbox(usuario);
        try {
            f.getParentFile().mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(f, StandardCharsets.UTF_8, false))) {
                for (Mensaje m : mensajes) {
                    pw.println(m.serializar());
                }
            }
        } catch (IOException e) {
            System.out.println("Error guardando inbox de " + usuario + ": " + e.getMessage());
        }
    }

    private static synchronized List<Mensaje> cargarMensajes(String usuario) {
        List<Mensaje> out = new ArrayList<>();
        File f = archivoInbox(usuario);
        if (!f.exists()) {
            return out;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) {
                    continue;
                }
                try {
                    out.add(Mensaje.parsear(l));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static synchronized void vaciarInbox(String usuario) {
        File f = archivoInbox(usuario);
        try (PrintWriter pw = new PrintWriter(new FileWriter(f, StandardCharsets.UTF_8, false))) {
            // truncado
        } catch (IOException ignored) {
        }
    }

    // ====== Usuarios (registro / login) ======
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
        } catch (IOException ignored) {
        }
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
                if (p.length >= 1 && p[0].trim().equals(usuario)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
        }
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
                    if (!u.isEmpty()) {
                        res.add(u);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return res;
    }

    // ====== Tracking de conectados ======
    private static final ConcurrentMap<String, ClientHandler> ONLINE = new ConcurrentHashMap<>();

    // ====== Mensajería (guardar + notificar) ======
    private static final AtomicLong MSG_SEQ = new AtomicLong(System.currentTimeMillis());

    private static synchronized long enviarMensajeUsuario(String from, String to, String texto) {
        Mensaje m = new Mensaje(
                MSG_SEQ.getAndIncrement(),
                LocalDateTime.now(),
                from, to,
                Mensaje.Estado.NORMAL,
                texto
        );
        List<Mensaje> lista = cargarMensajes(to);
        lista.add(m);
        guardarMensajes(to, lista);

        ClientHandler ch = ONLINE.get(to);
        if (ch != null) {
            ch.safeSend("Tienes un nuevo mensaje de " + from + ". Usa '2' para abrir tu bandeja.");
        }
        return m.id;
    }

    private static synchronized boolean editarMensaje(String editor, String destinatario, long id, String nuevoTexto) {
        List<Mensaje> lista = cargarMensajes(destinatario);
        boolean cambiado = false;
        for (Mensaje m : lista) {
            if (m.id == id && m.from.equals(editor) && m.estado != Mensaje.Estado.ELIMINADO) {
                m.texto = nuevoTexto;
                m.estado = Mensaje.Estado.EDITADO;
                cambiado = true;
                break;
            }
        }
        if (cambiado) {
            guardarMensajes(destinatario, lista);
        }
        return cambiado;
    }

    private static synchronized boolean borrarMensaje(String editor, String destinatario, long id) {
        List<Mensaje> lista = cargarMensajes(destinatario);
        boolean cambiado = false;
        for (Mensaje m : lista) {
            if (m.id == id && m.from.equals(editor) && m.estado != Mensaje.Estado.ELIMINADO) {
                m.estado = Mensaje.Estado.ELIMINADO;
                m.texto = "";
                cambiado = true;
                break;
            }
        }
        if (cambiado) {
            guardarMensajes(destinatario, lista);
        }
        return cambiado;
    }

    private static List<String> usuariosConocidosPorBandeja() {
        List<String> res = new ArrayList<>();
        File[] files = MENSAJES_DIR.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            return res;
        }
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".txt")) {
                res.add(name.substring(0, name.length() - 4));
            }
        }
        return res;
    }

    // ====== Consola admin (System.in) ======
    private static void adminLoop() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Consola admin activa. Escribe /help para ver comandos.");
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                

                if (line.equalsIgnoreCase("/help")) {
                    System.out.println(
                            "Comandos:\n"
                            + "  /online                    -> lista usuarios conectados\n"
                            + "  /usuarios                  -> lista usuarios registrados\n"
                            + "  /enviar <usuario> <txt>    -> envia mensaje a un usuario\n"
                                    
                            + "  /broadcast <txt>           -> envia mensaje a todos los registrados\n"
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
                    if (sp <= 0) {
                        System.out.println("Uso: /enviar <usuario> <texto>");
                        continue;
                    }
                    String usuario = rest.substring(0, sp).trim();
                    String texto = rest.substring(sp + 1).trim();
                    if (!existeUsuarioRegistrado(usuario)) {
                        System.out.println("Usuario no registrado: " + usuario);
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
                    List<String> users = listarUsuariosRegistrados();
                    if (users.isEmpty()) {
                        users = usuariosConocidosPorBandeja(); // fallback
                        System.out.println("No hay usuarios registrados. Usando bandejas: " + users);
                    }
                    for (String u : users) {
                        enviarMensajeASingle(u, "[ADMIN] " + texto);
                    }
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

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void safeSend(String msg) {
            try {
                if (out != null) {
                    out.println(msg);
                }
            } catch (Exception ignored) {
            }
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                out = new PrintWriter(s.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                usuarioMostrado = autenticarUsuario();
                if (usuarioMostrado == null) {
                    out.println("Error en autenticacion. Cerrando conexion...");
                    return;
                }

                usuarioBase = usuarioMostrado.endsWith(" (Invitado)")
                        ? usuarioMostrado.substring(0, usuarioMostrado.indexOf(" (Invitado)"))
                        : usuarioMostrado;

                try {
                    archivoInbox(usuarioBase).createNewFile();
                } catch (IOException ignored) {
                }
                ONLINE.put(usuarioBase, this);

                out.println("Bienvenido " + usuarioMostrado + " al sistema.");
                loopMenu();

            } catch (IOException e) {
                // cierre cliente o IO
            } finally {
                if (usuarioBase != null) {
                    ONLINE.remove(usuarioBase);
                }
            }
        }

        // ====== Autenticación ======
        private String autenticarUsuario() throws IOException {
            out.println("Elige una opcion: (1) Registrarse, (2) Iniciar sesion, (3) Invitado");
            String opcion = in.readLine();
            if (opcion == null) {
                return null;
            }

            switch (opcion.trim()) {
                case "1": {
                    out.println("Introduce un nombre de usuario:");
                    String nuevoUsuario = in.readLine();
                    out.println("Introduce una contrasena:");
                    String nuevaPassword = in.readLine();

                    if (nuevoUsuario == null || nuevaPassword == null || nuevoUsuario.trim().isEmpty()) {
                        return null;
                    }

                    if (existeUsuarioRegistrado(nuevoUsuario.trim())) {
                        out.println("Usuario ya existe.");
                        return null;
                    }

                    try {
                        guardarUsuario(nuevoUsuario.trim(), nuevaPassword.trim());
                    } catch (IOException ignored) {
                    }
                    try {
                        archivoInbox(nuevoUsuario.trim()).createNewFile();
                    } catch (IOException ignored) {
                    }
                    out.println("Usuario registrado con exito!");
                    return nuevoUsuario.trim();
                }
                case "2": {
                    out.println("Introduce tu nombre de usuario:");
                    String usuario = in.readLine();
                    out.println("Introduce tu contrasena:");
                    String password = in.readLine();

                    if (usuario != null && password != null && validarUsuario(usuario.trim(), password.trim())) {
                        try {
                            archivoInbox(usuario.trim()).createNewFile();
                        } catch (IOException ignored) {
                        }
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
                        try {
                            guardarInvitado(invitado.trim());
                        } catch (IOException ignored) {
                        }
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

                out.println("Elige opcion:");

                String op = in.readLine();
                if (op == null) {
                    break;
                }

                switch (op.trim()) {
                    case "1":
                        juegoAdivina();
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
                        case "6": editarMensajeFlujo(); break;
case "7": borrarMensajeFlujo(); break;
case "8": verMisEnviadosFlujo(); break;

                    default:
                        out.println("Opcion invalida.");
                }
            }
        }

        // ====== Bandeja ======
        private void mostrarBandeja() throws IOException {
    List<Mensaje> msgs = cargarMensajes(usuarioBase);
    if (msgs.isEmpty()) {
        out.println("Tu bandeja esta vacia.");
        return;
    }
    out.println("Mensajes (" + msgs.size() + "):");
    for (Mensaje m : msgs) {
        String hora = m.ts.toLocalTime().withNano(0).toString();
        out.println(" [" + m.id + "] " + hora + " " + m.from + ": " + m.textoParaMostrar());
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


        // ====== Mensajería usuario → usuario ======
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
           long id = enviarMensajeUsuario(usuarioBase, dest, texto.trim());{
out.println("Mensaje enviado a " + dest + ". id=" + id);
 }
           
           private void editarMensajeFlujo() throws IOException {
    out.println("Destinatario del mensaje a editar:");
    String dest = in.readLine();
    if (dest == null || dest.trim().isEmpty()) { out.println("Cancelado."); return; }
    dest = dest.trim();

    out.println("ID del mensaje a editar:");
    String sId = in.readLine();
    if (sId == null || sId.trim().isEmpty()) { out.println("Cancelado."); return; }

    long id;
    try { id = Long.parseLong(sId.trim()); }
    catch (NumberFormatException e) { out.println("ID invalido."); return; }

    out.println("Nuevo texto:");
    String nuevo = in.readLine();
    if (nuevo == null) { out.println("Cancelado."); return; }

    boolean ok = editarMensaje(usuarioBase, dest, id, nuevo.trim());
    out.println(ok ? "OK Mensaje editado." : "ERROR No se pudo editar (revisa id/destinatario/permisos).");
}

private void borrarMensajeFlujo() throws IOException {
    out.println("Destinatario del mensaje a borrar:");
    String dest = in.readLine();
    if (dest == null || dest.trim().isEmpty()) { out.println("Cancelado."); return; }
    dest = dest.trim();

    out.println("ID del mensaje a borrar:");
    String sId = in.readLine();
    if (sId == null || sId.trim().isEmpty()) { out.println("Cancelado."); return; }

    long id;
    try { id = Long.parseLong(sId.trim()); }
    catch (NumberFormatException e) { out.println("ID invalido."); return; }

    boolean ok = borrarMensaje(usuarioBase, dest, id);
    out.println(ok ? "OK Mensaje borrado." : "ERROR No se pudo borrar (revisa id/destinatario/permisos).");
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
            out.println(" [" + m.id + "] " + m.estado + " -> " + m.textoParaMostrar());
        }
    }
    if (!alguno) out.println("No hay mensajes enviados a " + dest + ".");
}


        private void verConectados() {
            out.println("Conectados (" + ONLINE.size() + "): " + ONLINE.keySet());
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
                    if (entrada == null) {
                        return;
                    }
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
                if (!ok) {
                    out.println("Fallaste. El numero era " + numeroSecreto + ".");
                }

                out.println("¿Jugar otra vez? (si/no)");
                String r = in.readLine();
                jugarOtra = (r != null && r.trim().equalsIgnoreCase("si"));
            }
        }
    }
}
