package dev.argus.client.media;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.ByValue;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class MacJnaRuntime implements MacApplicationMediaSource.RuntimeApi {
   private static final String FOUNDATION = "/System/Library/Frameworks/Foundation.framework/Foundation";
   private static final String APPKIT = "/System/Library/Frameworks/AppKit.framework/AppKit";
   private static final String APPLE_EVENTS = "/System/Library/Frameworks/CoreServices.framework/Frameworks/AE.framework/AE";
   private final NativeLibrary foundation;
   private final NativeLibrary appkit;
   private final NativeLibrary appleEvents;
   private final NativeLibrary objc;
   private final Function objcClass;
   private final Function selector;
   private final Function message;
   private final Function permission;
   private final Thread owner = Thread.currentThread();
   private boolean scopeActive;

   MacJnaRuntime() throws IOException {
      if (Native.POINTER_SIZE != 8) {
         throw new IOException("Unsupported Cocoa pointer ABI");
      }

      this.foundation = NativeLibrary.getInstance("/System/Library/Frameworks/Foundation.framework/Foundation");
      this.appkit = NativeLibrary.getInstance("/System/Library/Frameworks/AppKit.framework/AppKit");
      this.appleEvents = NativeLibrary.getInstance("/System/Library/Frameworks/CoreServices.framework/Frameworks/AE.framework/AE");
      this.objc = NativeLibrary.getInstance("objc", Map.of("string-encoding", StandardCharsets.UTF_8.name()));
      this.objcClass = this.objc.getFunction("objc_getClass");
      this.selector = this.objc.getFunction("sel_registerName");
      this.message = this.objc.getFunction("objc_msgSend");
      this.permission = this.appleEvents.getFunction("AEDeterminePermissionToAutomateTarget");
   }

   @Override
   public MacApplicationMediaSource.Scope begin() throws IOException {
      this.requireOwner();
      if (this.scopeActive) {
         throw new IOException("Nested Cocoa media read scope");
      }

      Pointer pool = required(this.pointer(this.type("NSAutoreleasePool"), "new"), "autorelease pool");
      this.scopeActive = true;
      AtomicBoolean drained = new AtomicBoolean();
      return () -> {
         this.requireOwner();
         if (drained.compareAndSet(false, true)) {
            try {
               this.voidMessage(pool, "drain");
            } finally {
               this.scopeActive = false;
            }
         }
      };
   }

   private void requireOwner() throws IOException {
      if (Thread.currentThread() != this.owner) {
         throw new IOException("Cocoa media objects are worker-owned");
      }
   }

   private void requireScope() throws IOException {
      this.requireOwner();
      if (!this.scopeActive) {
         throw new IOException("Cocoa media read scope absent");
      }

      if (Thread.currentThread().isInterrupted()) {
         throw new IOException("Cocoa media read interrupted");
      }
   }

   @Override
   public List<MacApplicationMediaSource.Application> alreadyRunning() throws IOException {
      this.requireScope();
      List<MacApplicationMediaSource.Application> result = new ArrayList<>();

      for (String id : MacApplicationMediaSource.SUPPORTED_APPLICATIONS) {
         Pointer apps = required(
            this.pointer(this.type("NSRunningApplication"), "runningApplicationsWithBundleIdentifier:", this.nsString(id)), "running app list"
         );
         long count = this.longMessage(apps, "count");
         if (count < 0L || count > 256L) {
            throw new IOException("Unexpected supported-app process count");
         }

         for (long index = 0L; index < count; index++) {
            Pointer app = required(this.pointer(apps, "objectAtIndex:", index), "running app");
            if (!this.boolMessage(app, "isTerminated") && this.boolMessage(app, "isFinishedLaunching")) {
               int pid = this.intMessage(app, "processIdentifier");
               if (pid > 0 && id.equals(this.text(this.pointer(app, "bundleIdentifier")))) {
                  Pointer bundle = required(
                     this.pointer(this.type("NSBundle"), "bundleWithURL:", required(this.pointer(app, "bundleURL"), "app bundle URL")), "app bundle"
                  );
                  String resources = this.text(this.pointer(bundle, "resourcePath"));
                  String definition = this.text(this.pointer(bundle, "objectForInfoDictionaryKey:", this.nsString("OSAScriptingDefinition")));
                  result.add(new MacApplicationMediaSource.Application(pid, id, resources, definition));
               }
            }
         }
      }

      return List.copyOf(result);
   }

   @Override
   public byte[] dictionary(MacApplicationMediaSource.Application application) throws IOException {
      this.requireScope();
      this.checkPid(application);
      return MacScriptingDictionary.readVendorFile(application.resourceDirectory(), application.dictionaryFile());
   }

   @Override
   public int permissionForGetData(MacApplicationMediaSource.Application application, boolean askUserIfNeeded) throws IOException {
      this.requireScope();
      if (askUserIfNeeded) {
         throw new IOException("Media provider never requests Automation consent");
      }

      this.checkPid(application);
      Pointer target = this.pidDescriptor(application.pid());
      Pointer address = required(this.pointer(target, "aeDesc"), "PID address descriptor");
      return this.permission.invokeInt(new Object[]{address, code("core"), code("getd"), Byte.valueOf((byte)0)});
   }

   @Override
   public MacApplicationMediaSource.Session openPidTarget(MacApplicationMediaSource.Application application, int sendMode, long replyTimeoutTicks) throws IOException {
      this.requireScope();
      this.checkPid(application);
      if (sendMode == 131219 && replyTimeoutTicks == 120L) {
         final Pointer target = this.pidDescriptor(application.pid());
         return new MacApplicationMediaSource.Session() {
            private Pointer query(List<Integer> propertyPath) throws IOException {
               MacJnaRuntime.this.requireScope();
               MacJnaRuntime.this.checkPid(application);
               MacGetDataRequest request = MacGetDataRequest.create(application.pid(), propertyPath, sendMode, replyTimeoutTicks);
               Pointer container = MacJnaRuntime.required(
                  MacJnaRuntime.this.pointer(MacJnaRuntime.this.type("NSAppleEventDescriptor"), "nullDescriptor"), "null container"
               );

               for (int property : request.propertyPath()) {
                  Pointer record = MacJnaRuntime.required(
                     MacJnaRuntime.this.pointer(MacJnaRuntime.this.type("NSAppleEventDescriptor"), "recordDescriptor"), "property specifier record"
                  );
                  MacJnaRuntime.this.voidMessage(
                     record, "setDescriptor:forKeyword:", MacJnaRuntime.this.typeDescriptor(MacJnaRuntime.code("prop")), MacJnaRuntime.code("want")
                  );
                  MacJnaRuntime.this.voidMessage(record, "setDescriptor:forKeyword:", container, MacJnaRuntime.code("from"));
                  MacJnaRuntime.this.voidMessage(
                     record, "setDescriptor:forKeyword:", MacJnaRuntime.this.enumDescriptor(MacJnaRuntime.code("prop")), MacJnaRuntime.code("form")
                  );
                  MacJnaRuntime.this.voidMessage(record, "setDescriptor:forKeyword:", MacJnaRuntime.this.typeDescriptor(property), MacJnaRuntime.code("seld"));
                  container = MacJnaRuntime.required(
                     MacJnaRuntime.this.pointer(record, "coerceToDescriptorType:", MacJnaRuntime.code("obj ")), "property specifier"
                  );
               }

               Pointer event = MacJnaRuntime.required(
                  MacJnaRuntime.this.pointer(
                     MacJnaRuntime.this.type("NSAppleEventDescriptor"),
                     "appleEventWithEventClass:eventID:targetDescriptor:returnID:transactionID:",
                     request.eventClass(),
                     request.eventId(),
                     target,
                     (short)-1,
                     0
                  ),
                  "get-data event"
               );
               MacJnaRuntime.this.voidMessage(event, "setParamDescriptor:forKeyword:", container, MacJnaRuntime.code("----"));
               PointerByReference error = new PointerByReference();
               Pointer reply = MacJnaRuntime.this.pointer(
                  event, "sendEventWithOptions:timeout:error:", (long)request.sendMode(), request.timeoutSeconds(), error
               );
               if (error.getValue() != null) {
                  throw new IOException(
                     "Mac media get-data unavailable: " + MacJnaRuntime.this.text(MacJnaRuntime.this.pointer(error.getValue(), "localizedDescription"))
                  );
               } else {
                  MacJnaRuntime.required(reply, "get-data reply");
                  Pointer errorNumber = MacJnaRuntime.this.pointer(reply, "paramDescriptorForKeyword:", MacJnaRuntime.code("errn"));
                  if (errorNumber != null && MacJnaRuntime.this.intMessage(errorNumber, "int32Value") != 0) {
                     throw new IOException("Mac media app rejected get-data: " + MacJnaRuntime.this.intMessage(errorNumber, "int32Value"));
                  } else {
                     return MacJnaRuntime.required(
                        MacJnaRuntime.this.pointer(reply, "paramDescriptorForKeyword:", MacJnaRuntime.code("----")), "get-data value"
                     );
                  }
               }
            }

            @Override
            public int enumValue(int property) throws IOException {
               Pointer value = this.query(List.of(property));
               if (MacJnaRuntime.this.intMessage(value, "descriptorType") != MacJnaRuntime.code("enum")) {
                  throw new IOException("Vendor player state is not an enumeration");
               } else {
                  return MacJnaRuntime.this.intMessage(value, "enumCodeValue");
               }
            }

            @Override
            public String text(List<Integer> propertyPath) throws IOException {
               Pointer value = this.query(propertyPath);
               int descriptorType = MacJnaRuntime.this.intMessage(value, "descriptorType");
               if (!List.of(
                     MacJnaRuntime.code("TEXT"), MacJnaRuntime.code("cstr"), MacJnaRuntime.code("utxt"), MacJnaRuntime.code("ut16"), MacJnaRuntime.code("utf8")
                  )
                  .contains(descriptorType)) {
                  throw new IOException("Vendor media property is not text");
               } else {
                  return MacJnaRuntime.this.text(MacJnaRuntime.required(MacJnaRuntime.this.pointer(value, "stringValue"), "media string"));
               }
            }

            @Override
            public Double number(List<Integer> propertyPath) throws IOException {
               Pointer value = this.query(propertyPath);
               int descriptorType = MacJnaRuntime.this.intMessage(value, "descriptorType");
               if (!List.of(
                     MacJnaRuntime.code("shor"),
                     MacJnaRuntime.code("long"),
                     MacJnaRuntime.code("magn"),
                     MacJnaRuntime.code("comp"),
                     MacJnaRuntime.code("ucom"),
                     MacJnaRuntime.code("sing"),
                     MacJnaRuntime.code("doub")
                  )
                  .contains(descriptorType)) {
                  throw new IOException("Vendor media property is not numeric");
               } else {
                  return MacJnaRuntime.this.doubleMessage(value, "doubleValue");
               }
            }
         };
      } else {
         throw new IOException("Unreviewed Apple event options");
      }
   }

   private void checkPid(MacApplicationMediaSource.Application application) throws IOException {
      if (MacApplicationMediaSource.SUPPORTED_APPLICATIONS.contains(application.bundleId()) && application.pid() > 0) {
         Pointer app = required(
            this.pointer(this.type("NSRunningApplication"), "runningApplicationWithProcessIdentifier:", application.pid()), "current PID app"
         );
         if (this.boolMessage(app, "isTerminated")
            || !this.boolMessage(app, "isFinishedLaunching")
            || !application.bundleId().equals(this.text(this.pointer(app, "bundleIdentifier")))) {
            throw new IOException("Media app is no longer the same running PID target");
         }
      } else {
         throw new IOException("Unsupported target app");
      }
   }

   private Pointer pidDescriptor(int pid) throws IOException {
      return required(this.pointer(this.type("NSAppleEventDescriptor"), "descriptorWithProcessIdentifier:", pid), "PID target");
   }

   private Pointer typeDescriptor(int code) throws IOException {
      return required(this.pointer(this.type("NSAppleEventDescriptor"), "descriptorWithTypeCode:", code), "type descriptor");
   }

   private Pointer enumDescriptor(int code) throws IOException {
      return required(this.pointer(this.type("NSAppleEventDescriptor"), "descriptorWithEnumCode:", code), "enum descriptor");
   }

   private Pointer type(String name) throws IOException {
      return required(this.objcClass.invokePointer(new Object[]{name}), "Cocoa class " + name);
   }

   private Pointer nsString(String text) throws IOException {
      return required(this.pointer(this.type("NSString"), "stringWithUTF8String:", text), "NSString");
   }

   private static Pointer required(Pointer pointer, String name) throws IOException {
      if (pointer != null && Pointer.nativeValue(pointer) != 0L) {
         return pointer;
      } else {
         throw new IOException("Unavailable " + name);
      }
   }

   private Object[] arguments(Pointer receiver, String name, Object... arguments) {
      Object[] all = new Object[arguments.length + 2];
      all[0] = receiver;
      all[1] = this.selector.invokePointer(new Object[]{name});
      System.arraycopy(arguments, 0, all, 2, arguments.length);
      return all;
   }

   private Pointer pointer(Pointer receiver, String name, Object... arguments) {
      return this.message.invokePointer(this.arguments(receiver, name, arguments));
   }

   private int intMessage(Pointer receiver, String name, Object... arguments) {
      return this.message.invokeInt(this.arguments(receiver, name, arguments));
   }

   private long longMessage(Pointer receiver, String name, Object... arguments) {
      return this.message.invokeLong(this.arguments(receiver, name, arguments));
   }

   private double doubleMessage(Pointer receiver, String name, Object... arguments) {
      return this.message.invokeDouble(this.arguments(receiver, name, arguments));
   }

   private boolean boolMessage(Pointer receiver, String name) {
      return ((Number)this.message.invoke(Byte.class, this.arguments(receiver, name))).byteValue() != 0;
   }

   private void voidMessage(Pointer receiver, String name, Object... arguments) {
      this.message.invokeVoid(this.arguments(receiver, name, arguments));
   }

   private static int code(String value) throws IOException {
      return MacScriptingDictionary.code(value);
   }

   private String text(Pointer string) throws IOException {
      if (string == null) {
         return "";
      }

      if (!this.boolMessageWithClass(string, "NSString")) {
         throw new IOException("Unexpected Cocoa string class");
      }

      long length = this.longMessage(string, "length");
      if (length < 0L || length > 1048576L) {
         throw new IOException("Cocoa string exceeds bounded size");
      }

      if (length == 0L) {
         return "";
      }

      Memory units = new Memory(length * 2L);

      String var9;
      try {
         this.voidMessage(string, "getCharacters:range:", units, new MacJnaRuntime.Range(0L, length));
         char[] characters = new char[(int)length];

         for (int n = 0; n < characters.length; n++) {
            characters[n] = (char)(units.getShort(n * 2L) & 65535);
         }

         var9 = new String(characters);
      } catch (Throwable var8) {
         try {
            units.close();
         } catch (Throwable var7) {
            var8.addSuppressed(var7);
         }

         throw var8;
      }

      units.close();
      return var9;
   }

   private boolean boolMessageWithClass(Pointer object, String name) throws IOException {
      return ((Number)this.message.invoke(Byte.class, this.arguments(object, "isKindOfClass:", this.type(name)))).byteValue() != 0;
   }

   @FieldOrder({"location", "length"})
   public static final class Range extends Structure implements ByValue {
      public long location;
      public long length;

      public Range() {
      }

      Range(long location, long length) {
         this.location = location;
         this.length = length;
      }
   }
}
