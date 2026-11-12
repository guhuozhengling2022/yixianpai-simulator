package local.yxp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScreenshotImportTest {
 @Test void rejectsNonImageBeforeOcr(){
  var error=assertThrows(Engine.Rejected.class,()->ScreenshotImport.recognize("not an image".getBytes(),"record"));
  assertEquals("INVALID_INPUT",error.status);
 }
 @Test void rejectsOversizedImageBeforeOcr() throws Exception {
  var bytes=new java.io.ByteArrayOutputStream();
  javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(6001,1,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  var error=assertThrows(Engine.Rejected.class,()->ScreenshotImport.recognize(bytes.toByteArray(),"game"));
  assertEquals("INVALID_INPUT",error.status);
 }
}
