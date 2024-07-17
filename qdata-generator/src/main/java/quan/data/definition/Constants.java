package quan.data.definition;

import quan.data.util.CollectionUtils;

import java.util.Set;
import java.util.regex.Pattern;

import static quan.data.util.CollectionUtils.asSet;

/**
 * 通用常量
 */
public final class Constants {

    /**
     * 整数类型
     */
    public static final Set<String> INTEGRAL_NUMBER_TYPES = asSet("short", "int", "long");

    /**
     * 数字类型
     */
    public static final Set<String> NUMBER_TYPES = asSet(INTEGRAL_NUMBER_TYPES, "float", "double");

    /**
     * 原生类型
     */
    public static final Set<String> PRIMITIVE_TYPES = asSet(NUMBER_TYPES, "bool", "string");

    /**
     * 集合类型
     */
    public static final Set<String> COLLECTION_TYPES = asSet("list", "set", "map");

    /**
     * 支持的内建类型
     */
    public static final Set<String> BUILTIN_TYPES = asSet(PRIMITIVE_TYPES, COLLECTION_TYPES);

    /**
     * Java保留字
     */
    public static final Set<String> RESERVED_WORDS = CollectionUtils.asSet(
            "abstract", "assert", "boolean", "break", "throws", "case", "catch", "char", "volatile",
            "const", "continue", "default", "do", "else", "enum", "extends", "finally", "long", "transient",
            "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "double",
            "native", "new", "try", "package", "private", "protected", "public", "void", "strictfp", "short",
            "static", "super", "switch", "synchronized", "throw", "byte", "final", "while", "class", "return"
    );

    /**
     * 首字母小写包名格式
     */
    public static final Pattern LOWER_PACKAGE_NAME_PATTERN = Pattern.compile("[a-z][a-z\\d]*(\\.[a-z][a-z\\d]*)*");

    /**
     * 首字母大写包名格式
     */
    public static final Pattern UPPER_PACKAGE_NAME_PATTERN = Pattern.compile("[A-Z][a-z\\d]*(\\.[A-Z][a-z\\d]*)*");

}
