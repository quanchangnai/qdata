package quan.data.definition;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * 各种【定义】的抽象类
 */
public abstract class Definition {

    public static final int KIND_ENUM = 1;

    public static final int KIND_BEAN = 2;

    public static final int KIND_FIELD = 4;

    public static final int KIND_DATA = 5;

    public static final int KIND_INDEX = 7;


    private String name;

    protected String underscoreName;

    private String comment = "";

    protected DefinitionParser parser;

    public Definition() {
    }

    public void setParser(DefinitionParser parser) {
        this.parser = parser;
    }

    public DefinitionParser getParser() {
        return parser;
    }

    public String getName() {
        return name;
    }

    public String getValidatedName() {
        return getValidatedName("");
    }

    public String getValidatedName(String append) {
        String kindName = getKindName();
        String validatedName;
        if (name != null) {
            validatedName = kindName + "[" + name + "]" + append;
        } else {
            validatedName = kindName + append;
        }
        return validatedName;
    }

    public void setName(String name) {
        if (!StringUtils.isBlank(name)) {
            this.name = name.trim();
        }
    }

    public String getUnderscoreName() {
        return underscoreName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        if (!StringUtils.isBlank(comment)) {
            this.comment = comment.trim();
        }
    }

    public abstract int getKind();

    public abstract String getKindName();

    public abstract Pattern getNamePattern();

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "name='" + name + '\'' +
                '}';
    }

    /**
     * 驼峰字符串转成蛇形字符串(下划线分隔)
     *
     * @param s     源字符串
     * @param lower 返回小写还是大写字符串
     */
    public static String toSnakeCase(String s, boolean lower) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && c >= 'A' && c <= 'Z') {
                sb.append('_');
            }
            if (lower) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }

        return sb.toString();
    }

}

