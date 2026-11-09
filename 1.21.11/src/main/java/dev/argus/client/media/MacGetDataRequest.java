package dev.argus.client.media;

import java.io.IOException;
import java.util.List;

record MacGetDataRequest(int eventClass, int eventId, List<Integer> propertyPath, int sendMode, double timeoutSeconds) {
   static MacGetDataRequest create(int pid, List<Integer> path, int mode, long timeoutTicks) throws IOException {
      if (pid <= 0 || path == null || path.isEmpty() || path.size() > 2 || path.stream().anyMatch(v -> v == null)) {
         throw new IOException("Unsupported PID property request");
      } else if (mode == 131219 && timeoutTicks == 120L) {
         return new MacGetDataRequest(MacScriptingDictionary.code("core"), MacScriptingDictionary.code("getd"), List.copyOf(path), mode, timeoutTicks / 60.0);
      } else {
         throw new IOException("Unreviewed Apple event options");
      }
   }
}
