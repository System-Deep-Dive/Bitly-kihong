package org.example.bitlygood.service;

import org.springframework.stereotype.Component;

@Component
public class Base62 {
    private static final char[] BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    public String encode(long value) {
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(BASE62[(int) (value % 62)]);
            value /= 62;
        }
        return sb.reverse().toString();
    }

    public long decode(String value) {
        long result = 0;
        long power = 1;
        for (int i = value.length() - 1; i >= 0; i--) {
            int digit = new String(BASE62).indexOf(value.charAt(i));
            result += digit * power;
            power *= 62;
        }
        return result;
    }

}
