/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import io.github.cdimascio.dotenv.Dotenv;
/**
 *
 * @author John Mark
 */
public class DatabaseConnection {
    private static final Dotenv dotenv = Dotenv.load();
    //For Online eto 
    public static final String ONLINE_DATABASE_URL = dotenv.get("DB_URL");
    public static final String ONLINE_DATABASE_USERNAME = dotenv.get("DB_USER");
    public static final String ONLINE_DATABASE_PASSWORD = dotenv.get("DB_PASS");
    
    //For XAMPP eto 
    public static final String LOCAL_DATABASE_URL = "";
    public static final String LOCAL_DATABASE_USERNAME = "";
    public static final String LOCAL_DATABASE_PASSWORD = "";
    
    public static Connection getConnection(){
        Connection conn = null;
        
        try{
            System.out.println("Trying to connect online...");
            conn = DriverManager.getConnection(ONLINE_DATABASE_URL, ONLINE_DATABASE_USERNAME, ONLINE_DATABASE_PASSWORD);
            System.out.println("Connected Successfully");
        }
        catch(SQLException e){
            System.out.println("Connection Failed, Trying to connect LOCAL...");
            try{
                conn = DriverManager.getConnection(LOCAL_DATABASE_URL, LOCAL_DATABASE_USERNAME, LOCAL_DATABASE_PASSWORD);
                System.out.println("Connected Successfully");
            }
            catch(SQLException ex){
                System.out.println("BOTH server are not reachable");
                ex.printStackTrace();
            }
        }
        return conn;
    }
}
