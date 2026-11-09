package dev.argus.client.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DbusMessageCodec {
   static final int MAX_MESSAGE_BYTES = 16777216;

   static DbusMessageCodec.Layout layout(byte[] fixed) throws IOException {
      if (fixed.length != 16) {
         throw invalid("Fixed header length");
      }

      ByteOrder order = fixed[0] == 108 ? ByteOrder.LITTLE_ENDIAN : (fixed[0] == 66 ? ByteOrder.BIG_ENDIAN : null);
      if (order != null && fixed[3] == 1 && (fixed[2] & -8) == 0) {
         ByteBuffer b = ByteBuffer.wrap(fixed).order(order);
         int body = b.getInt(4);
         int serial = b.getInt(8);
         int fields = b.getInt(12);
         if (serial != 0 && body >= 0 && fields >= 0 && fields <= 16777193 && body <= 16777193 - fields) {
            return new DbusMessageCodec.Layout(order, fixed[1] & 255, fields, body);
         } else {
            throw invalid("Message size or serial");
         }
      } else {
         throw invalid("Version, flags or byte order");
      }
   }

   static DbusMessageCodec.Message decode(DbusMessageCodec.Layout layout, byte[] paddedFields, byte[] body) throws IOException {
      if (paddedFields.length == layout.paddedFieldsBytes() && body.length == layout.bodyBytes()) {
         DbusMessageCodec.Reader fields = new DbusMessageCodec.Reader(paddedFields, layout.order());
         LinkedHashMap<Integer, DbusMessageCodec.Variant> headers = new LinkedHashMap<>();

         while (fields.position < layout.fieldsBytes()) {
            fields.align(8);
            if (fields.position >= layout.fieldsBytes()) {
               throw invalid("Trailing header alignment");
            }

            int id = fields.byteValue();
            DbusMessageCodec.Variant value = fields.variant(0);
            if (id == 0 || headers.putIfAbsent(id, value) != null || fields.position > layout.fieldsBytes()) {
               throw invalid("Header field");
            }
            String expected = switch (id) {
               case 1 -> "o";
               case 2, 3, 4, 6, 7 -> "s";
               case 5, 9 -> "u";
               case 8 -> "g";
               default -> null;
            };
            if (expected != null && !expected.equals(value.signature())) {
               throw invalid("Header field type");
            }
         }

         fields.align(8);
         if (fields.position != paddedFields.length) {
            throw invalid("Header padding");
         }

         if (headers.containsKey(9) && (Long)headers.get(9).value() != 0L) {
            throw invalid("Unix FD passing not negotiated");
         }

         boolean hasPath = headers.containsKey(1);
         boolean hasMember = headers.containsKey(3);
         if (layout.type() == 1 && (!hasPath || !hasMember) || layout.type() == 4 && (!hasPath || !hasMember || !headers.containsKey(2))) {
            throw invalid("Required request/signal headers");
         }

         if ((layout.type() != 2 && layout.type() != 3 || headers.containsKey(5)) && (layout.type() != 3 || headers.containsKey(4))) {
            String signature = headers.containsKey(8) ? (String)headers.get(8).value() : "";
            DbusMessageCodec.Reader reader = new DbusMessageCodec.Reader(body, layout.order());
            List<Object> arguments = new ArrayList<>();

            for (String type : types(signature)) {
               arguments.add(reader.value(type, 0));
            }

            if (reader.position != body.length) {
               throw invalid("Body signature/length");
            }

            long replySerial = headers.containsKey(5) ? (Long)headers.get(5).value() : 0L;
            return new DbusMessageCodec.Message(layout.type(), replySerial, Collections.unmodifiableMap(headers), List.copyOf(arguments));
         } else {
            throw invalid("Required reply headers");
         }
      } else {
         throw invalid("Message lengths");
      }
   }

   static byte[] methodCall(int serial, String destination, String path, String iface, String member, List<String> arguments) throws IOException {
      if (serial == 0) {
         throw invalid("Zero serial");
      }

      DbusMessageCodec.Writer fields = new DbusMessageCodec.Writer();
      DbusMessageCodec.Writer body = new DbusMessageCodec.Writer();
      fields.field(1, "o", path);
      fields.field(2, "s", iface);
      fields.field(3, "s", member);
      fields.field(6, "s", destination);
      String signature = "s".repeat(arguments.size());
      if (!signature.isEmpty()) {
         fields.field(8, "g", signature);
      }

      for (String argument : arguments) {
         body.string(argument);
      }

      int fieldsBytes = fields.bytes.size();
      fields.align(8);
      ByteBuffer fixed = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
      fixed.put((byte)108).put((byte)1).put((byte)2).put((byte)1).putInt(body.bytes.size()).putInt(serial).putInt(fieldsBytes);
      ByteArrayOutputStream all = new ByteArrayOutputStream();
      all.writeBytes(fixed.array());
      all.writeBytes(fields.bytes.toByteArray());
      all.writeBytes(body.bytes.toByteArray());
      if (all.size() > 16777216) {
         throw invalid("Request too large");
      } else {
         return all.toByteArray();
      }
   }

   private static List<String> types(String signature) throws IOException {
      if (signature.length() > 255) {
         throw invalid("Signature length");
      }

      List<String> result = new ArrayList<>();
      int at = 0;

      while (at < signature.length()) {
         int end = typeEnd(signature, at, 0, false);
         result.add(signature.substring(at, end));
         at = end;
      }

      return result;
   }

   private static int typeEnd(String signature, int at, int depth, boolean dictionaryAllowed) throws IOException {
      if (depth <= 32 && at < signature.length()) {
         char c = signature.charAt(at++);
         if ("ybnqiuxtdsoghv".indexOf(c) >= 0) {
            return at;
         }

         if (c == 'a') {
            return typeEnd(signature, at, depth + 1, true);
         }

         if (c == '(' || c == '{' && dictionaryAllowed) {
            int start = at;
            int count = 0;

            for (char close = (char)(c == '(' ? 41 : 125); at < signature.length() && signature.charAt(at) != close; count++) {
               at = typeEnd(signature, at, depth + 1, false);
            }

            if (at < signature.length() && at != start && (c != '{' || count == 2 && "ybnqiuxtdsogh".indexOf(signature.charAt(start)) >= 0)) {
               return at + 1;
            } else {
               throw invalid("Container signature");
            }
         } else {
            throw invalid("Unsupported signature type");
         }
      } else {
         throw invalid("Signature nesting");
      }
   }

   private static int alignment(String type) throws IOException {
      return switch (type.charAt(0)) {
         case '(', 'd', 't', 'x', '{' -> 8;
         case 'a', 'b', 'h', 'i', 'o', 's', 'u' -> 4;
         case 'g', 'v', 'y' -> 1;
         case 'n', 'q' -> 2;
         default -> throw invalid("Type alignment");
      };
   }

   private static IOException invalid(String detail) {
      return new IOException("Invalid D-Bus message: " + detail);
   }

   record Layout(ByteOrder order, int type, int fieldsBytes, int bodyBytes) {
      int paddedFieldsBytes() {
         return this.fieldsBytes + 7 & -8;
      }
   }

   record Message(int type, long replySerial, Map<Integer, DbusMessageCodec.Variant> headers, List<Object> arguments) {
   }

   private static final class Reader {
      private final byte[] bytes;
      private final ByteBuffer buffer;
      int position;

      Reader(byte[] bytes, ByteOrder order) {
         this.bytes = bytes;
         this.buffer = ByteBuffer.wrap(bytes).order(order);
      }

      void require(int count) throws IOException {
         if (count < 0 || count > this.bytes.length - this.position) {
            throw DbusMessageCodec.invalid("Truncated value");
         }
      }

      void align(int alignment) throws IOException {
         int next = this.position + alignment - 1 & -alignment;
         this.require(next - this.position);

         while (this.position < next) {
            if (this.bytes[this.position++] != 0) {
               throw DbusMessageCodec.invalid("Nonzero padding");
            }
         }
      }

      int byteValue() throws IOException {
         this.require(1);
         return this.bytes[this.position++] & 0xFF;
      }

      long unsignedInt() throws IOException {
         this.require(4);
         long n = Integer.toUnsignedLong(this.buffer.getInt(this.position));
         this.position += 4;
         return n;
      }

      int size() throws IOException {
         long n = this.unsignedInt();
         if (n > 16777216L) {
            throw DbusMessageCodec.invalid("Value too large");
         } else {
            return (int)n;
         }
      }

      String text(boolean signature) throws IOException {
         int count = signature ? this.byteValue() : this.size();
         this.require(count + 1);

         String text;
         try {
            text = StandardCharsets.UTF_8
               .newDecoder()
               .onMalformedInput(CodingErrorAction.REPORT)
               .onUnmappableCharacter(CodingErrorAction.REPORT)
               .decode(ByteBuffer.wrap(this.bytes, this.position, count))
               .toString();
         } catch (CharacterCodingException malformed) {
            throw DbusMessageCodec.invalid("Invalid UTF-8");
         }

         this.position += count;
         if (this.byteValue() == 0 && text.indexOf(0) < 0) {
            if (signature) {
               DbusMessageCodec.types(text);
            }

            return text;
         } else {
            throw DbusMessageCodec.invalid("String terminator");
         }
      }

      DbusMessageCodec.Variant variant(int depth) throws IOException {
         String signature = this.text(true);
         if (DbusMessageCodec.types(signature).size() != 1) {
            throw DbusMessageCodec.invalid("Variant must contain one type");
         } else {
            return new DbusMessageCodec.Variant(signature, this.value(signature, depth + 1));
         }
      }

      Object value(String type, int depth) throws IOException {
         if (depth > 64) {
            throw DbusMessageCodec.invalid("Value nesting");
         }

         this.align(DbusMessageCodec.alignment(type));

         return switch (type.charAt(0)) {
            case '(', '{' -> {
               List<Object> members = new ArrayList<>();

               for (String member : DbusMessageCodec.types(type.substring(1, type.length() - 1))) {
                  members.add(this.value(member, depth + 1));
               }

               yield List.copyOf(members);
            }
            case 'a' -> {
               int length = this.size();
               String element = type.substring(1);
               this.align(DbusMessageCodec.alignment(element));
               int end = this.position + length;
               if (end < this.position || end > this.bytes.length) {
                  throw DbusMessageCodec.invalid("Array length");
               }

               List<Object> values = new ArrayList<>();

               while (this.position < end) {
                  values.add(this.value(element, depth + 1));
                  if (this.position > end) {
                     throw DbusMessageCodec.invalid("Array element length");
                  }
               }

               if (element.charAt(0) != '{') {
                  yield List.copyOf(values);
               } else {
                  LinkedHashMap<Object, Object> dictionary = new LinkedHashMap<>();

                  for (Object value : values) {
                     List<?> pair = (List<?>)value;
                     if (dictionary.putIfAbsent(pair.get(0), pair.get(1)) != null) {
                        throw DbusMessageCodec.invalid("Duplicate dictionary key");
                     }
                  }

                  yield Collections.unmodifiableMap(dictionary);
               }
            }
            case 'b' -> {
               long n = this.unsignedInt();
               if (n != 0L && n != 1L) {
                  throw DbusMessageCodec.invalid("Boolean value");
               }

               yield n == 1L;
            }
            case 'd' -> {
               this.require(8);
               double n = this.buffer.getDouble(this.position);
               this.position += 8;
               yield n;
            }
            case 'g' -> this.text(true);
            case 'h' -> throw DbusMessageCodec.invalid("Unix FD type not negotiated");
            case 'i' -> {
               this.require(4);
               int n = this.buffer.getInt(this.position);
               this.position += 4;
               yield n;
            }
            case 'n' -> {
               this.require(2);
               short n = this.buffer.getShort(this.position);
               this.position += 2;
               yield n;
            }
            case 'o', 's' -> this.text(false);
            case 'q' -> {
               this.require(2);
               int n = Short.toUnsignedInt(this.buffer.getShort(this.position));
               this.position += 2;
               yield n;
            }
            case 't', 'x' -> {
               this.require(8);
               long n = this.buffer.getLong(this.position);
               this.position += 8;
               yield n;
            }
            case 'u' -> this.unsignedInt();
            case 'v' -> this.variant(depth);
            case 'y' -> this.byteValue();
            default -> throw DbusMessageCodec.invalid("Value type");
         };
      }
   }

   record Variant(String signature, Object value) {
   }

   private static final class Writer {
      final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      void align(int alignment) {
         while ((this.bytes.size() & alignment - 1) != 0) {
            this.bytes.write(0);
         }
      }

      void integer(int value) {
         this.align(4);

         for (int shift = 0; shift < 32; shift += 8) {
            this.bytes.write(value >>> shift);
         }
      }

      void string(String value) throws IOException {
         if (value != null && value.indexOf(0) < 0) {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            this.integer(utf8.length);
            this.bytes.writeBytes(utf8);
            this.bytes.write(0);
         } else {
            throw DbusMessageCodec.invalid("Outgoing string");
         }
      }

      void signature(String value) throws IOException {
         DbusMessageCodec.types(value);
         this.bytes.write(value.length());
         this.bytes.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
         this.bytes.write(0);
      }

      void field(int code, String type, String value) throws IOException {
         this.align(8);
         this.bytes.write(code);
         this.signature(type);
         if (type.equals("g")) {
            this.signature(value);
         } else {
            this.string(value);
         }
      }
   }
}
