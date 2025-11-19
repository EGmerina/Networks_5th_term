package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {

        if(args.length!=1){
            System.out.println("Usage : <server_port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        SocksProxy socksProxy = null;
        try {
            socksProxy = new SocksProxy();
        } catch (IOException e) {
            System.out.println("can't init proxy");
            throw new RuntimeException(e);
        }
        socksProxy.start(port);
    }
}