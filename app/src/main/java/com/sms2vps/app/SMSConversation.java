package com.sms2vps.app;

// Muutettu luokka record-luokaksi, mikä soveltuu hyvin tietorakenteiden kuvaamiseen
public record SMSConversation(String phoneNumber, String lastMessage, String timestamp, int unreadCount, boolean uploadedToVPS, boolean uploadFailed, boolean googleSent, boolean googleFailed, boolean driveSent, boolean driveFailed) {
    // Record-luokka generoi automaattisesti getterit ja muut tarvittavat metodit
    
    public SMSConversation {
        // Compact constructor to ensure valid state
        if (phoneNumber == null || lastMessage == null || timestamp == null) {
            throw new IllegalArgumentException("Required fields cannot be null");
        }
    }
    
    // Poistettu käyttämättömät metodit, koska record-luokka generoi automaattisesti getterit näille kentille
}