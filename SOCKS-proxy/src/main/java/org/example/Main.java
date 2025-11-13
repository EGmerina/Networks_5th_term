package org.example;
public class Main {
    public static void main(String[] args) {

        if(args.length!=1){
            System.out.println("Usage : <server_port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        SocksProxy socksProxy = new SocksProxy();
        socksProxy.start(port);
    }
}