package quan.data.definition;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quan.data.util.FileUtils;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 【定义】解析器
 */
public class DefinitionParser {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    //包名前缀
    protected String packagePrefix;

    protected String enumPackagePrefix;

    protected String definitionFileEncoding = Charset.defaultCharset().name();

    protected LinkedHashSet<String> definitionPaths = new LinkedHashSet<>();

    protected LinkedHashSet<File> definitionFiles = new LinkedHashSet<>();

    //定义文件的相对路径名
    protected Map<File, String> definitionFilePaths = new HashMap<>();

    private Pattern enumNamePattern;

    private Pattern beanNamePattern;

    private Pattern dataNamePattern;

    //解析出来的类定义，还未校验类名
    protected List<ClassDefinition> parsedClasses = new ArrayList<>();

    //key:长类名
    private Map<String, ClassDefinition> longName2Classes = new HashMap<>();

    //key:短类名
    private Map<String, Set<ClassDefinition>> shortName2Classes = new HashMap<>();

    //校验出的错误信息
    private LinkedHashSet<String> validatedErrors = new LinkedHashSet<>();

    public void setDefinitionFileEncoding(String definitionFileEncoding) {
        if (!StringUtils.isBlank(definitionFileEncoding)) {
            this.definitionFileEncoding = definitionFileEncoding;
        }
    }

    public void setDefinitionPaths(Collection<String> definitionPaths) {
        for (String path : definitionPaths) {
            this.definitionPaths.add(path);
            Path definitionPath = Paths.get(path);

            Set<File> definitionFiles = FileUtils.listFiles(new File(path));
            this.definitionFiles.addAll(definitionFiles);
            for (File definitionFile : definitionFiles) {
                Path relativizedPath = definitionPath.relativize(Paths.get(definitionFile.getPath()));
                definitionFilePaths.put(definitionFile, relativizedPath.toString());
            }
        }
    }

    public void setDefinitionPath(String definitionPath) {
        setDefinitionPaths(Collections.singletonList(definitionPath));
    }

    public void setPackagePrefix(String packagePrefix) {
        if (!StringUtils.isBlank(packagePrefix)) {
            this.packagePrefix = packagePrefix;
        }
    }

    public void setEnumPackagePrefix(String enumPackagePrefix) {
        if (!StringUtils.isBlank(enumPackagePrefix)) {
            this.enumPackagePrefix = enumPackagePrefix;
        }
    }

    public LinkedHashSet<String> getDefinitionPaths() {
        return definitionPaths;
    }

    public String getPackagePrefix() {
        return packagePrefix;
    }

    public String getEnumPackagePrefix() {
        return enumPackagePrefix;
    }


    public Pattern getEnumNamePattern() {
        return enumNamePattern;
    }

    public Pattern getBeanNamePattern() {
        return beanNamePattern;
    }

    public Pattern getDataNamePattern() {
        return dataNamePattern;
    }

    public void setEnumNamePattern(String enumNamePattern) {
        if (!StringUtils.isBlank(enumNamePattern)) {
            this.enumNamePattern = Pattern.compile(enumNamePattern);
        }
    }

    public void setBeanNamePattern(String beanNamePattern) {
        if (!StringUtils.isBlank(beanNamePattern)) {
            this.beanNamePattern = Pattern.compile(beanNamePattern);
        }
    }

    public void setDataNamePattern(String dataNamePattern) {
        if (!StringUtils.isBlank(dataNamePattern)) {
            this.dataNamePattern = Pattern.compile(dataNamePattern);
        }
    }

    /**
     * 获取所有的类定义
     */
    public Collection<ClassDefinition> getClasses() {
        return longName2Classes.values();
    }

    /**
     * 通过长类名获取类定义
     */
    public ClassDefinition getClass(String longName) {
        return longName2Classes.get(longName);
    }

    /**
     * 通过短类名获取类定义
     */
    public Set<ClassDefinition> getClasses(String shortName) {
        return shortName2Classes.get(shortName);
    }


    public ClassDefinition getClass(ClassDefinition owner, String name) {
        ClassDefinition classDefinition = getClass(ClassDefinition.getLongName(owner, name));
        if (classDefinition == null) {
            classDefinition = getClass(name);
        }
        return classDefinition;
    }

    public BeanDefinition getBean(String name) {
        ClassDefinition classDefinition = longName2Classes.get(name);
        if (classDefinition instanceof BeanDefinition) {
            return (BeanDefinition) classDefinition;
        }
        return null;
    }

    public BeanDefinition getBean(ClassDefinition owner, String name) {
        BeanDefinition beanDefinition = getBean(ClassDefinition.getLongName(owner, name));
        if (beanDefinition == null) {
            beanDefinition = getBean(name);
        }
        return beanDefinition;
    }

    public void addValidatedError(String error) {
        validatedErrors.add(error);
    }

    public LinkedHashSet<String> getValidatedErrors() {
        return validatedErrors;
    }

    public void parse() {
        if (!longName2Classes.isEmpty()) {
            return;
        }

        for (File definitionFile : definitionFiles) {
            if (checkFile(definitionFile)) {
                try {
                    parseFile(definitionFile);
                } catch (Exception e) {
                    logger.error("定义文件[{}]解析出错", definitionFile, e);
                    addValidatedError(String.format("定义文件[%s]解析出错：%s", definitionFile, e.getMessage()));
                }
            }
        }

        validate();
    }

    protected boolean checkFile(File definitionFile) {
        return definitionFile.getName().endsWith(".xml");
    }


    protected void parseFile(File definitionFile) {
        Element rootElement;
        try (InputStreamReader definitionReader = new InputStreamReader(Files.newInputStream(definitionFile.toPath()), definitionFileEncoding)) {
            rootElement = new SAXReader().read(definitionReader).getRootElement();
            if (rootElement == null || !rootElement.getName().equals("package")) {
                return;
            }
        } catch (Exception e) {
            String error;
            try {
                error = String.format("解析定义文件[%s]出错", definitionFile.getCanonicalPath());
            } catch (Exception ex) {
                error = String.format("解析定义文件[%s]出错", definitionFile);
            }
            addValidatedError(error);
            logger.error(error, e);
            return;
        }

        String definitionFilePath = this.definitionFilePaths.get(definitionFile);
        validateElementAttributes(definitionFilePath, rootElement);

        String packageName = null;
        if (rootElement.getName().equals("package")) {
            //以定义文件名作为包名
            packageName = definitionFile.getName().substring(0, definitionFile.getName().lastIndexOf("."));
            if (!Constants.LOWER_PACKAGE_NAME_PATTERN.matcher(packageName).matches()) {
                addValidatedError("定义文件[" + definitionFilePath + "]的文件名格式错误");
            }
        }

        for (int index = 0; index < rootElement.nodeCount(); index++) {
            if (!(rootElement.node(index) instanceof Element)) {
                continue;
            }

            Element classElement = (Element) rootElement.node(index);

            ClassDefinition classDefinition = parseClassDefinition(definitionFilePath, classElement, index);
            if (classDefinition == null) {
                continue;
            }

            parsedClasses.add(classDefinition);

            if (packageName != null) {
                classDefinition.setPackageName(packageName);
            }

            parseClassChildren(classDefinition, classElement);
        }
    }

    protected void validate() {
        validateClassName();
        parsedClasses.forEach(ClassDefinition::validate1);
        parsedClasses.forEach(ClassDefinition::validate2);
        parsedClasses.forEach(ClassDefinition::validate3);
    }

    protected void validateClassName() {
        Map<String, ClassDefinition> dissimilarNameClasses = new HashMap<>();

        for (ClassDefinition classDefinition1 : parsedClasses) {
            if (classDefinition1.getName() == null) {
                continue;
            }

            if (shortName2Classes.containsKey(classDefinition1.getName()) && !classDefinition1.isAllowSameName()) {
                for (ClassDefinition classDefinition2 : shortName2Classes.get(classDefinition1.getName())) {
                    validatedErrors.add(classDefinition1.getValidatedName("和") + classDefinition2.getValidatedName() + "名字相同");
                }
            }
            shortName2Classes.computeIfAbsent(classDefinition1.getName(), k -> new HashSet<>()).add(classDefinition1);

            ClassDefinition classDefinition3 = longName2Classes.get(classDefinition1.getLongName());
            if (classDefinition3 == null) {
                longName2Classes.put(classDefinition1.getLongName(), classDefinition1);
            } else {
                validatedErrors.add(classDefinition1.getValidatedName("和") + classDefinition3.getValidatedName() + "名字相同");
            }

            ClassDefinition classDefinition4 = dissimilarNameClasses.get(classDefinition1.getLongName().toLowerCase());
            if (classDefinition4 == null) {
                dissimilarNameClasses.put(classDefinition1.getLongName().toLowerCase(), classDefinition1);
            } else if (!classDefinition1.getLongName().equals(classDefinition4.getLongName())) {
                validatedErrors.add(classDefinition1.getValidatedName("和") + classDefinition4.getValidatedName() + "名字相似");
            }
        }
    }

    public void clear() {
        definitionFilePaths.clear();
        parsedClasses.clear();
        longName2Classes.clear();
        validatedErrors.clear();
    }


    private void validateElementAttributes(String definitionFile, Element element, Collection<Object> legalAttributes) {
        List<String> illegalAttributes = new ArrayList<>();

        outer:
        for (int i = 0; i < element.attributeCount(); i++) {
            String attrName = element.attribute(i).getName();
            if (legalAttributes != null) {
                for (Object legalAttribute : legalAttributes) {
                    if (legalAttribute instanceof Pattern && ((Pattern) legalAttribute).matcher(attrName).matches()
                            || legalAttribute instanceof String && attrName.equals(legalAttribute)) {
                        continue outer;
                    }
                }
            }
            illegalAttributes.add(attrName);
        }

        if (!illegalAttributes.isEmpty()) {
            addValidatedError(String.format("定义文件[%s]的元素[%s]不支持属性%s", definitionFile, element.getUniquePath().substring(1), illegalAttributes));
        }
    }

    private void validateElementAttributes(String definitionFile, Element element, Object... legalAttributes) {
        validateElementAttributes(definitionFile, element, Arrays.asList(legalAttributes));
    }

    /**
     * 提取注释
     */
    protected String getComment(Element element, int indexInParent) {
        if (!element.isRootElement() && element.getParent().isRootElement()) {
            List<String> list = new ArrayList<>();
            for (int i = indexInParent - 1; i >= 0; i--) {
                Node node = element.getParent().node(i);
                if (node instanceof Element) {
                    break;
                } else {
                    list.add(node.getText());
                }
            }

            StringBuilder builder = new StringBuilder();

            for (int i = list.size() - 1; i >= 0; i--) {
                builder.append(list.get(i).replaceAll("[\t ]", ""));
            }

            if (StringUtils.isBlank(builder)) {
                return null;
            }

            int start = builder.lastIndexOf("\n\n") + 2;
            if (start < 2) {
                start = 0;
            }

            String comment = builder.substring(start);
            if (StringUtils.isBlank(comment)) {
                return null;
            }

            if (comment.endsWith("\n")) {
                comment = comment.substring(0, comment.length() - 1);
            }
            comment = comment.replace("\n", "，");

            return comment;
        }

        Node commentNode = null;

        if (element.nodeCount() > 0) {
            commentNode = element.node(0);
        } else if (element.getParent().nodeCount() > indexInParent + 1) {
            commentNode = element.getParent().node(indexInParent + 1);
        }

        if (commentNode instanceof Text) {
            String text = commentNode.getText();
            if (!text.startsWith("\n")) {
                return text.trim().split("\n")[0];
            }
        }

        return null;
    }

    private ClassDefinition parseClassDefinition(String definitionFile, Element element, int indexInParent) {
        ClassDefinition classDefinition = createClassDefinition(definitionFile, element);

        if (classDefinition != null) {
            classDefinition.setParser(this);
            classDefinition.setName(element.attributeValue("name"));
            classDefinition.setComment(getComment(element, indexInParent));
            classDefinition.setDefinitionFile(definitionFile);
            classDefinition.setVersion(element.asXML().trim());
        }

        return classDefinition;
    }

    protected ClassDefinition createClassDefinition(String definitionFile, Element element) {
        switch (element.getName()) {
            case "enum":
                validateElementAttributes(definitionFile, element, "name");
                return new EnumDefinition();
            case "bean":
                validateElementAttributes(definitionFile, element, "name");
                return new BeanDefinition();
            case "data":
                validateElementAttributes(definitionFile, element, "name", "id");
                return new DataDefinition(element.attributeValue("id"));
            default:
                addValidatedError("定义文件[" + definitionFile + "]不支持定义元素:" + element.getName());
                return null;
        }
    }

    protected void parseClassChildren(ClassDefinition classDefinition, Element classElement) {
        for (int index = 0; index < classElement.nodeCount(); index++) {
            if (!(classElement.node(index) instanceof Element)) {
                continue;
            }

            Element childElement = (Element) classElement.node(index);
            String childName = childElement.getName();

            if (childName.equals("field") && parseField(classDefinition, childElement, index) != null) {
                continue;
            }

            if (classDefinition instanceof DataDefinition && childName.equals("index")) {
                DataDefinition dataDefinition = (DataDefinition) classDefinition;
                dataDefinition.addIndex(parseIndex(classDefinition, childElement, index));
                continue;
            }

            addValidatedError("定义文件[" + classDefinition.getDefinitionFile() + "]中的元素[" + classElement.getName() + "]不支持定义子元素:" + childName);
        }
    }

    protected FieldDefinition parseField(ClassDefinition classDefinition, Element fieldElement, int indexInParent) {
        FieldDefinition fieldDefinition = new FieldDefinition();
        fieldDefinition.setParser(classDefinition.getParser());
        classDefinition.addField(fieldDefinition);

        fieldDefinition.setName(fieldElement.attributeValue("name"));
        String typeInfo = fieldElement.attributeValue("type");
        fieldDefinition.setTypeInfo(typeInfo);
        fieldDefinition.setMin(fieldElement.attributeValue("min"));
        fieldDefinition.setMax(fieldElement.attributeValue("max"));
        fieldDefinition.setEnumValue(fieldElement.attributeValue("value"));

        fieldDefinition.setIgnore(fieldElement.attributeValue("ignore"));
        fieldDefinition.setIndex(fieldElement.attributeValue("index"));
        fieldDefinition.setComment(getComment(fieldElement, indexInParent));

        String type = typeInfo == null ? null : typeInfo.split("[:：]")[0];

        List<Object> legalAttributes = new ArrayList<>(Collections.singleton("name"));

        if (classDefinition instanceof BeanDefinition && type != null && Constants.NUMBER_TYPES.contains(type)) {
            legalAttributes.addAll(Arrays.asList("min", "max"));
        }

        if (classDefinition instanceof EnumDefinition) {
            legalAttributes.add("value");
        } else {
            legalAttributes.addAll(Arrays.asList("type", "ignore"));
        }

        validateElementAttributes(classDefinition.getDefinitionFile(), fieldElement, legalAttributes);

        return fieldDefinition;
    }

    protected IndexDefinition parseIndex(ClassDefinition classDefinition, Element indexElement, int indexInParent) {
        validateElementAttributes(classDefinition.getDefinitionFile(), indexElement, "name", "type", "fields");

        IndexDefinition indexDefinition = new IndexDefinition();
        indexDefinition.setParser(this);

        indexDefinition.setName(indexElement.attributeValue("name"));
        indexDefinition.setType(indexElement.attributeValue("type"));
        indexDefinition.setFieldNames(indexElement.attributeValue("fields"));

        if (indexInParent >= 0) {
            indexDefinition.setComment(getComment(indexElement, indexInParent));
        }

        return indexDefinition;
    }

}
