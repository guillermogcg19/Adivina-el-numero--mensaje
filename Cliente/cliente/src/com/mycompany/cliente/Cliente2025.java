package com.mycompany.cliente;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Cliente2025 {
    public static void main(String[] args) {
        // Host/puerto configurables por argumentos
        String host = (args.length >= 1) ? args[0] : "127.0.0.1";
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : 9090;

        try (Socket socket = new Socket(host, port);
             BufferedReader lectorServidor = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter escritorServidor = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader lectorUsuario = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Conectado a " + host + ":" + port + " (Escribe 'salir' para terminar)");

            Thread leerServidor = new Thread(() -> {
                // Contexto por usuario (se inicializa cuando llega SESSION_USER)
                final String[] currentUser = { null };
                final File[] baseDir = { null };
                final File[] downloadsDir = { null };
                final File[] convDir = { null };
                final BufferedWriter[] convLog = { null };

                // Elegir Escritorio o Descargas del usuario sin hardcodear rutas absolutas
                final java.util.function.Supplier<File> resolveBaseRoot = () -> {
                    String userHome = System.getProperty("user.home");
                    File home = new File(userHome);

                    File desktop1 = new File(home, "Desktop");
                    File desktop2 = new File(home, "Escritorio");
                    File downloads1 = new File(home, "Downloads");
                    File downloads2 = new File(home, "Descargas");

                    if (desktop1.exists()) return desktop1;
                    if (desktop2.exists()) return desktop2;
                    if (downloads1.exists()) return downloads1;
                    if (downloads2.exists()) return downloads2;

                    return home; // fallback
                };

                // Inicializar carpetas y log local por usuario
                final java.util.function.Consumer<String> initUserDirs = (userName) -> {
                    File root = resolveBaseRoot.get();
                    File userBase = new File(root, "Mensajeria_" + userName);
                    File dls = new File(userBase, "descargas");
                    File conv = new File(userBase, "conversaciones");

                    if (!userBase.exists()) userBase.mkdirs();
                    if (!dls.exists()) dls.mkdirs();
                    if (!conv.exists()) conv.mkdirs();

                    baseDir[0] = userBase;
                    downloadsDir[0] = dls;
                    convDir[0] = conv;

                    String hoy = java.time.LocalDate.now().toString();
                    File logFile = new File(conv, hoy + ".txt");
                    try {
                        convLog[0] = new BufferedWriter(
                                new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8));
                        convLog[0].write("=== Inicio de sesión: " + java.time.LocalDateTime.now() + " ===");
                        convLog[0].newLine();
                        convLog[0].flush();
                    } catch (IOException e) {
                        System.out.println("[Log] No se pudo abrir el log local: " + e.getMessage());
                    }

                    System.out.println("[Sesión] Carpeta base: " + userBase.getAbsolutePath());
                };

                // Agregar línea al log local
                final java.util.function.Consumer<String> logLine = (s) -> {
                    try {
                        if (convLog[0] != null) {
                            convLog[0].write("[" + java.time.LocalTime.now().withNano(0) + "] " + s);
                            convLog[0].newLine();
                            convLog[0].flush();
                        }
                    } catch (IOException ignored) {}
                };

                String linea;
                boolean recibiendoArchivo = false;
                BufferedWriter archivoOut = null;
                String archivoNombre = null;

                try {
                    while ((linea = lectorServidor.readLine()) != null) {

                        // ===== Señal de sesión para crear carpetas por usuario =====
                        if (linea.startsWith("SESSION_USER|")) {
                            String user = linea.substring("SESSION_USER|".length()).trim();
                            currentUser[0] = user;
                            initUserDirs.accept(user);
                            System.out.println("[Sesión] Usuario: " + user);
                            continue;
                        }

                        // ===== Descarga de archivos (receptor) =====
                        if (linea.startsWith("FILE_BEGIN ")) {
                            archivoNombre = linea.substring("FILE_BEGIN ".length()).trim();
                            archivoNombre = archivoNombre.replace("\\", "/");
                            int slash = archivoNombre.lastIndexOf('/');
                            if (slash >= 0) archivoNombre = archivoNombre.substring(slash + 1);

                            try {
                                File destino = new File(
                                        (downloadsDir[0] != null ? downloadsDir[0] : new File(".")),
                                        archivoNombre
                                );
                                archivoOut = new BufferedWriter(
                                        new OutputStreamWriter(
                                                new FileOutputStream(destino),
                                                StandardCharsets.UTF_8));
                                recibiendoArchivo = true;
                                System.out.println("[Descarga] Recibiendo " + archivoNombre + " ...");
                            } catch (IOException e) {
                                System.out.println("[Descarga] No se pudo crear archivo local: " + e.getMessage());
                                recibiendoArchivo = false;
                                archivoOut = null;
                            }
                            continue;
                        }
                        if ("FILE_END".equals(linea)) {
                            if (archivoOut != null) {
                                try { archivoOut.flush(); archivoOut.close(); } catch (IOException ignored) {}
                                System.out.println("[Descarga] Archivo guardado en " +
                                        (downloadsDir[0] != null ? downloadsDir[0].getAbsolutePath() : "./") +
                                        File.separator + archivoNombre);
                            }
                            recibiendoArchivo = false;
                            archivoOut = null;
                            archivoNombre = null;
                            continue;
                        }
                        if (recibiendoArchivo && archivoOut != null) {
                            try {
                                archivoOut.write(linea);
                                archivoOut.newLine();
                            } catch (IOException e) {
                                System.out.println("[Descarga] Error escribiendo archivo: " + e.getMessage());
                            }
                            continue;
                        }

                        // ===== Subida solicitada (push manual existente) =====
                        if (linea.startsWith("UPLOAD_REQUEST|")) {
                            String resto = linea.substring("UPLOAD_REQUEST|".length());
                            String[] parts = resto.split("\\|", 2);
                            if (parts.length == 2) {
                                String dest = parts[0].trim();
                                String ruta = parts[1].trim();
                                File f = new File(ruta);
                                if (!f.exists() || !f.isFile()) {
                                    System.out.println("[Subida] Archivo no existe: " + ruta);
                                    continue;
                                }
                                String baseName = f.getName();
                                escritorServidor.println("FILE_UPLOAD_BEGIN|" + dest + "|" + baseName);
                                try (BufferedReader fr = new BufferedReader(
                                        new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                                    String l;
                                    while ((l = fr.readLine()) != null) {
                                        escritorServidor.println(l);
                                    }
                                } catch (IOException e) {
                                    System.out.println("[Subida] Error leyendo archivo: " + e.getMessage());
                                }
                                escritorServidor.println("FILE_UPLOAD_END");
                                System.out.println("[Subida] Enviado: " + baseName + " -> " + dest);
                            } else {
                                System.out.println("[Subida] Solicitud inválida: " + linea);
                            }
                            continue;
                        }

                        // ===== Listado de .txt solicitado por otro usuario =====
                        if (linea.startsWith("LIST_LOCAL_TXT|")) {
                            String solicitante = linea.substring("LIST_LOCAL_TXT|".length()).trim();

                            // Lugares a revisar (cwd, carpeta de usuario, y descargas dentro de esa carpeta)
                            java.util.List<File> roots = new java.util.ArrayList<>();
                            File cwd = new File(".").getAbsoluteFile();
                            roots.add(cwd);
                            if (baseDir[0] != null) {
                                roots.add(baseDir[0]);
                                File dls = new File(baseDir[0], "descargas");
                                if (dls.exists()) roots.add(dls);
                            }

                            java.util.LinkedHashSet<String> nombres = new java.util.LinkedHashSet<>();
                            for (File root : roots) {
                                File[] files = root.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
                                if (files != null) {
                                    for (File f : files) nombres.add(f.getName());
                                }
                            }

                            int n = nombres.size();
                            escritorServidor.println("LIST_RESP_BEGIN|" + solicitante + "|" + n);
                            for (String name : nombres) escritorServidor.println(name);
                            escritorServidor.println("LIST_RESP_END");

                            System.out.println("[Lista] Buscado en:");
                            for (File r : roots) System.out.println("  - " + r.getAbsolutePath());
                            System.out.println("[Lista] Enviada lista de " + n + " .txt a " + solicitante);
                            continue;
                        }

                        // ===== Petición de "pull" de archivo desde otro usuario =====
                        if (linea.startsWith("PULL_FILE|")) {
                            String resto = linea.substring("PULL_FILE|".length());
                            String[] p = resto.split("\\|", 2);
                            if (p.length == 2) {
                                String solicitante = p[0].trim();
                                String nombre = p[1].trim();

                                if (nombre.contains("..") || nombre.contains("/") || nombre.contains("\\")) {
                                    escritorServidor.println("PULL_ERROR|" + solicitante + "|Nombre inválido.");
                                    continue;
                                }

                                // Buscar en las mismas rutas del listado
                                java.util.List<File> roots = new java.util.ArrayList<>();
                                roots.add(new File(".").getAbsoluteFile());
                                if (baseDir[0] != null) {
                                    roots.add(baseDir[0]);
                                    File dls = new File(baseDir[0], "descargas");
                                    if (dls.exists()) roots.add(dls);
                                }

                                File elegido = null;
                                for (File root : roots) {
                                    File f = new File(root, nombre);
                                    if (f.exists() && f.isFile() && nombre.toLowerCase().endsWith(".txt")) {
                                        elegido = f; break;
                                    }
                                }

                                if (elegido == null) {
                                    escritorServidor.println("PULL_ERROR|" + solicitante + "|No se encontró .txt llamado '" + nombre + "' en rutas conocidas.");
                                    System.out.println("[Pull] No encontrado '" + nombre + "'. Revisado:");
                                    for (File r : roots) System.out.println("  - " + new File(r, nombre).getAbsolutePath());
                                    continue;
                                }

                                String baseName = elegido.getName();
                                escritorServidor.println("FILE_UPLOAD_BEGIN|" + solicitante + "|" + baseName);
                                try (BufferedReader fr = new BufferedReader(
                                        new InputStreamReader(new FileInputStream(elegido), StandardCharsets.UTF_8))) {
                                    String l;
                                    while ((l = fr.readLine()) != null) {
                                        escritorServidor.println(l);
                                    }
                                } catch (IOException e) {
                                    escritorServidor.println("PULL_ERROR|" + solicitante + "|Error leyendo: " + e.getMessage());
                                }
                                escritorServidor.println("FILE_UPLOAD_END");
                                System.out.println("[Pull] Enviado " + baseName + " a " + solicitante + " desde " + elegido.getAbsolutePath());
                            }
                            continue;
                        }

                        // ===== Mensajes normales del servidor (también se registran en log local) =====
                        System.out.println(linea);
                        if (!linea.startsWith("FILE_BEGIN ")
                                && !"FILE_END".equals(linea)
                                && !linea.startsWith("UPLOAD_REQUEST|")
                                && !linea.startsWith("LIST_LOCAL_TXT|")
                                && !linea.startsWith("PULL_FILE|")
                                && !linea.startsWith("FILE_UPLOAD_BEGIN|")
                                && !"FILE_UPLOAD_END".equals(linea)
                                && !linea.startsWith("LIST_RESP_BEGIN|")
                                && !"LIST_RESP_END".equals(linea)
                                && !linea.startsWith("PULL_ERROR|")
                                && !linea.startsWith("SESSION_USER|")) {
                            logLine.accept(linea);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Conexion cerrada por el servidor.");
                } finally {
                    if (archivoOut != null) {
                        try { archivoOut.flush(); archivoOut.close(); } catch (IOException ignored) {}
                    }
                    if (convLog[0] != null) {
                        try {
                            convLog[0].write("=== Fin de sesión: " + java.time.LocalDateTime.now() + " ===");
                            convLog[0].newLine();
                            convLog[0].flush();
                            convLog[0].close();
                        } catch (IOException ignored) {}
                    }
                }
            });
            leerServidor.setDaemon(true);
            leerServidor.start();

            // Loop principal: manda lo que escribes al server
            String entradaUsuario;
            while ((entradaUsuario = lectorUsuario.readLine()) != null) {
                entradaUsuario = entradaUsuario.trim();
                if (entradaUsuario.equalsIgnoreCase("salir")) {
                    escritorServidor.println("3"); // opción "Salir"
                    break;
                }
                escritorServidor.println(entradaUsuario);
            }

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }
}
