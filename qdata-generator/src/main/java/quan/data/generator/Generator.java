package quan.data.generator;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quan.data.definition.BeanDefinition;
import quan.data.definition.ClassDefinition;
import quan.data.definition.DataDefinition;
import quan.data.definition.DefinitionParser;
import quan.data.definition.DependentSource;
import quan.data.definition.DependentSource.DependentType;
import quan.data.definition.EnumDefinition;
import quan.data.definition.FieldDefinition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * 代码生成器
 */
public class Generator {

    protected static final Logger logger = LoggerFactory.getLogger(Generator.class);

    //生成器参数
    protected Properties params;

    protected Map<String, String> basicTypes = new HashMap<>();

    protected Map<String, String> classTypes = new HashMap<>();

    /**
     * 类的简单名对应全名
     */
    protected Map<String, String> classNames = new HashMap<>();

    protected boolean enable = true;

    //是否开启增量生成
    protected boolean increment;

    protected String definitionFileEncoding;

    protected Set<String> definitionPaths = new HashSet<>();

    protected String packagePrefix;

    protected String enumPackagePrefix;

    protected String codePath;

    protected DefinitionParser parser;

    protected Configuration freemarkerCfg;

    protected Map<Class<? extends ClassDefinition>, Template> templates = new HashMap<>();

    //<包名,<类名,类定义>
    protected Map<String, Map<String, ClassDefinition>> packagesClasses = new HashMap<>();

    //生成或删除代码文件数量
    protected int count;

    //上一次代码生成记录
    protected Map<String, String> oldRecords = new HashMap<>();

    //当前代码生成记录
    protected Map<String, String> newRecords = new HashMap<>();

    protected Set<String> addClasses = new HashSet<>();

    protected Set<String> deleteClasses = new HashSet<>();

    public Generator(Properties params) {
        initBasicTypes();
        initClassTypes();
        initClassNames();
        parseParams(params);
        if (enable) {
            checkParams();
        }
    }

    public void setDefinitionPath(Collection<String> definitionPaths) {
        this.definitionPaths.clear();
        this.definitionPaths.addAll(definitionPaths);
    }

    public void setDefinitionPath(String definitionPath) {
        this.definitionPaths.clear();
        this.definitionPaths.add(definitionPath);
    }

    public void setCodePath(String codePath) {
        this.codePath = codePath;
    }

    public void setPackagePrefix(String packagePrefix) {
        this.packagePrefix = packagePrefix;
    }

    public void setEnumPackagePrefix(String enumPackagePrefix) {
        this.enumPackagePrefix = enumPackagePrefix;
    }


    public void setParser(DefinitionParser parser) {
        if (parser == null) {
            return;
        }

        this.parser = parser;
        parser.setDefinitionFileEncoding(definitionFileEncoding);
        parseParams(params);

        if (!parser.getDefinitionPaths().isEmpty() && definitionPaths.isEmpty()) {
            definitionPaths.addAll(parser.getDefinitionPaths());
        } else {
            parser.setDefinitionPaths(definitionPaths);
        }
    }

    public DefinitionParser getParser() {
        return parser;
    }

    private void initBasicTypes() {
        basicTypes.put("byte", "byte");
        basicTypes.put("bool", "boolean");
        basicTypes.put("short", "short");
        basicTypes.put("int", "int");
        basicTypes.put("long", "long");
        basicTypes.put("float", "float");
        basicTypes.put("double", "double");
        basicTypes.put("string", "String");
        basicTypes.put("set", "Set");
        basicTypes.put("list", "List");
        basicTypes.put("map", "Map");
    }


    private void initClassTypes() {
        classTypes.put("byte", "Byte");
        classTypes.put("bool", "Boolean");
        classTypes.put("short", "Short");
        classTypes.put("int", "Integer");
        classTypes.put("long", "Long");
        classTypes.put("float", "Float");
        classTypes.put("double", "Double");
        classTypes.put("string", "String");
        classTypes.put("set", "SetField");
        classTypes.put("list", "ListField");
        classTypes.put("map", "MapField");
    }

    private void initClassNames() {
        classNames.put("Boolean", Boolean.class.getName());
        classNames.put("Short", Short.class.getName());
        classNames.put("Integer", Integer.class.getName());
        classNames.put("Long", Long.class.getName());
        classNames.put("Float", Float.class.getName());
        classNames.put("Double", Double.class.getName());
        classNames.put("String", String.class.getName());

        classNames.put("Set", Set.class.getName());
        classNames.put("HashSet", HashSet.class.getName());
        classNames.put("List", List.class.getName());
        classNames.put("ArrayList", ArrayList.class.getName());
        classNames.put("Map", Map.class.getName());
        classNames.put("HashMap", HashMap.class.getName());

        classNames.put("Object", Object.class.getName());
        classNames.put("Class", Class.class.getName());
        classNames.put("Override", Override.class.getName());
        classNames.put("SuppressWarnings", SuppressWarnings.class.getName());

        classNames.put("Objects", Objects.class.getName());
        classNames.put("Arrays", Arrays.class.getName());
        classNames.put("Collection", Collection.class.getName());
        classNames.put("Collections", Collections.class.getName());

        classNames.put("Index", "quan.data.Index");
        classNames.put("Bean", "quan.data.Bean");
        classNames.put("Data", "quan.data.Data");
        classNames.put("Transaction", "quan.data.Transaction");
        classNames.put("BaseField", "quan.data.field.BaseField");
        classNames.put("BeanField", "quan.data.field.BeanField");
        classNames.put("ListField", "quan.data.field.ListField");
        classNames.put("MapField", "quan.data.field.MapField");
        classNames.put("SetField", "quan.data.field.SetField");
        classNames.put("NumberUtils", "quan.util.NumberUtils");
        classNames.put("BsonReader", "org.bson.BsonReader");
        classNames.put("BsonWriter", "org.bson.BsonWriter");
        classNames.put("Codec", "org.bson.codecs.Codec");
        classNames.put("BsonType", "org.bson.codecs.BsonType");
        classNames.put("JsonWriter", "org.bson.json.JsonWriter");
        classNames.put("EncoderContext", "org.bson.codecs.EncoderContext");
        classNames.put("DecoderContext", "org.bson.codecs.DecoderContext");
        classNames.put("CodecRegistry", "org.bson.codecs.configuration.CodecRegistry");
    }

    protected void parseParams(Properties params) {
        this.params = params;

        String enable = params.getProperty("enable");
        if (!StringUtils.isBlank(enable)) {
            this.enable = enable.trim().equals("true");
        }

        String increment = params.getProperty("increment");
        if (!StringUtils.isBlank(increment)) {
            this.increment = increment.trim().equals("true");
        }

        String definitionPath = params.getProperty("definitionPath");
        if (!StringUtils.isBlank(definitionPath)) {
            definitionPaths.addAll(Arrays.asList(definitionPath.split("[,，]")));
        }

        String definitionFileEncoding = params.getProperty("definitionFileEncoding");
        if (!StringUtils.isBlank(definitionFileEncoding)) {
            this.definitionFileEncoding = definitionFileEncoding;
        }

        if (parser != null) {
            parser.setDefinitionFileEncoding(definitionFileEncoding);
            parser.setDataNamePattern(params.getProperty("namePattern"));
            parser.setBeanNamePattern(params.getProperty("beanNamePattern"));
            parser.setEnumNamePattern(params.getProperty("enumNamePattern"));
        }

        String codePath = params.getProperty("codePath");
        if (!StringUtils.isBlank(codePath)) {
            setCodePath(codePath);
        }

        packagePrefix = params.getProperty("packagePrefix");
        enumPackagePrefix = params.getProperty("enumPackagePrefix");
    }


    /**
     * 检查生成器参数
     */
    protected void checkParams() {
        if (definitionPaths.isEmpty()) {
            throw new IllegalArgumentException("定义文件路径[definitionPaths]不能为空");
        }
        if (codePath == null) {
            throw new IllegalArgumentException("目标代码文件路径[codePath]不能为空");
        }
    }

    protected void initFreemarker() {
        freemarkerCfg = new Configuration(Configuration.VERSION_2_3_23);
        freemarkerCfg.setClassForTemplateLoading(Generator.class, "");
        freemarkerCfg.setDefaultEncoding("UTF-8");

        try {
            Template dataTemplate = freemarkerCfg.getTemplate("Data.ftl");
            Template enumTemplate = freemarkerCfg.getTemplate("Enum.ftl");
            templates.put(EnumDefinition.class, enumTemplate);
            templates.put(DataDefinition.class, dataTemplate);
            templates.put(BeanDefinition.class, dataTemplate);
        } catch (IOException e) {
            logger.error("加载模板文件失败", e);
            return;
        }

        freemarkerCfg.setClassForTemplateLoading(getClass(), "");
    }

    protected void parseDefinitions() {
        if (parser == null) {
            throw new IllegalArgumentException("定义解析器[definitionParser]不能为空");
        }
        parser.setPackagePrefix(packagePrefix);
        parser.setEnumPackagePrefix(enumPackagePrefix);
        parser.parse();
    }

    public void generate() {
        generate(true);
    }

    public void generate(boolean printErrors) {
        if (!enable) {
            return;
        }

        checkParams();
        parseDefinitions();

        if (!parser.getValidatedErrors().isEmpty()) {
            if (printErrors) {
                printErrors();
            }
            return;
        }

        if (parser.getClasses().isEmpty()) {
            return;
        }

        initFreemarker();

        readHistory();

        for (ClassDefinition classDefinition : parser.getClasses()) {
            packagesClasses.computeIfAbsent(classDefinition.getPackageName(), k -> new HashMap<>()).put(classDefinition.getName(), classDefinition);
        }

        List<ClassDefinition> classDefinitions = new ArrayList<>();
        for (ClassDefinition classDefinition : parser.getClasses()) {
            classDefinition.reset();
            prepareClass(classDefinition);
            classDefinitions.add(classDefinition);
        }

        generate(classDefinitions);

        packagesClasses.clear();

        oldRecords.keySet().forEach(this::delete);

        writeHistory();

        logger.info("生成数据代码完成\n");
    }

    @SuppressWarnings("unchecked")
    private void readHistory() {
        try {
            File historyFile = new File(".history", "data.json");
            if (historyFile.exists()) {
                oldRecords = JSON.parseObject(new String(Files.readAllBytes(historyFile.toPath())), HashMap.class);
            }
        } catch (IOException e) {
            logger.error("读取生成历史文件失败", e);
        }
    }

    protected void writeHistory() {
        try {
            File historyPath = new File(".history");
            File historyFile = new File(historyPath, "data.json");
            if (historyPath.exists() || historyPath.mkdirs()) {
                JSON.writeJSONString(new FileWriter(historyFile), newRecords, SerializerFeature.PrettyFormat);
            }
        } catch (IOException e) {
            logger.error("写入生成历史文件失败", e);
        }

        oldRecords.clear();
        newRecords.clear();
    }

    protected void recordHistory(ClassDefinition classDefinition) {
        String fullName = classDefinition.getFullName();
        String version = classDefinition.getVersion();

        if (oldRecords.remove(fullName) == null) {
            addClasses.add(fullName);
        }

        newRecords.put(fullName, version);
    }

    /**
     * 删除失效的代码文件
     */
    protected void delete(String fullName) {
        count++;
        deleteClasses.add(fullName);

        File classFile = new File(codePath, fullName.replace(".", File.separator) + ".java");
        if (classFile.delete()) {
            logger.error("删除[{}]完成", classFile);
        } else {
            logger.error("删除[{}]失败", classFile);
        }
    }

    protected void generate(List<ClassDefinition> classDefinitions) {
        classDefinitions.forEach(this::generate);
    }

    protected final boolean checkChange(ClassDefinition classDefinition) {
        if (increment) {
            return isChange(classDefinition);
        } else {
            return true;
        }
    }

    protected boolean isChange(ClassDefinition classDefinition) {
        String fullName = classDefinition.getFullName();
        String version = classDefinition.getVersion();
        return !version.equals(oldRecords.get(fullName));
    }

    protected void generate(ClassDefinition classDefinition) {
        if (!checkChange(classDefinition)) {
            recordHistory(classDefinition);
            return;
        }

        File packagePath = new File(codePath, classDefinition.getFullPackageName().replace(".", File.separator));
        File classFile = new File(packagePath, classDefinition.getName() + ".java");

        if (!packagePath.exists() && !packagePath.mkdirs()) {
            logger.error("生成[{}]失败，无法创建目录[{}]", classFile, packagePath);
            return;
        }

        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(classFile.toPath()), StandardCharsets.UTF_8)) {
            count++;
            templates.get(classDefinition.getClass()).process(classDefinition, writer);
        } catch (Exception e) {
            logger.error("生成[{}]失败", classFile, e);
            return;
        }

        recordHistory(classDefinition);

        logger.info("生成[{}]成功", classFile);
    }

    protected void prepareClass(ClassDefinition classDefinition) {
        classDefinition.setDependentClassNames(this.classNames);

        if (classDefinition instanceof BeanDefinition) {
            prepareBean((BeanDefinition) classDefinition);
        }

        //不同包下的同名类依赖
        Map<String, TreeMap<DependentSource, ClassDefinition>> dependentsClasses = classDefinition.getDependentsClasses();
        for (String dependentName : dependentsClasses.keySet()) {
            ClassDefinition simpleNameClassDefinition = null;//同名类中只有一个可以使用简单类名
            TreeMap<DependentSource, ClassDefinition> dependentClasses = dependentsClasses.get(dependentName);
            for (DependentSource dependentSource : dependentClasses.keySet()) {
                ClassDefinition dependentClassDefinition = dependentClasses.get(dependentSource);
                String dependentClassFullName = dependentClassDefinition.getFullName();
                Pair<Boolean, Boolean> useDependent = howUseDependent(classDefinition, dependentClassDefinition, simpleNameClassDefinition);

                if (!useDependent.getLeft() && simpleNameClassDefinition == null) {
                    simpleNameClassDefinition = dependentClassDefinition;
                }

                if (useDependent.getRight()) {
                    if (useDependent.getLeft()) {
                        classDefinition.getImports().put(dependentClassDefinition.getFullName(), dependentClassFullName);
                    } else {
                        classDefinition.getImports().put(dependentClassDefinition.getFullName(), dependentClassDefinition.getName());
                    }
                }

                if (useDependent.getLeft()) {
                    if (dependentSource.getType() == DependentType.FIELD) {
                        ((FieldDefinition) dependentSource.getOwnerDefinition()).setClassType(dependentClassFullName);
                    } else if (dependentSource.getType() == DependentType.FIELD_VALUE) {
                        ((FieldDefinition) dependentSource.getOwnerDefinition()).setValueClassType(dependentClassFullName);
                    }
                }
            }
        }
    }

    /**
     * 判断依赖类的使用方式
     *
     * @return Pair<使用全类名还是简单类名, 是否使用import或using或require>
     */
    protected Pair<Boolean, Boolean> howUseDependent(ClassDefinition ownerClassDefinition, ClassDefinition dependentClassDefinition, ClassDefinition simpleNameClassDefinition) {
        String fullPackageName = ownerClassDefinition.getFullPackageName();
        String dependentFullPackageName = dependentClassDefinition.getFullPackageName();

        if (ownerClassDefinition.getName().equals(dependentClassDefinition.getName())) {
            return Pair.of(true, false);
        } else if (simpleNameClassDefinition == null) {
            return fullPackageName.equals(dependentFullPackageName) ? Pair.of(false, false) : Pair.of(false, true);
        } else {
            return dependentClassDefinition == simpleNameClassDefinition ? Pair.of(false, false) : Pair.of(true, false);
        }
    }

    protected void prepareBean(BeanDefinition beanDefinition) {
        beanDefinition.addImport("java.util.*");
        beanDefinition.addImport("org.bson.BsonType");
        beanDefinition.addImport("org.bson.BsonReader");
        beanDefinition.addImport("org.bson.BsonWriter");
        beanDefinition.addImport("org.bson.codecs.Codec");
        beanDefinition.addImport("org.bson.codecs.EncoderContext");
        beanDefinition.addImport("org.bson.codecs.DecoderContext");
        beanDefinition.addImport("org.bson.codecs.configuration.CodecRegistry");
        beanDefinition.addImport("quan.data.*");
        beanDefinition.addImport("quan.data.field.*");

        if (beanDefinition instanceof DataDefinition) {
            beanDefinition.addImport("org.bson.json.JsonWriter");
        }

        beanDefinition.getFields().forEach(this::prepareField);
    }

    protected void prepareField(FieldDefinition fieldDefinition) {
        ClassDefinition owner = fieldDefinition.getOwner();
        String fieldType = fieldDefinition.getType();

        if (fieldDefinition.isBuiltinType()) {
            fieldDefinition.setBasicType(owner.getDependentName(basicTypes.get(fieldType)));
            fieldDefinition.setClassType(owner.getDependentName(classTypes.get(fieldType)));
        }

        if (fieldDefinition.isCollectionType()) {
            if (fieldType.equals("map") && fieldDefinition.isBuiltinKeyType()) {
                String fieldKeyType = fieldDefinition.getKeyType();
                fieldDefinition.setKeyBasicType(owner.getDependentName(basicTypes.get(fieldKeyType)));
                fieldDefinition.setKeyClassType(owner.getDependentName(classTypes.get(fieldKeyType)));
            }

            String fieldValueType = fieldDefinition.getValueType();
            if (fieldDefinition.isBuiltinValueType()) {
                fieldDefinition.setValueBasicType(owner.getDependentName(basicTypes.get(fieldValueType)));
                fieldDefinition.setValueClassType(owner.getDependentName(classTypes.get(fieldValueType)));
            }
        }

        if (fieldDefinition.getMin() != null || fieldDefinition.getMax() != null) {
            fieldDefinition.getOwner().addImport("quan.data.util.NumberUtils");
        }
    }

    protected void printErrors() {
        if (parser == null) {
            return;
        }

        LinkedHashSet<String> errors = parser.getValidatedErrors();
        if (errors.isEmpty()) {
            return;
        }

        logger.error("生成数据代码失败，路径{}下的定义文件共发现{}条错误", parser.getDefinitionPaths(), errors.size());

        int i = 0;
        for (String error : errors) {
            logger.error("{}{}", error, ++i == errors.size() ? "\n" : "");
        }
    }

    /**
     * 执行代码生成
     *
     * @param paramsFileName 参数文件名为空时使用默认参数文件
     * @param extraParams    附加的参数会覆盖参数文件里的参数
     * @return 成功或失败，部分成功也会返回false
     */
    public static boolean generate(String paramsFileName, Properties extraParams) {
        long startTime = System.currentTimeMillis();
        Properties params = new Properties();

        if (!StringUtils.isBlank(paramsFileName)) {
            File paramsFile = new File(paramsFileName);
            try (InputStream inputStream = Files.newInputStream(paramsFile.toPath())) {
                params.load(inputStream);
                logger.info("加载生成器参数配置文件成功：{}\n", paramsFile.getCanonicalPath());
            } catch (IOException e) {
                logger.error("加载生成器参数配置文件出错", e);
                return false;
            }
        }

        if (extraParams != null) {
            params.putAll(extraParams);
        }

        Generator generator = new Generator(params);
        generator.setParser(new DefinitionParser());
        generator.generate();
        boolean success = generator.getParser().getValidatedErrors().isEmpty();

        logger.info("生成完成，耗时{}s", (System.currentTimeMillis() - startTime) / 1000D);
        return success;
    }

    public static void generate(Properties params) {
        generate("", params);
    }

    public static void generate(String paramsFile) {
        if (StringUtils.isBlank(paramsFile)) {
            paramsFile = "generator.properties";
        }
        generate(paramsFile, null);
    }


    private static Properties getExtraParams(String[] args) {
        Properties extraParams = new Properties();

        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            String param = arg.substring(2);
            String paramKey = param;
            String paramValue = "true";
            if (param.contains("=")) {
                paramKey = param.substring(0, param.indexOf("="));
                paramValue = param.substring(param.indexOf("=") + 1);
            }
            extraParams.put(paramKey, paramValue);
        }

        return extraParams;
    }

    public static void main(String[] args) {
        String paramsFile = "";
        if (args.length > 0 && !args[0].startsWith("--")) {
            paramsFile = args[0];
        }

        boolean exit1OnFail = false;
        if (args.length > 1 && !args[1].startsWith("--")) {
            exit1OnFail = Boolean.parseBoolean(args[1]);
        }

        Properties extraParams = getExtraParams(args);

        if (!generate(paramsFile, extraParams) && exit1OnFail) {
            System.exit(1);
        }
    }

}
