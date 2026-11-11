package com.jagrosh.discordipc.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import net.lenni0451.reflect.Methods;

public class WinRegistry {
   public static final int HKEY_CURRENT_USER = -2147483647;
   public static final int HKEY_LOCAL_MACHINE = -2147483646;
   public static final int REG_SUCCESS = 0;
   private static final int KEY_ALL_ACCESS = 983103;
   private static final int KEY_READ = 131097;
   private static final Preferences userRoot = Preferences.userRoot();
   private static final Preferences systemRoot = Preferences.systemRoot();
   private static final Class userClass = userRoot.getClass();
   private static final Method regOpenKey;
   private static final Method regCloseKey;
   private static final Method regQueryValueEx;
   private static final Method regEnumValue;
   private static final Method regQueryInfoKey;
   private static final Method regEnumKeyEx;
   private static final Method regCreateKeyEx;
   private static final Method regSetValueEx;
   private static final Method regDeleteKey;
   private static final Method regDeleteValue;
   private static final float javaSpec;

   private WinRegistry() {
   }

   public static String readString(int hkey, String key, String valueName) {
      if (hkey == -2147483646) {
         return readString(systemRoot, hkey, key, valueName);
      } else if (hkey == -2147483647) {
         return readString(userRoot, hkey, key, valueName);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static String readString(long hkey, String key, String valueName) {
      if (hkey == -2147483646L) {
         return readString(systemRoot, hkey, key, valueName);
      } else if (hkey == -2147483647L) {
         return readString(userRoot, hkey, key, valueName);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static Map readStringValues(int hkey, String key) {
      if (hkey == -2147483646) {
         return readStringValues(systemRoot, hkey, key);
      } else if (hkey == -2147483647) {
         return readStringValues(userRoot, hkey, key);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static List readStringSubKeys(int hkey, String key) {
      if (hkey == -2147483646) {
         return readStringSubKeys(systemRoot, hkey, key);
      } else if (hkey == -2147483647) {
         return readStringSubKeys(userRoot, hkey, key);
      } else {
         throw new IllegalArgumentException("hkey=" + hkey);
      }
   }

   public static void createKey(int hkey, String key) {
      int[] result;
      if (hkey == -2147483646) {
         result = createKey(systemRoot, hkey, key);
         Methods.invoke(systemRoot, regCloseKey, new Object[]{result[0]});
      } else {
         if (hkey != -2147483647) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         result = createKey(userRoot, hkey, key);
         Methods.invoke(userRoot, regCloseKey, new Object[]{result[0]});
      }

      if (result[1] != 0) {
         throw new IllegalArgumentException("rc=" + result[1] + "  key=" + key);
      }
   }

   public static void createKey(long hkey, String key) {
      long[] result;
      if (hkey == -2147483646L) {
         result = createKey(systemRoot, hkey, key);
         Methods.invoke(systemRoot, regCloseKey, new Object[]{result[0]});
      } else {
         if (hkey != -2147483647L) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         result = createKey(userRoot, hkey, key);
         Methods.invoke(userRoot, regCloseKey, new Object[]{result[0]});
      }

      if (result[1] != 0L) {
         throw new IllegalArgumentException("rc=" + result[1] + "  key=" + key);
      }
   }

   public static void writeStringValue(int hkey, String key, String valueName, String value) {
      if (hkey == -2147483646) {
         writeStringValue(systemRoot, hkey, key, valueName, value);
      } else {
         if (hkey != -2147483647) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         writeStringValue(userRoot, hkey, key, valueName, value);
      }
   }

   public static void writeStringValue(long hkey, String key, String valueName, String value) {
      if (hkey == -2147483646L) {
         writeStringValue(systemRoot, hkey, key, valueName, value);
      } else {
         if (hkey != -2147483647L) {
            throw new IllegalArgumentException("hkey=" + hkey);
         }

         writeStringValue(userRoot, hkey, key, valueName, value);
      }
   }

   public static void deleteKey(int hkey, String key) {
      int result = -1;
      if (hkey == -2147483646) {
         result = deleteKey(systemRoot, hkey, key);
      } else if (hkey == -2147483647) {
         result = deleteKey(userRoot, hkey, key);
      }

      if (result != 0) {
         throw new IllegalArgumentException("rc=" + result + "  key=" + key);
      }
   }

   public static void deleteValue(int hkey, String key, String valueName) {
      int result = -1;
      if (hkey == -2147483646) {
         result = deleteValue(systemRoot, hkey, key, valueName);
      } else if (hkey == -2147483647) {
         result = deleteValue(userRoot, hkey, key, valueName);
      }

      if (result != 0) {
         throw new IllegalArgumentException("rc=" + result + "  key=" + key + "  value=" + valueName);
      }
   }

   private static int deleteValue(Preferences root, int hkey, String key, String valueName) {
      int[] openedKey = (int[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 983103});
      if (openedKey[1] != 0) {
         return openedKey[1];
      }

      int result = (Integer)Methods.invoke(root, regDeleteValue, new Object[]{openedKey[0], toCstr(valueName)});
      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
      return result;
   }

   private static int deleteKey(Preferences root, int hkey, String key) {
      return (Integer)Methods.invoke(root, regDeleteKey, new Object[]{hkey, toCstr(key)});
   }

   private static String readString(Preferences root, int hkey, String key, String valueName) {
      int[] openedKey = (int[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 131097});
      if (openedKey[1] != 0) {
         return null;
      }

      byte[] value = (byte[])Methods.invoke(root, regQueryValueEx, new Object[]{openedKey[0], toCstr(valueName)});
      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
      return value != null ? new String(value).trim() : null;
   }

   private static String readString(Preferences root, long hkey, String key, String valueName) {
      long[] openedKey = (long[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 131097});
      if (openedKey[1] != 0L) {
         return null;
      }

      byte[] value = (byte[])Methods.invoke(root, regQueryValueEx, new Object[]{openedKey[0], toCstr(valueName)});
      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
      return value != null ? new String(value).trim() : null;
   }

   private static Map readStringValues(Preferences root, int hkey, String key) {
      HashMap values = new HashMap();
      int[] openedKey = (int[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 131097});
      if (openedKey[1] != 0) {
         return null;
      }

      int[] info = (int[])Methods.invoke(root, regQueryInfoKey, new Object[]{openedKey[0]});
      int count = info[0];
      int maxNameLength = info[3];

      for (int index = 0; index < count; index++) {
         byte[] name = (byte[])Methods.invoke(root, regEnumValue, new Object[]{openedKey[0], index, maxNameLength + 1});
         String value = readString(hkey, key, new String(name));
         values.put(new String(name).trim(), value);
      }

      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
      return values;
   }

   private static List readStringSubKeys(Preferences root, int hkey, String key) {
      ArrayList subkeys = new ArrayList();
      int[] openedKey = (int[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 131097});
      if (openedKey[1] != 0) {
         return null;
      }

      int[] info = (int[])Methods.invoke(root, regQueryInfoKey, new Object[]{openedKey[0]});
      int count = info[0];
      int maxNameLength = info[3];

      for (int index = 0; index < count; index++) {
         byte[] name = (byte[])Methods.invoke(root, regEnumKeyEx, new Object[]{openedKey[0], index, maxNameLength + 1});
         subkeys.add(new String(name).trim());
      }

      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
      return subkeys;
   }

   private static int[] createKey(Preferences root, int hkey, String key) {
      return (int[])Methods.invoke(root, regCreateKeyEx, new Object[]{hkey, toCstr(key)});
   }

   private static long[] createKey(Preferences root, long hkey, String key) {
      return (long[])Methods.invoke(root, regCreateKeyEx, new Object[]{hkey, toCstr(key)});
   }

   private static void writeStringValue(Preferences root, int hkey, String key, String valueName, String value) {
      int[] openedKey = (int[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 983103});
      Methods.invoke(root, regSetValueEx, new Object[]{openedKey[0], toCstr(valueName), toCstr(value)});
      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
   }

   private static void writeStringValue(Preferences root, long hkey, String key, String valueName, String value) {
      long[] openedKey = (long[])Methods.invoke(root, regOpenKey, new Object[]{hkey, toCstr(key), 983103});
      Methods.invoke(root, regSetValueEx, new Object[]{openedKey[0], toCstr(valueName), toCstr(value)});
      Methods.invoke(root, regCloseKey, new Object[]{openedKey[0]});
   }

   private static byte[] toCstr(String text) {
      byte[] bytes = new byte[text.length() + 1];

      for (int index = 0; index < text.length(); index++) {
         bytes[index] = (byte)text.charAt(index);
      }

      bytes[text.length()] = 0;
      return bytes;
   }

   static {
      try {
         javaSpec = Float.parseFloat(System.getProperty("java.specification.version"));
         regOpenKey = Methods.getDeclaredMethod(
            userClass, "WindowsRegOpenKey", new Class[]{javaSpec >= 11.0F ? long.class : int.class, byte[].class, int.class}
         );
         regCloseKey = Methods.getDeclaredMethod(userClass, "WindowsRegCloseKey", new Class[]{javaSpec >= 11.0F ? long.class : int.class});
         regQueryValueEx = Methods.getDeclaredMethod(userClass, "WindowsRegQueryValueEx", new Class[]{javaSpec >= 11.0F ? long.class : int.class, byte[].class});
         regEnumValue = Methods.getDeclaredMethod(
            userClass, "WindowsRegEnumValue", new Class[]{javaSpec >= 11.0F ? long.class : int.class, int.class, int.class}
         );
         regQueryInfoKey = Methods.getDeclaredMethod(userClass, "WindowsRegQueryInfoKey1", new Class[]{javaSpec >= 11.0F ? long.class : int.class});
         regEnumKeyEx = Methods.getDeclaredMethod(
            userClass, "WindowsRegEnumKeyEx", new Class[]{javaSpec >= 11.0F ? long.class : int.class, int.class, int.class}
         );
         regCreateKeyEx = Methods.getDeclaredMethod(userClass, "WindowsRegCreateKeyEx", new Class[]{javaSpec >= 11.0F ? long.class : int.class, byte[].class});
         regSetValueEx = Methods.getDeclaredMethod(
            userClass, "WindowsRegSetValueEx", new Class[]{javaSpec >= 11.0F ? long.class : int.class, byte[].class, byte[].class}
         );
         regDeleteValue = Methods.getDeclaredMethod(userClass, "WindowsRegDeleteValue", new Class[]{javaSpec >= 11.0F ? long.class : int.class, byte[].class});
         regDeleteKey = Methods.getDeclaredMethod(userClass, "WindowsRegDeleteKey", new Class[]{javaSpec >= 11.0F ? long.class : int.class, byte[].class});
      } catch (Exception failure) {
         throw new RuntimeException("Unable to setup registry data", failure);
      }
   }
}
