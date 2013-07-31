package com.wstest.websocket.parser;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

public class WebSocketParser {
    
    // Used for handling bytebuffer <=> string operations
    private static Charset charset = Charset.forName("iso-8859-1");
    private static CharsetEncoder encoder = charset.newEncoder();
    private static CharsetDecoder decoder = charset.newDecoder();
    
    public static ByteBuffer createHandshakeResponse(ByteBuffer buffer) throws CharacterCodingException, 
                                                                                NoSuchAlgorithmException, 
                                                                                UnsupportedEncodingException {
        CharBuffer chars = decoder.decode(buffer);
        
        // Ignore first line (GET / HTTP/1.1)
        String line = readLine(chars);
        
        // Get headers
        Properties props = new Properties();
        while ((line = readLine(chars)) != null && !line.isEmpty()) {
            String[] val = line.split(": ");
            props.put(val[0], val[1]);
        }
        
        // Handle upgrade
        if (props.getProperty("Upgrade").equals("websocket") && props.get("Sec-WebSocket-Version").equals("13")) {
            String key = (String) props.get("Sec-WebSocket-Key");
            String hash = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"; // Magic websocket GUID (http://tools.ietf.org/html/rfc4122)
            
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(hash.getBytes("iso-8859-1"), 0, hash.length());
            
            byte[] sha1Hash = md.digest();
            
            String ret64 = base64(sha1Hash);
            
            StringBuilder ret = new StringBuilder();
            ret.append("HTTP/1.1 101 Switching Protocols\r\n");
            ret.append("Upgrade: websocket\r\n");
            ret.append("Connection: Upgrade\r\n");
            ret.append("Sec-WebSocket-Accept: " + ret64 + "\r\n");
            ret.append("\r\n");
            
            return encoder.encode(CharBuffer.wrap(ret.toString()));
        }
        
        return null;
    }
    
    public static List<String> parseWebsocketMessage(ByteBuffer buffer) throws CharacterCodingException {
        List<String> messages = new ArrayList<String>();
        while (buffer.hasRemaining()) {
            buffer.mark();
            
            int max = buffer.remaining(),
                real = 2;
            
            if (max < 2) {
                break;
            }
            
            byte b1 = buffer.get();
            boolean end = b1 >> 8 != 0;
            
            // MAGIC HAPPENS
            
            byte rsv = (byte) ((b1 & ~(byte)128) >> 4);
            if (rsv != 0) break;
            
            byte b2 = buffer.get();
            boolean mask = (b2 & -128) != 0;
            
            // Payload length
            int l = (byte) (b2 & ~(byte)128);
            if (l > 125) {
                if (l == 126) {
                    real += 2;
                    
                    byte[] size = new byte[3];
                    size[1] = buffer.get();
                    size[2] = buffer.get();
                    
                    l = new BigInteger(size).intValue();
                } else {
                    real += 8;
                    
                    byte[] size = new byte[8];
                    for (int i = 0; i < 8; ++i) {
                        size[i] = buffer.get();
                    }
                    
                    l = (int) new BigInteger(size).longValue();
                }
            }
            
            real += (mask ? 4 : 0);
            real += l;
            
            ByteBuffer payload = ByteBuffer.allocate(l);
            if (mask) {
                byte[] key = new byte[4];
                buffer.get(key);
                
                // EVEN MORE MAGIC WHAT IS THIS SHIT
                
                for (int i = 0; i < l; ++i) {
                    payload.put( (byte) ((byte)buffer.get() ^ (byte) key[i % 4]) );
                }
            } else {
                payload.put(buffer.array(), buffer.position(), payload.limit());
                buffer.position(buffer.position() + payload.limit());
            }
            
            payload.flip();
            
            CharBuffer chars = decoder.decode(payload);
            messages.add(chars.toString());
        }
        
        return messages;
    }
    
    /**
     * Convert a byte array to base64 String
     * 
     * @param input
     * @return
     */
    private static String base64(byte[] input) {
        return DatatypeConverter.printBase64Binary(input);
    }
    
    /**
     * Consume a CharBuffer and return a String representing a given line.
     * Expects CharBuffer to represent HTTP headers, i.e each line ends with \r\n
     * 
     * @param in
     * @return
     */
    private static String readLine(CharBuffer in) {
        int read = 0, prev;
        StringBuilder line = new StringBuilder();
        
        // TODO 
        // Probably a better way to handle this
        while (true) {
            prev = read;
            read = in.get();

            if (read != 13 && read != 10) {
                line.append((char) read);
            }
            
            if (prev == 13 && read == 10) break;
        }

        return line.toString();
    }
}
