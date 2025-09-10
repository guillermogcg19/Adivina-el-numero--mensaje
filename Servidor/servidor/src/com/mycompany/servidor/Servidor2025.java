/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.servidor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
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
    
     private static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            // aquí llamas a loopMenu()
        }

        // ⬇️ Aquí va tu método loopMenu
        private void loopMenu() throws IOException {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

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
                        out.println("Opcion 'Bandeja' aun no implementada.");
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
    } // 
}