package quan.data.util;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件工具
 */
public class FileUtils {

    /**
     * 递归列出子文件
     *
     * @param path   文件目录
     * @param suffix 文件名后缀
     * @return 子文件集合
     */
    public static Set<File> listFiles(File path, String suffix) {
        Objects.requireNonNull(suffix);
        return listFiles(path, Pattern.compile(".+\\." + suffix));
    }

    /**
     * 递归列出子文件
     *
     * @param path        文件目录
     * @param namePattern 文件名格式
     * @return 子文件集合
     */
    public static Set<File> listFiles(File path, Pattern namePattern) {
        Objects.requireNonNull(namePattern);
        Set<File> children = new HashSet<>();
        listFiles(path, namePattern, children);
        return children;
    }

    /**
     * 递归列出子文件
     *
     * @param path 文件目录
     * @return 子文件集合
     */
    public static Set<File> listFiles(File path) {
        Set<File> children = new HashSet<>();
        listFiles(path, null, children);
        return children;
    }

    private static void listFiles(File path, Pattern namePattern, Set<File> children) {
        if (path.isDirectory()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    listFiles(file, namePattern, children);
                }
            }
        } else if (namePattern == null || namePattern.matcher(path.getName()).matches()) {
            children.add(path);
        }
    }

}
