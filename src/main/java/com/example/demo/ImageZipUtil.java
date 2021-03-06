package com.example.demo;


import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 图片压缩
 */
@Slf4j
public class ImageZipUtil {

   /* public static void main(String[] args) throws Exception {
        File oldFile = new File("F:\\1\\20210302134405.jpg");
        String newFilePath = "F:\\1\\20210302134405_1.jpg";
        BufferedImage bufferedImg = ImageIO.read(oldFile);
        int width = (int) (bufferedImg.getWidth() * 0.6f);
        int height = (int) (bufferedImg.getHeight() * 0.6f);
        String imagpath = zipWidthHeightImageFile(oldFile, newFilePath, width, height, 0.7f);
        System.out.println("==imagpath=" + imagpath);
    }*/

    /**
     * 按设置的宽度高度压缩图片文件<br> 先保存原文件，再压缩、上传
     *
     * @param oldFile 要进行压缩的文件全路径
     * @param newFilePath 新文件存放路径
     * @param width   宽度
     * @param height  高度
     * @param quality 质量
     * @return 返回压缩后的文件的全路径
     */
    public static String zipWidthHeightImageFile(File oldFile, String newFilePath, int width, int height, float quality) {
        if (oldFile == null) {
            return null;
        }

        Image srcFile = null;
        BufferedImage buffImg = null;

        try {
            /** 对服务器上的临时文件进行处理 */
            srcFile = ImageIO.read(oldFile);

            log.info("outputNewImage:".concat(newFilePath));
            String subfix = "jpg";
            subfix = newFilePath.substring(newFilePath.lastIndexOf(".") + 1, newFilePath.length());
            if (subfix.equals("png")) {
                buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            } else {
                buffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
            Graphics2D graphics = buffImg.createGraphics();
            graphics.setBackground(new Color(255, 255, 255));
            graphics.setColor(new Color(255, 255, 255));
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(srcFile.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
            File newFile = new File(newFilePath);
            ImageIO.write(buffImg, subfix, newFile);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (srcFile != null) {
                srcFile.flush();
            }
            if (buffImg != null) {
                buffImg.flush();
            }
        }
        return newFilePath;
    }
}

