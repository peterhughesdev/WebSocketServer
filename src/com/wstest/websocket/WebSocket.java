package com.wstest.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.bind.DatatypeConverter;

import com.wstest.websocket.listener.WebSocketListener;
import com.wstest.websocket.parser.WebSocketParser;

public class WebSocket {
    // Used for handling bytebuffer <=> string operations
    private static Charset charset = Charset.forName("UTF-8");
    private static CharsetEncoder encoder = charset.newEncoder();
    private static CharsetDecoder decoder = charset.newDecoder();
    
    // Receive Buffer Size 
    public static final int RCVBUF = 16384;
    
    // Allowable WebSocket states
    public static enum State {
        READY, OPEN, CLOSED
    };
    
    // TODO
    // Use for higher-level API
    private final WebSocketListener listener;
    
    // Queue ByteBuffers for reading and writing, to allow async processing
    private final BlockingQueue<ByteBuffer> inQueue = new LinkedBlockingQueue<ByteBuffer>();
    private final BlockingQueue<ByteBuffer> outQueue = new LinkedBlockingQueue<ByteBuffer>();
    
    // Internal state, indicates ability to perform certain methods
    private State state = State.READY;
    
    // Reference to Selector key (will have self as attachment)
    private SelectionKey key;
    private SocketChannel channel;
    
    public WebSocket(WebSocketListener listener) {
        this.listener = listener;
    }
    
    public void setKey(SelectionKey key) {
        this.key = key;
    }
    
    public void setChannel(SocketChannel channel) {
        this.channel = channel;
    }
        
    /**
     * Queue buffer for sending
     * 
     * @param buffer
     */
    public void queue(ByteBuffer buffer) {
        outQueue.add(buffer);
    }
    
    /**
     * Queue buffer for consuming
     * 
     * @param buffer
     */
    public void consume(ByteBuffer buffer) {
        inQueue.add(buffer);
    }
    
    /**
     * Read from SocketChannel to given ByteBuffer
     * 
     * @param buffer
     * @return true if read successful, otherwise false
     * @throws IOException
     */
    public boolean read(ByteBuffer buffer) throws IOException {
        buffer.clear();
         
        int read = channel.read(buffer);
        buffer.flip();
        
        if (read == -1) eot();

        return read > 0;
    }
    
    /**
     * Write queued buffers to SocketChannel
     * 
     * @throws IOException
     */
    public void write() throws IOException {
        ByteBuffer buffer = outQueue.poll();

        while (buffer != null) {
            channel.write(buffer);
            
            if (buffer.remaining() <= 0) {
                buffer = outQueue.poll();
            }
        }
    } 
    
    /**
     * Process queued inbound buffers
     * 
     * @throws CharacterCodingException 
     * @throws UnsupportedEncodingException 
     * @throws NoSuchAlgorithmException 
     */
    public void process() throws CharacterCodingException, NoSuchAlgorithmException, UnsupportedEncodingException {
        ByteBuffer buffer = inQueue.poll();
        
        if (buffer != null) {
            decode(buffer);
        }
    }
    
    /**
     * Convert a ByteBuffer to WebSocket protocol
     * 
     * @param buffer
     * @throws CharacterCodingException 
     * @throws UnsupportedEncodingException 
     * @throws NoSuchAlgorithmException 
     */
    public void decode(ByteBuffer buffer) throws CharacterCodingException, NoSuchAlgorithmException, UnsupportedEncodingException {
        if (buffer.hasRemaining()) {
            switch (state) {
            case READY:
                decodeHandshake(buffer);
                break;
            case OPEN:
                decodeFrames(buffer);
                break;
            }
        }
    }
    
    /**
     * Decode Handshake message from Client
     * 
     * @param buffer
     * @throws CharacterCodingException
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    private synchronized void decodeHandshake(ByteBuffer buffer) throws CharacterCodingException, NoSuchAlgorithmException, UnsupportedEncodingException {
       ByteBuffer response = WebSocketParser.createHandshakeResponse(buffer);
       if (response != null) {
           state = State.OPEN;
           listener.onOpen();
           queue(response);
       }
    }
    
    // TODO
    // IMPLEMENT PROPERLY
    /**
     * Decode message frames from client
     * 
     * @param buffer
     * @throws CharacterCodingException 
     */
    private synchronized void decodeFrames(ByteBuffer buffer) throws CharacterCodingException {
        List<String> messages = WebSocketParser.parseWebsocketMessage(buffer);
        
        for (String message : messages) {
            listener.onMessage(message);
        }
    }
    
    /**
     * Handle buffer exception
     */
    private void eot() {
       // Shit's fucked, yo
    }  
    
   
}
