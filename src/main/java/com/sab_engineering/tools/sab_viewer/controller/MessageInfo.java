package com.sab_engineering.tools.sab_viewer.controller;

public class MessageInfo {
    private final String title;
    private final String message;
    private final int messageType; // ToDo: change to ENUM

    public MessageInfo(String title, String message, int messageType) {
        this.title = title;
        this.message = message;
        this.messageType = messageType;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public int getMessageType() {
        return messageType;
    }
}
