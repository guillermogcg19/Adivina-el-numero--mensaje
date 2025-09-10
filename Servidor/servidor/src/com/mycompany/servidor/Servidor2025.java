/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.servidor;
import java.io.IOException;
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
        System.out.println("Cliente conectado: " + socket.getRemoteSocketAddress());
    }
}
}