package com.sms2vps.app;

import androidx.annotation.NonNull;
import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
public final class ContactItem {
    private final String name;
    private final String phoneNumber;

    public ContactItem(String name, String phoneNumber) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "phoneNumber cannot be null");
    }

    @NonNull
    public String name() {
        return name;
    }

    @NonNull
    public String phoneNumber() {
        return phoneNumber;
    }

    @Override
    @NonNull
    public String toString() {
        return name + " (" + phoneNumber + ")";
    }
}