package com.example.demo;

/***
 * Desc: 比较两张图的相似度
 * @return {@link null}
 * @author 紫夜雪蓝
 * @date 7/30/21 5:44 PM
 */

import javax.imageio.*;
import java.awt.image.*;
import java.awt.*;//Color
import java.io.*;
import java.util.concurrent.Callable;

public class PhotoDigest implements Callable<Float> {

    private static Float value;

    public PhotoDigest(Float value) {
        this.value = value;
    }

    @Override
    public Float call() throws Exception {
        // 返回当前值
        return this.value;
    }

    public static int[] getData(String name) {
        try {
            BufferedImage img = ImageIO.read(new File(name));
            BufferedImage slt = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            slt.getGraphics().drawImage(img, 0, 0, 100, 100, null);
            // ImageIO.write(slt,"jpeg",new File("slt.jpg"));
            int[] data = new int[256];
            for (int x = 0; x < slt.getWidth(); x++) {
                for (int y = 0; y < slt.getHeight(); y++) {
                    int rgb = slt.getRGB(x, y);
                    Color myColor = new Color(rgb);
                    int r = myColor.getRed();
                    int g = myColor.getGreen();
                    int b = myColor.getBlue();
                    data[(r + g + b) / 3]++;
                }
            }
            // data 就是所谓图形学当中的直方图的概念
            return data;
        } catch (Exception exception) {
            System.out.println("有文件没有找到,请检查文件是否存在或路径是否正确");
            return null;
        }
    }

    public static float compare(int[] s, int[] t) {
        try {
            float result = 0F;
            for (int i = 0; i < 256; i++) {
                int abs = Math.abs(s[i] - t[i]);
                int max = Math.max(s[i], t[i]);
                result += (1 - ((float) abs / (max == 0 ? 1 : max)));
            }
            return (result / 256) * 100;
        } catch (Exception exception) {
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {

        //(数据类型)(最小值+Math.random()*(最大值-最小值+1))
        for (int i = 18; i <= 21; i++) {

            float percent = compare(getData("G:/oss/pk/" + 18 + ".jpg"),
                    getData("G:/oss/pk/" + i + ".jpg"));
            if (percent == 0) {
                System.out.println("无法比较");
            } else {
                System.out.println("\t"+"G:/oss/pk/" + 18 + ".jpg"+"\t"+"与"+"\t"+"G:/oss/pk/" + i + ".jpg"+"\t"+"两张图片的相似度为：" + percent + "%");
            }
        }
    }
}