package com.mycompany.cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Cliente2025 {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 9090);
             BufferedReader lectorServidor = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter escritorServidor = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader lectorUsuario = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Conectado al servidor. Sigue las instrucciones.");

            // Hilo para leer mensajes del servidor
            Thread leerServidor = new Thread(() -> {
                try {
                    String mensajeServidor;
                    while ((mensajeServidor = lectorServidor.readLine()) != null) {
                        System.out.println(mensajeServidor);
                    }
                } catch (IOException e) {
                    System.out.println("Conexion cerrada por el servidor.");
                }
            });
            leerServidor.setDaemon(true);
            leerServidor.start();

            // Loop principal: lee del usuario y envía al servidor
            String entradaUsuario;
            while ((entradaUsuario = lectorUsuario.readLine()) != null) {
                entradaUsuario = entradaUsuario.trim();
                if (entradaUsuario.equalsIgnoreCase("salir")) {
                    escritorServidor.println("3"); // opción Salir del menú
                    break;
                }
                escritorServidor.println(entradaUsuario);
            }

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }
}
