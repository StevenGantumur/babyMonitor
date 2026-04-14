package com.babyMonitor;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to the Baby Monitor! By Steven Gantumur The Third!!!!");
        System.out.println("Please select a mode:");
        System.out.println("1. Crib Monitor");
        System.out.println("2. Room/PlayPlace Monitor");

        int choice = scanner.nextInt();
        
        if (choice == 1) {
            System.out.println("Starting Crib Monitor...");
            CribMonitor.start();
        } else if (choice == 2) {
            System.out.println("Starting Room Monitor...");
            //RoomMonitor.start();
        } else {
            System.out.println("Invalid choice. Please restart.");
        }
        
        scanner.close();
    }
}
