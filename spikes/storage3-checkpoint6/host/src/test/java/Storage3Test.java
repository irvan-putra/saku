import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import org.linguafranca.pwdb.kdbx.jackson.KdbxDatabase;
import org.linguafranca.pwdb.format.KdbxCredentials;
public class Storage3Test {
  private KdbxCredentials creds() { return new KdbxCredentials("SYNTHETIC-master".getBytes()); }
  private byte[] fixture() throws Exception {
    try (InputStream i=getClass().getResourceAsStream("/saku-pykeepass-roundtrip.kdbx")) { return i.readAllBytes(); }
  }
  @Test(timeout=15000) public void wrongKeyTamperAndTruncationRejected() throws Exception {
    byte[] good=fixture();
    boolean rejected=false;
    try { KdbxDatabase.load(new KdbxCredentials("SYNTHETIC-wrong".getBytes()),new ByteArrayInputStream(good)); }
    catch(Exception expected) { rejected=true; }
    assertTrue("wrong key",rejected);
    for(int n:new int[]{0,1,8,12,32,64,good.length/2,good.length-1}) {
      rejected=false;
      try { KdbxDatabase.load(creds(),new ByteArrayInputStream(java.util.Arrays.copyOf(good,n))); }
      catch(Exception expected) { rejected=true; }
      assertTrue("truncated at "+n,rejected);
    }
    for(int offset:new int[]{0,4,12,good.length/2,good.length-1}) {
      byte[] changed=good.clone(); changed[offset]^=1;
      rejected=false;
      try { KdbxDatabase.load(creds(),new ByteArrayInputStream(changed)); }
      catch(Exception expected) { rejected=true; }
      assertTrue("tamper at "+offset,rejected);
    }
  }
  @Test(timeout=15000) public void shortMalformedHeadersRejected() throws Exception {
    for(int n=0;n<100;n++) {
      byte[] x=new byte[16+n];new java.util.Random(n).nextBytes(x);
      boolean rejected=false;
      try { KdbxDatabase.load(creds(),new ByteArrayInputStream(x)); }
      catch(Exception expected) { rejected=true; }
      assertTrue("malformed seed "+n,rejected);
    }
  }
  @Test public void readsBothInteropFilesAndWrites() throws Exception {
    for (String p : new String[]{"/saku-synthetic-2.2.6.kdbx","/saku-pykeepass-roundtrip.kdbx"}) {
      KdbxDatabase db = KdbxDatabase.load(creds(), getClass().getResourceAsStream(p));
      assertEquals("SYNTHETIC-spike",db.findEntries("example.test").get(0).getPassword());
      if(p.contains("pykeepass")) assertEquals("SYNTHETIC-from-python",db.findEntries("python-roundtrip.test").get(0).getPassword());
      db.save(creds(),new FileOutputStream("build/saku-storage3-roundtrip.kdbx"));
    }
  }
}
