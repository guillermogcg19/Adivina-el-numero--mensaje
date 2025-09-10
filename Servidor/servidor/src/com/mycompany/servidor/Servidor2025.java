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

    private static class ClientHandler implements Runnable {

        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket s = socket) {
                out = new PrintWriter(s.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                // Aquí iría la autenticación si la tienes
                loopMenu();

            } catch (IOException e) {
                System.out.println("Error con cliente: " + e.getMessage());
            }
        }

        // MÉTODO DEL MENÚ
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
                if (op == null) {
                    break;
                }

                switch (op.trim()) {
                    case "1":
                        juegoAdivina();
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

        // MÉTODO DEL JUEGO
        private void juegoAdivina() throws IOException {
            int numeroSecreto = new Random().nextInt(10) + 1;
            out.println("Adivina un numero del 1 al 10. Tienes 3 intentos.");

            int intentos = 0, maxIntentos = 3;
            boolean ok = false;
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
                        out.println("¡Adivinaste!");
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
        }
    }
}
