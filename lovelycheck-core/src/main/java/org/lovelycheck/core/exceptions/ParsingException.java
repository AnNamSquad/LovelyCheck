package org.lovelycheck.core.exceptions;

public class ParsingException extends LovelyCheckException {

    public ParsingException(String errorMessage) {
        super("This error is related to an invalid lovelycheck configuration. Please read the additional information carefully.", errorMessage);
    }

}
