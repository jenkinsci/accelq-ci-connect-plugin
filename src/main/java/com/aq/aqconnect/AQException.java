package com.aq.aqconnect;


public class AQException extends RuntimeException{

    public AQException(String message) {
        super(String.format("[%s]", message), null, false, false);
    }
}