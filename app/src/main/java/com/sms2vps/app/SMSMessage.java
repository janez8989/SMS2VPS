package com.sms2vps.app;

public record SMSMessage(
    String sender,
    String message,
    long timestamp,
    boolean isRead,
    boolean uploadedToVPS,
    boolean uploadFailed,
    boolean googleSent,
    boolean googleFailed,
    boolean driveSent,
    boolean driveFailed
) {}