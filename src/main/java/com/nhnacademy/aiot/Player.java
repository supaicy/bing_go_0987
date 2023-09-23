package com.nhnacademy.aiot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Player {
    private int turn;
    private String nickname;
    private int[][] bingoBoard;
    
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Player(Socket socket) {
        this.socket = socket;
        try {
            this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
    

}

