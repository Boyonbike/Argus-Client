package dev.argus.client.config.importing;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Content identity, independent of cached registry entries or coarse filesystem timestamps. */
public final class PresetFileVersion {
    private PresetFileVersion() {}
    public static String capture(Path file) {
        try {
            if(!Files.isRegularFile(file) || Files.size(file)>ExternalConfigReader.MAX_FILE)return null;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=ExternalConfigReader.bounded(Files.newInputStream(file),ExternalConfigReader.MAX_FILE)) {
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){return null;}
    }
}
