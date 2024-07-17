package quan.data.definition;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * 字段定义
 */
public class FieldDefinition extends Definition implements Cloneable {

    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-zA-Z\\d]*");

    private ClassDefinition owner;

    //原始定义的字段类型,集合类型包含其元素类型
    private String typeInfo;

    //拆分后的字段类型
    private String type;
    private String keyType;
    private String valueType;

    //内建类型对应的特定语言基本类型，自定义类型保持不变
    private String basicType;
    private String keyBasicType;
    private String valueBasicType;

    //内建类型对应的特定语言具体类型，自定义类型保持不变
    private String classType;
    private String keyClassType;
    private String valueClassType;

    //字段类型依赖是否有循环
    private boolean cycle;

    //数字类型限制：最小值
    private Object min;

    //数字类型限制：最小值
    private Object max;

    //枚举值
    private String enumValue;

    //忽略编码
    private boolean ignore;

    //索引类型
    private String index;


    public FieldDefinition() {
    }

    @Override
    public int getKind() {
        return KIND_FIELD;
    }

    @Override
    public String getKindName() {
        return "字段";
    }

    public ClassDefinition getOwner() {
        return owner;
    }

    public FieldDefinition setOwner(ClassDefinition owner) {
        this.owner = owner;
        return this;
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        if (name != null) {
            underscoreName = toSnakeCase(name, false);
        }
    }

    @Override
    public Pattern getNamePattern() {
        return NAME_PATTERN;
    }


    public String getTypeInfo() {
        return typeInfo;
    }

    public void setTypeInfo(String typeInfo) {
        if (!StringUtils.isBlank(typeInfo)) {
            this.typeInfo = typeInfo;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (!StringUtils.isBlank(type)) {
            this.type = type.trim();
        } else {
            this.type = null;
        }
    }

    public boolean isBuiltinType() {
        return isBuiltinType(type);
    }

    public boolean isBuiltinType(String type) {
        return Constants.BUILTIN_TYPES.contains(type);
    }

    public boolean isIntegralNumberType() {
        return Constants.INTEGRAL_NUMBER_TYPES.contains(type);
    }

    public boolean isNumberType() {
        return Constants.NUMBER_TYPES.contains(type);
    }

    public boolean isCollectionType() {
        return Constants.COLLECTION_TYPES.contains(type);
    }

    public boolean isPrimitiveType() {
        return Constants.PRIMITIVE_TYPES.contains(type);
    }

    public boolean isEnumType() {
        return getEnum() != null;
    }

    public EnumDefinition getEnum() {
        ClassDefinition classDefinition = getClassDefinition();
        if (classDefinition instanceof EnumDefinition) {
            return (EnumDefinition) classDefinition;
        }
        return null;
    }

    public boolean isBeanType() {
        return getTypeBean() != null;
    }

    public ClassDefinition getClassDefinition() {
        return parser.getClass(owner, type);
    }

    public BeanDefinition getTypeBean() {
        ClassDefinition classDefinition = getClassDefinition();
        if (classDefinition != null && classDefinition.getClass() == BeanDefinition.class) {
            return (BeanDefinition) classDefinition;
        }
        return null;
    }

    /**
     * 类型是否合法
     */
    public boolean isLegalType() {
        return isBuiltinType() || isBeanType() || isEnumType();
    }

    public boolean isMapType() {
        return "map".equals(type);
    }

    public boolean isListType() {
        return "list".equals(type);
    }

    public boolean isSetType() {
        return "set".equals(type);
    }

    public boolean isStringType() {
        return "string".equals(type);
    }

    public Object getMin() {
        return min;
    }

    public void setMin(Object min) {
        if (min instanceof String) {
            min = ((String) min).trim();
        }
        this.min = min;
    }

    public Object getMax() {
        return max;
    }

    public void setMax(Object max) {
        if (max instanceof String) {
            max = ((String) max).trim();
        }
        this.max = max;
    }

    public String getEnumValue() {
        return enumValue;
    }

    public void setEnumValue(String enumValue) {
        if (!StringUtils.isBlank(enumValue)) {
            this.enumValue = enumValue.trim();
        }
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        if (!StringUtils.isBlank(keyType)) {
            this.keyType = keyType.trim();
        }
    }

    public boolean isBuiltinKeyType() {
        return isBuiltinType(keyType);
    }

    public boolean isPrimitiveKeyType() {
        return Constants.PRIMITIVE_TYPES.contains(keyType);
    }

    public boolean isStringKeyType() {
        return keyType.equals("string");
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        if (!StringUtils.isBlank(valueType)) {
            this.valueType = valueType.trim();
        }
    }

    public boolean isBuiltinValueType() {
        return isBuiltinType(valueType);
    }

    public boolean isPrimitiveValueType() {
        return Constants.PRIMITIVE_TYPES.contains(valueType);
    }

    public boolean isNumberValueType() {
        return Constants.NUMBER_TYPES.contains(valueType);
    }

    public boolean isBeanValueType() {
        return getValueTypeBean() != null;
    }

    public boolean isStringValueType() {
        return valueType.equals("string");
    }

    public BeanDefinition getValueTypeBean() {
        if (!isCollectionType()) {
            return null;
        }

        ClassDefinition classDefinition = parser.getClass(owner, getValueType());
        if (classDefinition != null && classDefinition.getClass() == BeanDefinition.class) {
            return (BeanDefinition) classDefinition;
        }

        return null;
    }

    /**
     * 集合值类型是否合法
     */
    public boolean isLegalValueType() {
        return isPrimitiveValueType() || isBeanValueType();
    }

    public String getBasicType() {
        if (isEnumType() || isBeanType()) {
            return getClassType();
        }
        if (basicType == null) {
            return getType();
        }
        return basicType;
    }

    public void setBasicType(String basicType) {
        this.basicType = basicType;
    }

    public String getKeyBasicType() {
        if (keyBasicType == null) {
            return getKeyType();
        }
        return keyBasicType;
    }

    public void setKeyBasicType(String keyBasicType) {
        this.keyBasicType = keyBasicType;
    }

    public String getValueBasicType() {
        if (valueBasicType == null) {
            return getValueType();
        }
        return valueBasicType;
    }

    public void setValueBasicType(String valueBasicType) {
        this.valueBasicType = valueBasicType;
    }

    public String getClassType() {
        if (classType == null) {
            return ClassDefinition.getShortName(type);
        }
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public String getKeyClassType() {
        if (keyClassType == null) {
            return getKeyType();
        }
        return keyClassType;
    }

    public void setKeyClassType(String keyClassType) {
        this.keyClassType = keyClassType;
    }

    public String getValueClassType() {
        if (valueClassType == null) {
            return ClassDefinition.getShortName(getValueType());
        }
        return valueClassType;
    }

    public void setValueClassType(String valueClassType) {
        this.valueClassType = valueClassType;
    }

    public boolean isCycle() {
        return cycle;
    }

    public FieldDefinition setCycle(boolean cycle) {
        this.cycle = cycle;
        return this;
    }

    public boolean isIgnore() {
        return ignore;
    }

    public FieldDefinition setIgnore(String ignore) {
        if (!StringUtils.isBlank(ignore) && ignore.trim().equals("true")) {
            this.ignore = true;
        }
        return this;
    }

    public String getIndex() {
        return index;
    }

    public FieldDefinition setIndex(String index) {
        if (!StringUtils.isBlank(index)) {
            this.index = index.trim();
        }
        return this;
    }

    @Override
    public FieldDefinition clone() {
        try {
            return (FieldDefinition) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public Object getDefaultValue() {
        if (Constants.NUMBER_TYPES.contains(type)) {
            return 0;
        } else if ("string".equals(type)) {
            return "";
        } else if ("bool".equals(type)) {
            return false;
        } else {
            return null;
        }
    }

}
