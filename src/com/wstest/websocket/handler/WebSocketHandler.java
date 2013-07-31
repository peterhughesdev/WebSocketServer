package com.wstest.websocket.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import com.wstest.websocket.WebSocket;
import com.wstest.websocket.listener.WebSocketListener;


import wstest.server.Server;

public class WebSocketHandler implements Runnable {
    private final String name;
    private final WebSocketListener listener;
    private final InetSocketAddress address;
    
    // Selector thread
    private Thread thread;
    private Selector selector;
    private ServerSocketChannel server;
    
    // Worker thread
    private WebSocketHandlerWorker worker = new WebSocketHandlerWorker();
    
    /**
     * Handle Websocket connections from a specific port
     * 
     * @param name
     * @param port
     * @param parent
     */
    public WebSocketHandler(String name, int port, WebSocketListener listener) {
        this.name = name;
        this.listener = listener;
      
        this.address = new InetSocketAddress(port);
    }
    
    public void init() throws Exception {
        // Maintain reference to thread
        synchronized (this) {
            thread = Thread.currentThread();
            thread.setName(name);
        }
        
        // Open non-blocking channel
        server = ServerSocketChannel.open();
        server.configureBlocking(false);
        
        // Bind a new socket to our channel
        ServerSocket socket = server.socket();
        socket.setReceiveBufferSize(WebSocket.RCVBUF);
        socket.bind(address);
        
        // Open a selector for our channel to consume everything
        selector = Selector.open();
        server.register(selector, server.validOps());
        
        // Start the worker thread for handling connections
        worker.start();
    }
    
    /**
     * Initialise handler and begin selector loop
     * 
     */
    public void run() {
        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SelectionKey key;
        Set<SelectionKey> keys;
        Iterator<SelectionKey> iterator;
        
        // Keep alive as long as the thread is fine
        while (!thread.isInterrupted()) {
            try {
                selector.select();
                
                keys = selector.selectedKeys();
                iterator = keys.iterator();
                
                while (iterator.hasNext()) {
                    key = iterator.next();
                    handleKey(key);
                    
                    // Tidy up
                    iterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Perform the correct method based on a given SelectorKey
     * 
     * @param key
     * @throws Exception
     */
    protected void handleKey(SelectionKey key) throws Exception {
        if (key.isValid()) {
            if (key.isAcceptable()) acceptKey(key);
            if (key.isWritable())   writeKey(key);
            if (key.isReadable())   readKey(key);
        }
    }
    
    /**
     * Create a new WebSocket object for an 'OP_ACCEPT' key.
     * 
     * @param key
     * @throws Exception
     */
    protected void acceptKey(SelectionKey key) throws Exception {
        SocketChannel channel = server.accept();
        channel.configureBlocking(false);
        
        WebSocket ws = new WebSocket(listener);
        
        ws.setChannel(channel);
        ws.setKey(channel.register(selector, SelectionKey.OP_READ, ws));
    }
    
    /**
     * Handle an 'OP_READ' key
     * 
     * @param key
     * @throws InterruptedException
     * @throws IOException
     */
    protected void readKey(SelectionKey key) throws InterruptedException, IOException {
       WebSocket ws = (WebSocket) key.attachment();
       ByteBuffer buffer = createBuffer();

       if (ws.read(buffer)) {
           ws.consume(buffer); 
           
           // Schedule for processing
           queue(ws);
           
           // Set WebSocket key to handle writes as well
           if (key.isValid()) {
               key.interestOps(SelectionKey.OP_READ|SelectionKey.OP_WRITE);
           }
       }
    }
    
    /**
     * Handle an 'OP_WRITE' key
     * 
     * @param key
     * @throws IOException
     */
    protected void writeKey(SelectionKey key) throws IOException {
        WebSocket ws = (WebSocket) key.attachment();
        ws.write();
    }
   
    /**
     * Queue a WebSocket for processing on worker thread
     * 
     * @param ws
     * @throws InterruptedException
     */
    protected void queue(WebSocket ws) throws InterruptedException {
        worker.put(ws);
    }

    /**
     * Create a ByteBuffer set to default WebSocket message size
     * 
     * @return
     */
    protected ByteBuffer createBuffer() {
        return ByteBuffer.allocate(WebSocket.RCVBUF);
    }
}
