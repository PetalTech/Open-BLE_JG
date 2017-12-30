package uk.ac.lancs.scc.openbcible;

import java.io.ByteArrayOutputStream;

//APACHE DEPRECIATED as of API 23 JG
//import org.apache.http.util.ByteArrayBuffer;

public class HexAsciiHelper {
    public static int PRINTABLE_ASCII_MIN = 0x20; // ' '
    public static int PRINTABLE_ASCII_MAX = 0x7E; // '~'

    public static boolean isPrintableAscii(int c) {
        return c >= PRINTABLE_ASCII_MIN && c <= PRINTABLE_ASCII_MAX;
    }

    public static String bytesToHex(byte[] data) {
        return bytesToHex(data, 0, data.length);
    }

    public static String bytesToHex(byte[] data, int offset, int length) {
        if (length <= 0) {
            return "";
        }

        StringBuilder hex = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            hex.append(String.format(" %02X", data[i] % 0xFF));
        }
        hex.deleteCharAt(0);
        return hex.toString();
    }

    public static String bytesToAsciiMaybe(byte[] data) {
        return bytesToAsciiMaybe(data, 0, data.length);
    }


    //ORIGINAL
    public static String bytesToAsciiMaybe(byte[] data, int offset, int length) {
        StringBuilder ascii = new StringBuilder();
        boolean zeros = false;
        for (int i = offset; i < offset + length; i++) {
            int c = data[i] & 0xFF;
            if (isPrintableAscii(c)) {
                if (zeros) {
                    return null;
                }
                ascii.append((char) c);
            } else if (c == 0) {
                zeros = true;
            } else {
                return null;
            }
        }
        return ascii.toString();
    }
    //ORIGINAL


    //ORIGINAL
//    public static byte[] hexToBytes(String hex) {

    //ByteArrayBuffer from Apache is DISCONTINUED as of API 23 JG
    //ByteArrayBuffer bytes = new ByteArrayBuffer(hex.length() / 2);
    //Instead, we use ByteArrayOutputStream JG
//        ByteArrayOutputStream bytes = new ByteArrayOutputStream(hex.length() / 2);
//
//        for (int i = 0; i < hex.length(); i++) {
//            if (hex.charAt(i) == ' ') {
//                continue;
//            }
//
//            String hexByte;
//            if (i + 1 < hex.length()) {
//                hexByte = hex.substring(i, i + 2).trim();
//                i++;
//            } else {
//                hexByte = hex.substring(i, i + 1);
//            }
//
//            bytes.append(Integer.parseInt(hexByte, 16));
//        }
//
//        //"buffer" is depreciated: instead we use "buf" JG, PERHAPS BYTEBUFFER
//        //return bytes.buffer();
//        return bytes.buf();
//    }
//}
//ORIGINAL


    //JG FROM https://stackoverflow.com/questions/18714616/convert-hex-string-to-byte
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }
}
    //JG FROM https://stackoverflow.com/questions/18714616/convert-hex-string-to-byte


