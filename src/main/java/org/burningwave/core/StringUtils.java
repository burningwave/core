package org.burningwave.core;

public class StringUtils {

    public static String capitalizeFirstCharacter(String value) {
        return Character.toString(value.charAt(0)).toUpperCase()
                + value.substring(1, value.length());
    }

    public static boolean isBlank(String str) {
        int strLen;
        if ((str == null) || ((strLen = str.length()) == 0)) {
            return true;
        }
        for (int i = 0; i < strLen; ++i) {
            if (!(Character.isWhitespace(str.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(String str) {
        return (!(isBlank(str)));
    }

    public static boolean isEmpty(String str) {
        return ((str == null) || (str.length() == 0));
    }

    public static boolean isNotEmpty(String str) {
        return (!(isEmpty(str)));
    }

    public static boolean contains(String str, char searchChar) {
        if (isEmpty(str)) {
            return false;
        }
        return (str.indexOf(searchChar) >= 0);
    }

    public static boolean areEquals(String string1, String string2) {
        return (isEmpty(string1) && isEmpty(string2)) ||
                (isNotEmpty(string1) && isNotEmpty(string2) && string1.equals(string2));
    }
}
