package com.wstest.websocket.listener;

public interface WebSocketListener {
    public void onOpen();
    
    public void onClose();
    
    public void onMessage(String message);
}
