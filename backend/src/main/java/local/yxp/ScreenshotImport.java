package local.yxp;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

final class ScreenshotImport {
    static JsonObject recognize(byte[] bytes,String mode) throws Exception {
        java.awt.image.BufferedImage decoded;
        try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
            var readers=javax.imageio.ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new Engine.Rejected("INVALID_INPUT","无法读取图片，请选择PNG或JPG截图");
            var reader=readers.next();try{reader.setInput(stream);if(reader.getWidth(0)>6000||reader.getHeight(0)>6000)throw new Engine.Rejected("INVALID_INPUT","截图边长不能超过6000像素");decoded=reader.read(0);}finally{reader.dispose();}
        }
        if(decoded==null||decoded.getWidth()>6000||decoded.getHeight()>6000)throw new Engine.Rejected("INVALID_INPUT","请选择不超过6000像素的PNG或JPG截图");
        Path input=Files.createTempFile("yxp-import-",".png"),output=Files.createTempFile("yxp-ocr-",".json");
        try{
            javax.imageio.ImageIO.write(decoded,"png",input.toFile());
            String python=System.getenv("YXP_PYTHON");if(python==null||python.isBlank())python=Path.of(System.getenv().getOrDefault("USERPROFILE",System.getProperty("user.home")),".cache","codex-runtimes","codex-primary-runtime","dependencies","python","python.exe").toString();
            if(!Files.isRegularFile(Path.of(python)))throw new Engine.Rejected("OCR_FAILED","未找到本地Python，请通过YXP_PYTHON设置Python可执行文件路径");
            var process=new ProcessBuilder(python,Path.of("scripts/recognize-screenshot.py").toAbsolutePath().toString(),input.toString(),mode).redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(90,TimeUnit.SECONDS)){process.descendants().forEach(child->child.destroyForcibly());process.destroyForcibly();throw new Engine.Rejected("OCR_FAILED","本地文字识别超时，请裁剪后重试");}
            if(process.exitValue()!=0)throw new Engine.Rejected("OCR_FAILED","本地识别失败，请检查Windows简体中文OCR、Python的Pillow/numpy及本地识别模板");
            return JsonParser.parseString(Files.readString(output,StandardCharsets.UTF_8).replace("\ufeff","")).getAsJsonObject();
        }finally{Files.deleteIfExists(input);Files.deleteIfExists(output);}
    }
}


