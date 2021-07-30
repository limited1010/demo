package com.example.demo;

import jdk.internal.ref.Cleaner;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.BufferOverflowException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.lang3.StringUtils;

import static com.example.demo.ImageZipUtil.zipWidthHeightImageFile;
import static com.example.demo.PhotoDigest.compare;
import static com.example.demo.PhotoDigest.getData;

/***
 * Desc:
 * @author 紫夜雪蓝
 * @date 6/27/21 11:00 PM
 * @version 1.0
 *
 * Copyright [limited1010] [name of copyright owner]
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * 免责说明:本仓库基于Apache License 2.0开源许可证下仅做个人学习目的，
 *        请遵守所在地相关法规，由该仓库下源码衍生变体造成的所有后果与原仓库所有者及贡献者无关!
 * 文档整理程序:配合Typora使用;win系列未测试，新鲜出炉!
 * 主要有两功能:1.将所有截屏图片压缩后按修改时间先后顺序生成md文件;
 *           2.把md目录所有的md文件内的http图片链接下载到本地并更新图片引用或本地图片的归档整理;
 *      说明:重要数据先备份！首次运行图片量较多的话可能比较慢，后续运行已优化在4ms内更新所有md文件图片引用;
 * 应用:1.截屏后生成的md图片文件方便保存浏览，整理后免维护;
 *   2.网页拷贝的内容随心记录，后期直接运行程序整理即可,省时省心;
 */
@Slf4j
public class ImgToMd {

    static String imgPath = "/home/mi/Pictures/img/Screenshots/"; // img path
    //    static String imgPath = "/home/mi/Pictures/img/screenshot/"; // img path
//    static String imgPath = "/home/mi/Pictures/img/image/"; // img path
    static String imgOutPath = "/home/mi/MI/md/img/";
    static String mdFilePath = "/home/mi/MI/md/";
    static String oldImgPath = "/home/mi/MI/md/oldImg/";
    static String tmpPath = "/home/mi/MI/md/tmp/";
    static String suffix; // file suffix
    //    static StringBuilder sb = new StringBuilder();
    static StringBuffer sb = new StringBuffer();
    //    static LinkedList list = new LinkedList();
    static ConcurrentLinkedDeque list = new ConcurrentLinkedDeque();
    static ConcurrentHashMap hashMap = new ConcurrentHashMap();
    static Map<Integer, List> map = new ConcurrentHashMap<Integer, List>();

    private static final Integer threadPSize = 5;


    /**
     * 按文件修改时间排序
     *
     * @param path
     * @return
     */
    static public File[] sortFileByModifyTime(String path) {

        File fileDir = new File(path);
        File[] files = fileDir.listFiles();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Long.valueOf(o1.lastModified()).compareTo(o2.lastModified());
            }
        });
        return files;
    }

    /**
     * 按文件名排序
     *
     * @param path
     * @return
     */
    static public File[] sortFileByFileName(String path) {
        File imgPath = new File(path);
        File[] imgFile = imgPath.listFiles();
        Arrays.sort(imgFile, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                int n1 = extractNumber(o1.getName());
                int n2 = extractNumber(o2.getName());
                return n1 - n2;
            }
        });
        return imgFile;
    }

    /**
     * 将所有图片压缩体积后按时间先后顺序转换成md文件
     *
     * @return
     */
    static void copyImgGenMdFile(String path) {

        String mdFile = path.substring(path.lastIndexOf("/")).substring(1);
        String mdSuffix = mdFile.substring(mdFile.lastIndexOf("."));
        String writeDir = mdFile.substring(0, mdFile.length() - mdSuffix.length());

        try {
            int i = 0;
            checkDirectory(imgPath); // check directory
            checkDirectory(imgOutPath.concat(writeDir + "/"));
//            initDirectory(imgOutPath.concat(writeDir + "/")); // 初始化目录,会清空目录，慎重启用

            File[] files = sortFileByModifyTime(imgPath);
            // 在这可以去重处理做相似度判断
            for (File fs : files) {
                suffix = null;
                suffix = fs.getName().substring(fs.getName().lastIndexOf(".") + 1, fs.getName().length()); // 判断后缀
                if (suffix.equalsIgnoreCase("jpg") || suffix.equalsIgnoreCase("png")) {
                    sb.delete(0, sb.length()); // 可以避免反复new对象
//                tempList.add(fs.getAbsoluteFile());
                    sb.append("/img_").append(i++).append(".").append(suffix);
//                copyFileByChannelTransfer(fs.getPath(), targetPath.getPath() + suffix); // 拷贝文件到指定位置
                    File oldFile = new File(imgPath.concat(fs.getName()));
                    String newFilePath = imgOutPath.concat(writeDir).concat(String.valueOf(sb));

                    imgZip(oldFile, newFilePath); // 压缩处理
                }
            }
            log.info("genMdFile!");
            writeToMdFile(writeDir, mdSuffix); // 生成md文件

        } catch (NullPointerException e) {
            log.error("not file!\t".concat(String.valueOf(e)));
        }
    }

    /**
     * 通过JAVA NIO 通道传输拷贝文件
     *
     * @param sourcePath
     * @param targetPath
     */
    public static void copyFileByChannelTransfer(String sourcePath, String targetPath) {
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            //获取通道
            inChannel = FileChannel.open(Paths.get(sourcePath), StandardOpenOption.SYNC, StandardOpenOption.READ);
            outChannel = FileChannel.open(Paths.get(targetPath), StandardOpenOption.SYNC, StandardOpenOption.WRITE,
                    StandardOpenOption.READ, StandardOpenOption.CREATE);

            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (NoSuchFileException e) {
            log.error("dont'tAgainRun!".concat(e.toString()));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //关闭流
            try {
                if (outChannel != null) {
                    outChannel.close();
                }
                if (inChannel != null) {
                    inChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 生成md文件
     *
     * @param file
     * @param suffix
     */
    public static void writeToMdFile(String file, String suffix) { // 判断文件是否存在;
        String tempPath = "/home/mi/MI/md/tmp/"; // 在这修改md生成目录
        checkDirectory(tempPath);
        String fileName = tempPath.concat(file + suffix);
        File mdFile = new File(tempPath.concat(file + suffix));
        File[] imgFile = sortFileByFileName(imgOutPath.concat(file + "/"));
        Path path = Paths.get(fileName);
        if (!mdFile.exists()) {
            mdFile.mkdirs();
        }

        try (BufferedWriter writer =
                     Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (File f : imgFile) {
                if (f.isFile()) {
                    log.info("write:".concat(f.getName()));
                    sb.delete(0, sb.length()); // 可以避免反复new对象
                    writer.write(String.valueOf(sb.append("![")
                            .append(f.getName()).append("](../img/").
                                    append(file).append("/").
                                    append(f.getName()).append(")\n")));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

      /*  //追加写模式
        try (BufferedWriter writer =
                     Files.newBufferedWriter(path,
                             StandardCharsets.UTF_8,
                             StandardOpenOption.APPEND)){
            writer.write("Hello World -字母哥!!");
        } catch (IOException e) {
            e.printStackTrace();
        }*/

    }

    /**
     * 图片压缩
     *
     * @param oldFile
     * @param newFilePath
     */
    public static void imgZip(File oldFile, String newFilePath) {

        File fs = new File(oldFile.getPath());
        if (fs.length() / 1024 <= 200) { // 当图片小于200kb则不压缩,直接拷贝过去
            copyFileByChannelTransfer(String.valueOf(oldFile), newFilePath);
            log.info("copyImgTo".concat(newFilePath));
        } else {
            BufferedImage bufferedImg = null;
            try {
                bufferedImg = ImageIO.read(oldFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int width = (int) (bufferedImg.getWidth() * 0.4f);
            int height = (int) (bufferedImg.getHeight() * 0.4f);
            String imagpath = zipWidthHeightImageFile(oldFile, newFilePath, width, height, 0.2f);
            log.info("saveImgTo:".concat(imagpath));
        }
    }

    /**
     * 按文件名排序
     *
     * @param name
     * @return
     */
    private static int extractNumber(String name) {
        int i;
        try {
            String number = name.replaceAll("[^\\d]", "");
            i = Integer.parseInt(number);
        } catch (Exception e) {
            i = 0;
        }
        return i;
    }

    // 文件清理，循环读取md文件，找出路径图片，按md路径将图片移动到temp目录，压缩图片到对应img目录，修改md文件;

    /**
     * 判断目录是否存在,没有则创建
     *
     * @param path
     */
    static void checkDirectory(String path) {
        File targetPath = new File(path);
        if (!targetPath.exists() && !targetPath.isDirectory()) { //不存在则创建
            targetPath.mkdir();
        }
    }

    /**
     * 初始化目录,注意保存目录下的文件,尽量不要用！allDataBecomeClear!!!
     */
    static void initDirectory(String path) {
        File dir = new File(path);
        File[] files = dir.listFiles();
        for (File fs : files) {
            if (fs.isFile() && fs.exists()) {
                fs.delete();
            }
        }
    }

    /**
     * 在md文件中找出所有图片路径并更新
     *
     * @param path
     */
    static public void findImgByMd(String path) {
        FileChannel fcl;
        MappedByteBuffer mbf = null;
        String upData = null;
        CharBuffer cb;
        Charset charset;

        String mdFile = path.substring(path.lastIndexOf("/")).substring(1);
        suffix = mdFile.substring(mdFile.lastIndexOf("."));
        String writeDir = mdFile.substring(0, mdFile.length() - suffix.length());

        try {

            checkDirectory(imgOutPath.concat(writeDir + "/"));
            initDirectory(tmpPath); // 清空目录下所有文件，不包含目录; 中间目录
//            initDirectory(imgOutPath.concat(writeDir + "/")); // 警告!!!生成的目录,建议不要开启，若原图片路径为空，则会清空生成目录已有图片和删除md图片引用

//            RandomAccessFile rad = new RandomAccessFile(path, "rw"); // 打开可读写操作
            fcl = FileChannel.open(Paths.get(path), StandardOpenOption.READ, StandardOpenOption.WRITE);
//            RandomAccessFile raf = new RandomAccessFile(path, "rw");
//            fcl = raf.getChannel();

            mbf = fcl.map(FileChannel.MapMode.READ_WRITE, 0, fcl.size());

//            Boolean end = true;
//            byte[] array = null;
            charset = Charset.forName("utf-8"); // 编码转换
            cb = charset.decode(mbf); // 解码后直接用这个搞事情,也可以用mbf;

            /*while (end) { // 先读了再说，边读编写要考虑编码类的问题,偷个懒;

                int limit = mbf.limit(); // 最大可读写数量
                int position = mbf.position(); // 当前位置，有读写则+1;
                if (position >= limit) { // 判断文件是否读完
                    end = false; // 表示已经读取完
                }
                if (end) {

                    int maxSize = 2048; // 读取2048字节
                    if (limit - position < maxSize) { // 如果总量-当前位置小于读取的字节数,表示没读完
                        maxSize = limit - position;
                    }
//                    array = new byte[maxSize];
//                    mbf.get(array);
//                    byte[] utf8Code =new String(array).getBytes(StandardCharsets.UTF_8);
//                    upData=new String(utf8Code,"utf-8"); // 中文产生乱码原因，某个汉字编码限定字节内没有读全，写入时只写了某个汉字编码的一半;
                }
            }*/
            upData = cb.toString();

            Map<Integer, List> mlt = getOldImgPath(upData, writeDir);
            for (int i = 0; i < mlt.size(); i++) { // 在这找出要修改的路径;
                if (!mlt.isEmpty()) {
                    upData = StringUtils.replace(upData, String.valueOf(mlt.get(i).get(0)), mlt.get(i).get(1).toString(), -1);
                }
            }
            if (!mlt.isEmpty()) {
                mbf.flip(); // 转换为写模式
                mbf = fcl.map(FileChannel.MapMode.READ_WRITE, 0, upData.getBytes(StandardCharsets.UTF_8).length);
                while (mbf.hasRemaining()) {
                    if (!(mbf.remaining() == 0)) {
                        mbf.put(upData.getBytes(StandardCharsets.UTF_8));
                    } else {
                        mbf.clear();
                    }
                }
            }

            mbf.force(); // 每次同步到磁盘,不然可能导致到
            mbf.clear();
            cb.clear();
            fcl.close();

            copyImgNotRename(tmpPath, imgOutPath.concat(writeDir + "/")); // 不重命名，根据文件名直接压缩到指定目录
            log.info(path.concat("\tsuccess!"));
        } catch (BufferOverflowException e) {
            log.error("bufferException!");
        } catch (IOException e) {
            log.error(String.valueOf(e));
        } finally {
//            releaseByteBuff(mbf);
        }
    }

    /**
     * url解码
     *
     * @param url
     * @return
     */
    static String uRlDecode(String url) {
        // 调用URL解码
        if (url.contains("%")) {
            try {
                return URLDecoder.decode(url, "utf-8"); // 默认以utf-8读取,写入也是
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        // 这里不同时检查后缀因为文件还没移动到指定目录，文件链接还没下载;
        return url;
    }


    /**
     * 找出md文件中图片链接
     *
     * @param data
     * @param writeDir
     * @return
     */
    static public Map<Integer, List> getOldImgPath(String data, String writeDir) {
        String[] str = data.split("\n");
        int i = 0;

        for (String fs : str) {
            try {
                sb.delete(0, sb.length());
//                sb.append(fs.substring(fs.indexOf("!["), fs.indexOf(")")).substring(2)).toString();
                String oldData = sb.append(fs.substring(fs.indexOf("!["), fs.indexOf(")")).substring(2)).toString();
                String oldfName = sb.substring(sb.lastIndexOf("/")).substring(1);
                String newfName = uRlDecode(oldfName); // 文件名检查

                if (sb.toString().contains("https") || sb.toString().contains("http")) {
                    // 处理链接,直接下载到指定目录
                    log.info("download:".concat(sb.toString()));
                    newfName = getImgByNet(sb.substring(sb.indexOf("("), sb.length()).substring(1)); // 下载图片
                    if (!newfName.isEmpty()) {
                        sb.replace(sb.indexOf("]("), sb.length(), "](".concat(imgOutPath + writeDir + "/").concat(newfName)); // 在这修改md文件中的图片链接,这两句不能省
                        list = new ConcurrentLinkedDeque();
                        list.add(oldData);
                        list.add(sb.toString());
                        map.put(i++, new ArrayList(list));
                    }
                } else {
//                    System.out.println("移动文件:"+sb.toString());
                    log.info("copyImgFile!".concat(newfName));
                    moveFile(newfName); //否则拷贝图片到目录
                    // 拷贝之后校验文件后缀，否则无法打开
                    newfName = renameFileByType(newfName); // 把文件名传递过去后再返回正确的文件名

//                    System.out.println(imgPath.concat(fileName));
//                    sb.replace(sb.indexOf("]("), sb.length(), "](".concat(imgPath).concat(fileName)); // 在这修改md文件中的图片链接
                    sb.replace(sb.indexOf("]("), sb.length(), "](".concat(imgOutPath + writeDir + "/").concat(newfName)); // 在这修改md文件中的图片链接
//                    System.out.println("修改后的内容:"+sb.toString());

                    list = new ConcurrentLinkedDeque();
//                    list.clear(); // 清空后整个清空
                    list.add(oldData);
                    list.add(sb.toString());
                    map.put(i++, new ArrayList(list));

//                    return ls; // 返回图片拷贝的路径
                }
            } catch (StringIndexOutOfBoundsException e) {
                // 文件已经找完
//                log.info("currentLineNotFind!");
            } catch (Exception e) {
                log.error(e.toString());
            }
        }

        return map;
    }

    /**
     * 将要读取文件头信息的文件的byte数组转换成string类型表示
     *
     * @param src 要读取文件头信息的文件的byte数组
     * @return 文件头信息
     */
    private static String bytesToHexString(byte[] src) {
        StringBuilder builder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        String hv;
        for (int i = 0; i < src.length; i++) {
            // 以十六进制（基数 16）无符号整数形式返回一个整数参数的字符串表示形式，并转换为大写
            hv = Integer.toHexString(src[i] & 0xFF).toUpperCase();
            if (hv.length() < 2) {
                builder.append(0);
            }
            builder.append(hv);
        }
        return builder.toString();
    }

    static void renameFile(String oldPath, String newPath) {
        File old = new File(oldPath);
        if (old.exists()) { // 如果旧文件名存在
            old.renameTo(new File(newPath));
            old.delete();
        }
    }

    /**
     * 根据文件类型重命名后缀,因为文件已经移动或下载，我们直接读取指定目录判断文件是否正确
     *
     * @param file
     * @return
     */
    static String renameFileByType(String file) { // 需要返回正确的文件名
        try {
//                String type = Files.probeContentType(new File(imgPath.concat(file)).toPath()); // 获取文件类型,好像判断的后缀

            FileInputStream fileInputStream = new FileInputStream(tmpPath.concat(file));
            byte[] b = new byte[4];
            fileInputStream.read(b, 0, b.length);
            String type = bytesToHexString(b);
            String suffix = file.substring(file.lastIndexOf(".")).substring(1);
            String str = null, str1;

            if (type.contains("FFD8FF")) {
                str = "jpg";
            } else if (type.contains("89504E47")) { // png
                str = "png";
            }

            if (!suffix.equals(str)) { // 改正后重命名文件;
                if (type.contains("FFD8FF")) { // jpg
                    str1 = file.replace("png", "jpg");
                    renameFile(imgPath.concat(file), imgPath.concat(str1));
                    return str1;
                }
                if (type.contains("89504E47")) { // png
                    str1 = file.replace("jpg", "png");
                    renameFile(imgPath.concat(file), imgPath.concat(str1));
                    return str1;
                }
            }
            if (file.contains(" ")) {
                str1 = file.replaceAll(" ", "");
                renameFile(imgPath.concat(file), imgPath.concat(str1));
                return str1;
            }

//            System.out.println(file+","+type); // 返回的文件类型在linux上不正确
            // 当文件名后缀与判定的后缀不符时以判定为准
        } catch (FileNotFoundException e) {
            log.error("don'tTryAgainRun!\t".concat(String.valueOf(e)));
        } catch (StringIndexOutOfBoundsException e) {
            log.error("fileTypeNotFound!\t".concat(String.valueOf(e)));
        } catch (NullPointerException e) {
            log.error("notFundSuffix!\t".concat(String.valueOf(e)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    /**
     * 下载图片
     *
     * @param path
     */
    private static String getImgByNet(String path) {
        URL url = null;
        //从网络上下载一张图片
        InputStream inputStream = null;
        OutputStream outputStream = null;
        //建立一个网络链接
        HttpURLConnection con = null;
        String fileName = path.substring(path.lastIndexOf("/")).substring(1);
        String newName = null;
        fileName = uRlDecode(fileName); // 先检查，文件还没下载;
        File localFilePath = new File(imgPath.concat(fileName));

        try {
            url = new URL(path);
            con = (HttpURLConnection) url.openConnection();
            inputStream = con.getInputStream();
            outputStream = new FileOutputStream(localFilePath);
            int n = -1;
            byte b[] = new byte[1024];
            while ((n = inputStream.read(b)) != -1) {
                outputStream.write(b, 0, n); // 文件名有空格先不管
            }
            outputStream.flush();
            newName = renameFileByType(fileName); // 在这处理文件名和类型

        } catch (UnknownHostException e) {
            log.error("currentNotNetworkOrNotFindFile!".concat(String.valueOf(e)));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return newName;
    }

    /**
     * 拷贝图片
     *
     * @param fileName
     */
    static public void moveFile(String fileName) {
        // 从imgOutPath拷贝到imgPath

        copyFileByChannelTransfer(oldImgPath.concat(fileName), tmpPath.concat(fileName)); // 拷贝文件到指定位置

    }

    /**
     * 不重命名直接压缩或拷贝目录下所有图片到指定目录
     *
     * @param sourcePath
     * @param dectPath
     */
    public static void copyImgNotRename(String sourcePath, String dectPath) {
        File[] files = sortFileByFileName(sourcePath);
        for (File fs : files) {
            if (fs.isFile() && fs.exists()) {
                imgZip(new File(String.valueOf(fs)), dectPath.concat(fs.getName())); // 压缩或拷贝目录下所有图片到指定目录
            }
        }
    }

    /**
     * 释放内存
     *
     * @param mappedBuf
     */
    static void releaseByteBuff(MappedByteBuffer mappedBuf) {
        // 如果文件读完则释放内存,调用后程序可能会中断
        if (mappedBuf == null) {
            return;
        }

        try {
            Method getCleanerMethod = mappedBuf.getClass().getMethod("cleaner", new Class[0]);
            getCleanerMethod.setAccessible(true);
            Cleaner cleaner = (Cleaner) getCleanerMethod.invoke(mappedBuf, new Object[0]);
            cleaner.clean();

        } catch (IllegalAccessException e) {
            log.error("releaseMapperByteBufferFail!\n".concat(String.valueOf(e)));
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }


    /**
     * 递归读取给定目录下所有文件，增加目录排除功能，速度可能会更快点
     *
     * @param str
     * @return
     */
    static public ArrayList findAllFileByPath(String str) {

//        list = new LinkedList(); // 不要初始化

        String[] path = str.split(",");
        File dir = new File(path[0]); // md主目录
        File[] files = dir.listFiles();

        for (File fs : files) {
            if (fs.exists()) {
                sb.delete(0, sb.length());
                for (int i = 1; i < path.length; i++) { // 存储标志位，匹配则不遍历;
                    hashMap.put(i, path[i]);
                    sb.append(fs.getName().contains(hashMap.get(i).toString())); // 因为没有匹配到，判断为空,多注意吧！
                }
                if (fs.isDirectory() && !sb.toString().contains("true")) { // 如果目录存在
                    findAllFileByPath(fs.getAbsolutePath()); // 继续递归
                }
                if (fs.isFile()) { // 如果文件存在
                    list.add(fs.getAbsolutePath());
                }
            }
        }

        return new ArrayList(list);
    }

    /**
     * 找出md文件
     *
     * @param dir
     * @return
     */
    static public List findMdFileByAll(String dir) {

        List allFile = findAllFileByPath(dir);

        List mdFile = new LinkedList();

        for (int i = 0; i < allFile.size(); i++) {
            String fs = String.valueOf(allFile.get(i));
            suffix = null;
            try {
//             suffix=fs.substring(fs.lastIndexOf(".")).substring(1);
                suffix = fs.substring(fs.lastIndexOf("."));
            } catch (StringIndexOutOfBoundsException e) {
                log.info("fileNameOrSuffixError!");
            }
            if (".md".equals(suffix)) {
                mdFile.add(allFile.get(i));
            }
        }
        return new ArrayList(mdFile);
    }

    /**
     * 从md目录中所有文件依次找出图片并更新路径
     *
     * @param path
     * @return
     */
    static public void upImgPathByAllMdFile(String path) {

        ExecutorService exc = new ThreadPoolExecutor(threadPSize, threadPSize, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>());
        SubTask subTask;
        Future<?> future;
        boolean isExit = true;

        List allMdFile = findMdFileByAll(path);
        File mdf;
        checkDirectory(oldImgPath);
        checkDirectory(tmpPath);
        Long checkSourceImg = Long.valueOf(new File(oldImgPath).list().length);
        if (!checkSourceImg.equals(0L)) {
            for (int i = 0; i < allMdFile.size(); i++) {
                mdf = new File(String.valueOf(allMdFile.get(i)));
                if (mdf.exists() && mdf.isFile()) {
                    try {
                        log.info("updateFile:".concat(mdf.toString())); // 在这里使用多线程处理
//                        findImgByMd(String.valueOf(mdf.toPath())); // 不使用线程可以打开
                        subTask = new SubTask(mdf);
                        future = exc.submit(subTask);
                    } catch (Exception e) {
                        log.error(String.valueOf(e));
                    }
                }
            }

            exc.shutdown(); // 发出中断,拒绝执行新任务，直到队列所有任务全部执行完;
            try {
                while (isExit) {
                    if (exc.isTerminated() == true) { // 判断线程池所有任务完成
                        exc.shutdownNow();
                        isExit = false;
                        break;
                    }
                    exc.awaitTermination(15, TimeUnit.SECONDS); // 等待15秒
                    // Thread.currentThread().sleep((long) (Math.random()*100));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else {
            log.warn("警告!图片目录为空,未做任何操作,请检查!!!");
            log.error("pleaseCheckSourceImgPath!");
            try {
                throw new FileNotFoundException(oldImgPath.concat("\t目录为空,请检查!\tisNull,checkPlease!"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /***
     * Desc:
     * 用多线程处理，可能会导致数据错乱，使用前先备份;
     */
    static class SubTask implements Runnable {
        // 创建10个线程;
        File mdf;
        private ImgToMd imgToMd;

        public SubTask(File mdf) {
            this.mdf = mdf;
        }

        @Override
        public void run() {

            try {
                imgToMd = new ImgToMd();

                log.info("threadName:" + Thread.currentThread().getName() +
                        "\tThreadId:" + Thread.currentThread().getId() + "\t:" + mdf.toString());
                imgToMd.findImgByMd(String.valueOf(mdf.toPath()));
                throw new InterruptedException();
            } catch (InterruptedException e) {
                log.info("done!\t" + Thread.currentThread().getName() +
                        "\tThreadId:" + Thread.currentThread().getId() + "\t:" + mdf.toString());
            }
        }
    }


    /***
     * Desc: 移动文件
     */
    static void mvFile(String soc, String dir) {
        try {
            Files.move(Paths.get(soc), Paths.get(dir), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error(e.toString());
        }
    }

    /**
     * 判断指定目录图片相似性,不与自己比较且不与比过的比较
     * 目前速度难以忍受，不建议启用
     */
    static public void checkImgsIsRepeat(String path) {
        File[] files = sortFileByModifyTime(path);

        for (int i = 0; i <= files.length; i++) {
            for (int j = i + 1; j < files.length; j++) {
                if (!files[i].getName().equals(files[j].getName())) { // 不与自己比较
                    float percent = compare(getData(files[i].getAbsolutePath()),
                            getData(files[j].getAbsolutePath()));
                    if (percent == 0F) {
                        log.info(files[i].getName() + "与" + files[j].getName() + "无法比较!");
                    } else {
                        log.info(files[i].getName() + "与" + files[j].getName() + "相似度为" + percent + "%");
                        if (percent >= 70F) { // 建议定为70%,74%以上即可判断是否为同一张图
                            log.warn(files[i].getName() + "与" + files[j].getName() + "相似度大于70%");
                            copyFileByChannelTransfer(files[i].getAbsolutePath(), tmpPath.concat(files[i].getName()));
                            copyFileByChannelTransfer(files[j].getAbsolutePath(), tmpPath.concat(files[j].getName()));
                        }
                    }
//                    System.out.println("i="+i+"\tj="+j+"\t"+files[i].getName() + "与" + files[j].getName() + "相似度为" + "%");
                }
            }
        }
    }


    /**
     * 压缩体积7m为290k左右，体积还不够小,比系统压缩工具还大，1比1压缩原图体积反增加;
     *
     * @param sourceImg
     * @param outImg
     * @return
     */
    static public void zipImgByThubnails(String sourceImg, String outImg) {

        try {
            Thumbnails.of(sourceImg)
                    .scale(1f)
                    .outputQuality(0.1f)
                    .toFile(outImg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            // 将图片生成md文件
//            copyImgGenMdFile("/home/mi/MI/md/temp/test.md");

            // 整理img目录，只修改图片链接;将md文件中的所有图片移动到按文件命名的img目录内并修改md链接

            upImgPathByAllMdFile(mdFilePath.concat(",img,ky,company,oldImg,tmp,mind,heat"));//排除目录以","分隔

//            test(imgPath);

            System.gc(); // 高版本可能需要显示的调用
        } catch (IllegalAccessError e) {
        } catch (Exception e) {
            log.error(String.valueOf(e));
        }
    }

    /**
     * 判断指定目录图片相似性,不与自己比较且不与比过的比较
     * 目前速度难以忍受，不建议启用
     */
    static public void test(String path) {
        File[] files = sortFileByModifyTime(path);

        for (int i = 0; i <= files.length; i++) {
            for (int j = i + 1; j < files.length; j++) {
                if (!files[i].getName().equals(files[j].getName())) { // 不与自己比较
                    float percent = compare(getData(files[i].getAbsolutePath()),
                            getData(files[j].getAbsolutePath()));
                    if (percent == 0F) {
                        log.info(files[i].getName() + "与" + files[j].getName() + "无法比较!");
                    } else {
                        log.info(files[i].getName() + "与" + files[j].getName() + "相似度为" + percent + "%");
                        if (percent >= 70F) { // 建议定为70%,74%以上即可判断是否为同一张图
                            log.warn(files[i].getName() + "与" + files[j].getName() + "相似度大于70%");
                            copyFileByChannelTransfer(files[i].getAbsolutePath(), tmpPath.concat(files[i].getName()));
                            copyFileByChannelTransfer(files[j].getAbsolutePath(), tmpPath.concat(files[j].getName()));
                        }
                    }
//                    System.out.println("i="+i+"\tj="+j+"\t"+files[i].getName() + "与" + files[j].getName() + "相似度为" + "%");
                }
            }
        }

    }

}
