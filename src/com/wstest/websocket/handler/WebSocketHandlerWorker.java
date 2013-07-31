package com.wstest.websocket.handler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.wstest.websocket.WebSocket;

public class WebSocketHandlerWorker extends Thread {
    private BlockingQueue<WebSocket> queue = new LinkedBlockingQueue<WebSocket>();
    
    public void put (WebSocket ws) throws InterruptedException {
        queue.put(ws);
    }
    
    public void run() {
        try {
            while (true) {
                WebSocket ws = queue.take();
                ws.process();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
