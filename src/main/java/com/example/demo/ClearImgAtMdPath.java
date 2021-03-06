package com.example.demo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class ClearImgAtMdPath {
    static List allFilePath = new LinkedList(), newImgList = new LinkedList(),imgList=new LinkedList(),mdList=new LinkedList(),delList=new LinkedList(),tempList;

    public static void main(String[] args) {
        // function call
        // 先读取所有文件,按类别分类;
        String path = "/home/mi/MI/md"; // 要遍历的路径
        String imgPath = "/home/mi/MI/md/img"; // 要遍历的路径

        tempList=new LinkedList();
        findFile(path);
        allFilePath= tempList; // 遍历目录所有文件
        tempList=new LinkedList();
        findFile(imgPath);
        imgList=tempList;
        assort(); // 文件分类
        current(); // 读取文件匹配img文件名
        delFile();

        System.out.println(allFilePath+"\n"+imgList+"\n"+mdList+"\n"+newImgList+"\n"+delList);
        System.out.println(newImgList.size() + "," + imgList.size()+","+delList.size());

    }

    // 遍历路径
    static void findFile(String filePath) {
            File file = new File(filePath);
            File[] files = file.listFiles();
            List fileList = Arrays.asList(files);
            Collections.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    if (o1.isDirectory() && o2.isFile())
                        return -1;
                    if (o1.isFile() && o2.isDirectory())
                        return 1;
                    return o1.getName().compareTo(o2.getName());
                }
            });
            for (File fs : files) {
                if (fs.isDirectory()) {
                    findFile(fs.getAbsolutePath());
                } else if (fs.isFile()) {
                    tempList.add(fs.getAbsoluteFile());
                }
        }
    }

    // 分类
    static void assort() {
        String temp = null;
        for (Iterator iter = allFilePath.iterator(); iter.hasNext(); ) {
            temp = String.valueOf(iter.next());
            if (temp.lastIndexOf(".md") != -1) {
                mdList.add(temp);
            }
        }
    }

    // 读取每一个md文件，在md文件中匹配img文件名，成功则放进数组,最后再与原img文件名匹配,找出不存在的文件名删除;
    static void current() {
        try {
            for (int i = 0; i < mdList.size(); i++) {
                FileInputStream fileIn = new FileInputStream(String.valueOf(mdList.get(i)));
                FileChannel fileChannel = fileIn.getChannel();
                MappedByteBuffer mappedBuf = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
                boolean end = false;
                do {
                    int limit = mappedBuf.limit();
                    int position = mappedBuf.position();
                    if (position >= limit) {
                        end = true;
                    }

                    int maxSize = 2048;
                    if (limit - position < maxSize) {
                        maxSize = limit - position;
                    }
                    byte[] array = new byte[maxSize];
                    mappedBuf.get(array);
                    //拿array搞事情
                    currentFile(new String(array));
//                    System.out.println(new String(array));
                } while (!end);
                mappedBuf.clear();
                fileChannel.close();
                fileIn.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 匹配文件放入数组,注意以img路径以0命名的文件会报错
    static void currentFile(String array) {
        for (int i = 0; i < imgList.size(); i++) {
//            System.out.println(imgList.get(i).toString().substring(imgList.get(i).toString().lastIndexOf("/")+1));
//            if (array.toString().indexOf(new File(String.valueOf(imgList.get(i))).getName()) !=-1) {
            if (array.indexOf(imgList.get(i).toString().substring(imgList.get(i).toString().lastIndexOf("/")+1)) != -1) {
                // 如果找到即加入
                newImgList.add(imgList.get(i));
            }
        }
    }

    // 找出md不对应的文件并删除
    static void delFile() {
        for(Iterator iter = imgList.iterator(); iter.hasNext();)
        if (!newImgList.contains(iter.next())){
                delList.add(iter.next());
                try {
//                    File files=new File(iter.next().toString());
//                    files.renameTo(new File("/home/mi/MI/md/timg/"+files.getName()));
//                    System.out.println(files.getName()+"MoveToBackSucess!");
                }catch (Exception e) {
                    e.printStackTrace();
                }
        }
    }

}
