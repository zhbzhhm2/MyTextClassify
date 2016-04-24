package MyTextClassify;

import java.io.*;
/**
 * Created by zhang on 16-4-8.
 */
public class Tools {
    public static String getEncode(String fileName){

        BufferedInputStream bin = null;
        int p = 0;
        try {
            bin = new BufferedInputStream(
                    new FileInputStream(fileName));
            p = (bin.read() << 8) + bin.read();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String code = null;

        switch (p) {
            case 0xefbb:
                code = "UTF-8";
                break;
            case 0xfffe:
                code = "Unicode";
                break;
            case 0xfeff:
                code = "UTF-16BE";
                break;
            default:
                code = "GBK";
        }

        return code;
    }
}
