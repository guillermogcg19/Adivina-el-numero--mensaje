/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.cliente;

import java.io.*;
import java.net.Socket;
/**
 *
 * @author Guillermo
 */

public class Cliente2025 {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 9090)) {
            System.out.println("Conectado al servidor.");
        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }
}