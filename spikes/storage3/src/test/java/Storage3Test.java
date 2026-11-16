import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import org.linguafranca.pwdb.kdbx.jackson.KdbxDatabase;
import org.linguafranca.pwdb.format.KdbxCredentials;
public class Storage3Test {
  private KdbxCredentials creds() { return new KdbxCredentials("SYNTHETIC-master".getBytes()); }
  @Test public void readsBothInteropFilesAndWrites() throws Exception {
    for (String p : new String[]{"/saku-synthetic-2.2.6.kdbx","/saku-pykeepass-roundtrip.kdbx"}) {
      KdbxDatabase db = KdbxDatabase.load(creds(), getClass().getResourceAsStream(p));
      assertEquals("SYNTHETIC-spike",db.findEntries("example.test").get(0).getPassword());
      if(p.contains("pykeepass")) assertEquals("SYNTHETIC-from-python",db.findEntries("python-roundtrip.test").get(0).getPassword());
      db.save(creds(),new FileOutputStream("build/saku-storage3-roundtrip.kdbx"));
    }
  }
}
