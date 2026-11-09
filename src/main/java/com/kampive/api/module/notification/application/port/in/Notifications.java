package com.kampive.api.module.notification.application.port.in;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
public interface Notifications {
    record Item(UUID uuid,String title,String message,Instant date,boolean read,String requestType,UUID requestUuid) {}
    record Inbox(List<Item> items,int page,int size,long totalElements,long totalPages,long unreadCount) {}
    Inbox list(UUID actor,int page,int size,String filter);
    void read(UUID actor,UUID uuid,boolean read);
    void readAll(UUID actor);
}
