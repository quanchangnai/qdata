package quan.data.definition;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import quan.data.definition.DependentSource.DependentType;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * 类定义
 */
public abstract class ClassDefinition extends Definition {

    public static final Pattern NAME_PATTERN = Pattern.compile("[A-Z][a-zA-Z\\d]*");

    //不含前缀的默认包名
    private String packageName;

    //定义文件
    private String definitionFile;

    //定义版本
    private String version;

    //依赖的类，Map<依赖的类名, <来源, ClassDefinition>>
    protected Map<String, TreeMap<DependentSource, ClassDefinition>> dependentsClasses = new HashMap<>();

    protected List<FieldDefinition> fields = new LinkedList<>();

    //导包，和具体语言相关
    private Map<String, String> imports = new TreeMap<>();

    //依赖类的简单名对应全名，和具体语言相关
    private Map<String, String> dependentClassNames;

    //字段名:字段定义
    protected Map<String, FieldDefinition> nameFields = new HashMap<>();

    @Override
    public String getKindName() {
        return "类型";
    }

    @Override
    public Pattern getNamePattern() {
        return NAME_PATTERN;
    }

    public void reset() {
        imports.clear();
        dependentClassNames = null;
        fields.forEach(this::resetField);
    }

    protected void resetField(FieldDefinition fieldDefinition) {
        fieldDefinition.setBasicType(null);
        fieldDefinition.setClassType(null);
        fieldDefinition.setKeyBasicType(null);
        fieldDefinition.setKeyClassType(null);
        fieldDefinition.setValueBasicType(null);
        fieldDefinition.setValueClassType(null);
    }

    public String getPackagePrefix() {
        if (this instanceof EnumDefinition && !isBlank(parser.getEnumPackagePrefix())) {
            return parser.getEnumPackagePrefix();
        } else {
            return parser.getPackagePrefix();
        }
    }

    public void setPackageName(String packageName) {
        if (!StringUtils.isBlank(packageName)) {
            this.packageName = packageName;
        }
    }

    public String getPackageName() {
        return packageName;
    }

    public String getFullPackageName() {
        String packagePrefix = getPackagePrefix();
        String packageName = getPackageName();
        if (!isBlank(packagePrefix)) {
            if (!isBlank(packageName)) {
                return packagePrefix + "." + packageName;
            } else {
                return packagePrefix;
            }
        }
        return packageName;
    }

    public String getLongName() {
        return getLongName(this, getName());
    }

    public String getFullName() {
        String fullPackageName = getFullPackageName();
        if (StringUtils.isBlank(fullPackageName)) {
            return getName();
        } else {
            return fullPackageName + "." + getName();
        }
    }

    @Override
    public String getValidatedName(String append) {
        String kindName = getKindName();
        String validatedName;
        if (getName() != null) {
            validatedName = kindName + "[" + getLongName() + "]" + append;
        } else {
            validatedName = kindName + append;
        }
        return validatedName;
    }

    /**
     * 短类名是否允许相同
     */
    public boolean isAllowSameName() {
        return true;
    }

    /**
     * 获取短类名，即不带包名的类名
     */
    public static String getShortName(String name) {
        if (name == null) {
            return null;
        }
        int index = name.lastIndexOf(".");
        if (index >= 0) {
            return name.substring(index + 1);
        }
        return name;
    }


    /**
     * 获取长类名，即和具体语言环境无关的[不含前缀的包名.类名]
     */
    public static String getLongName(ClassDefinition owner, String name) {
        if (!isBlank(owner.getPackageName()) && !isBlank(name) && !name.contains(".")) {
            name = owner.getPackageName() + "." + name;
        }
        return name;
    }


    public List<FieldDefinition> getFields() {
        return fields;
    }

    public Map<String, FieldDefinition> getNameFields() {
        return nameFields;
    }

    public void addField(FieldDefinition fieldDefinition) {
        fieldDefinition.setOwner(this);
        fields.add(fieldDefinition);
    }

    public FieldDefinition getField(String fieldName) {
        return nameFields.get(fieldName);
    }


    public String getDefinitionFile() {
        return definitionFile;
    }

    public ClassDefinition setDefinitionFile(String definitionFile) {
        this.definitionFile = definitionFile;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = DigestUtils.md5Hex(version);
    }

    public Map<String, String> getImports() {
        return imports;
    }

    public void addImport(String importName) {
        if (!importName.equals(getFullPackageName() + ".*")) {
            imports.put(importName, null);
        }
    }

    public void addDependent(DependentType dependentType, ClassDefinition ownerClass, Definition ownerDefinition, ClassDefinition dependentClass) {
        if (dependentClass != null) {
            DependentSource dependentSource = new DependentSource(dependentType, ownerClass, ownerDefinition, dependentClass);
            dependentsClasses.computeIfAbsent(dependentClass.getName(), k -> new TreeMap<>()).put(dependentSource, dependentClass);
        }
    }

    public Map<String, TreeMap<DependentSource, ClassDefinition>> getDependentsClasses() {
        return dependentsClasses;
    }

    public void validate1() {
        validateName();

        for (FieldDefinition fieldDefinition : getFields()) {
            validateField(fieldDefinition);
        }
    }

    /**
     * 依赖{@link #validate1()}的结果，必须等所有类的{@link #validate1()}执行完成后再执行
     */
    public void validate2() {
        for (FieldDefinition fieldDefinition : getFields()) {
            validateFieldNameDuplicate(fieldDefinition);
        }
    }

    /**
     * 依赖{@link #validate2()}的结果
     */
    public void validate3() {
        validateDependents();
    }


    protected void validateName() {
        if (getName() == null) {
            addValidatedError("定义文件[" + getDefinitionFile() + "]中" + getKindName() + "的名字不能为空");
        } else {
            if (isReservedWord(getName())) {
                addValidatedError(getValidatedName() + "的名字不合法,不能使用保留字");
            }
            if (!getNamePattern().matcher(getName()).matches()) {
                addValidatedError(getValidatedName() + "的名字格式错误,正确格式:" + getNamePattern());
            }
            if (getIllegalNames().contains(getName())) {
                addValidatedError(getValidatedName() + "的名字不合法");
            }
        }
    }

    protected Set<String> getIllegalNames() {
        return Collections.emptySet();
    }

    protected void validateField(FieldDefinition fieldDefinition) {
        validateFieldName(fieldDefinition);
    }

    /**
     * 校验字段名
     */
    protected void validateFieldName(FieldDefinition fieldDefinition) {
        if (fieldDefinition.getName() == null) {
            addValidatedError(getValidatedName() + "的字段名不能为空");
            return;
        }

        //校验字段名格式
        if (!fieldDefinition.getNamePattern().matcher(fieldDefinition.getName()).matches()) {
            addValidatedError(getValidatedName() + "的字段名[" + fieldDefinition.getName() + "]格式错误,正确格式:" + fieldDefinition.getNamePattern());
            return;
        }

        if (isReservedWord(fieldDefinition.getName())) {
            addValidatedError(getValidatedName() + "的字段名[" + fieldDefinition.getName() + "]不合法，不能使用保留字");
        }
    }

    protected boolean isReservedWord(String name) {
        return Constants.RESERVED_WORDS.contains(name);
    }

    protected void validateFieldNameDuplicate(FieldDefinition fieldDefinition) {
        if (fieldDefinition.getName() == null) {
            return;
        }
        if (nameFields.containsKey(fieldDefinition.getName())) {
            addValidatedError(getValidatedName() + "的字段名[" + fieldDefinition.getName() + "]不能重复");
        } else {
            nameFields.put(fieldDefinition.getName(), fieldDefinition);
        }
    }


    protected void validateDependents() {
    }


    protected void addValidatedError(String error) {
        parser.addValidatedError(error);
    }

    public String resolveFieldRef(String fieldRef) {
        return fieldRef;
    }

    public void setDependentClassNames(Map<String, String> dependentClassNames) {
        this.dependentClassNames = dependentClassNames;
    }

    public String getDependentName(String className) {
        if (dependentClassNames != null && getName().equals(className) && dependentClassNames.containsKey(className)) {
            return dependentClassNames.get(className);
        } else {
            return className;
        }
    }

    public String dn(String className) {
        return getDependentName(className);
    }

    @Override
    public String toString() {
        return getClass().getName() + "{" +
                "name='" + getLongName() + '\'' +
                '}';
    }

}
