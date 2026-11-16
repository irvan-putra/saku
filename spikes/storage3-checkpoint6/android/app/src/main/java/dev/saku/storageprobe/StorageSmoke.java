package dev.saku.storageprobe;
import java.io.*;
import org.linguafranca.pwdb.kdbx.jackson.KdbxDatabase;
import org.linguafranca.pwdb.format.KdbxCredentials;
/** Compilation/DEX probe only. NOT EXECUTED on Android. */
public final class StorageSmoke {
 public static byte[] syntheticRoundtrip() throws Exception {
   KdbxCredentials c=new KdbxCredentials("SYNTHETIC-only".getBytes("UTF-8"));
   KdbxDatabase db=new KdbxDatabase();
   ByteArrayOutputStream out=new ByteArrayOutputStream();db.save(c,out);
   KdbxDatabase.load(c,new ByteArrayInputStream(out.toByteArray()));return out.toByteArray();
 }
}
