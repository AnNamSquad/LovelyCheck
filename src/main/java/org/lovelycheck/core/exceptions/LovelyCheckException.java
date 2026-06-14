package org.lovelycheck.core.exceptions;

public class LovelyCheckException extends Exception {

    public final String advice;

    public LovelyCheckException(String advice, String errorMessage) {
        super(errorMessage);
        this.advice = advice;
    }
}