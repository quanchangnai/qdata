package quan.data.definition;

import org.apache.commons.lang3.StringUtils;
import quan.data.definition.DependentSource.DependentType;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bean定义
 */
public class BeanDefinition extends ClassDefinition {

    public BeanDefinition() {
    }

    @Override
    public int getKind() {
        return KIND_BEAN;
    }


    @Override
    public String getKindName() {
        return super.getKindName();
    }

    @Override
    public Pattern getNamePattern() {
        Pattern namePattern = parser.getBeanNamePattern();
        if (namePattern == null) {
            namePattern = super.getNamePattern();
        }
        return namePattern;
    }

    @Override
    public void validate2() {
        for (FieldDefinition field : fields) {
            validateFieldNameDuplicate(field);
        }
    }

    @Override
    protected void validateDependents() {
        super.validateDependents();
        for (FieldDefinition fieldDefinition : getFields()) {
            addDependent(DependentType.FIELD, this, fieldDefinition, fieldDefinition.getEnum());
            addDependent(DependentType.FIELD, this, fieldDefinition, fieldDefinition.getTypeBean());
            addDependent(DependentType.FIELD_VALUE, this, fieldDefinition, fieldDefinition.getValueTypeBean());
        }
    }


    @Override
    protected void validateField(FieldDefinition field) {
        super.validateField(field);

        validateFieldType(field);
        validateFieldRange(field);
        validateFieldBeanCycle(field);
    }

    /**
     * 校验字段类型
     */
    protected void validateFieldType(FieldDefinition field) {
        if (field.getTypeInfo() == null) {
            addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型不能为空");
            return;
        }

        String[] fieldTypes = field.getTypeInfo().split("[:：]", -1);
        String fieldType = fieldTypes[0];

        if (fieldTypes.length == 1 && StringUtils.isBlank(fieldType)) {
            addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型不能为空");
            return;
        }

        field.setType(fieldType);
        if (!field.isLegalType()) {
            addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型[" + fieldType + "]不合法");
            field.setType(null);
            return;
        }

        if (fieldTypes.length != 1 && !field.isCollectionType()) {
            addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型[" + field.getTypeInfo() + "]格式错误");
            return;
        }

        if (fieldType.equals("list") || fieldType.equals("set")) {
            if (fieldTypes.length == 2 && !StringUtils.isBlank(fieldTypes[1])) {
                field.setValueType(fieldTypes[1]);
                if (!field.isLegalValueType()) {
                    addValidatedError(getValidatedName("的[") + field.getType() + "]类型" + field.getValidatedName() + "的值类型[" + field.getValueType() + "]不合法");
                }
            } else {
                addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型[" + field.getTypeInfo() + "]格式错误，合法格式[" + fieldType + ":值类型]");
            }
        }

        if (fieldType.equals("map")) {
            if (fieldTypes.length == 3 && !StringUtils.isBlank(fieldTypes[1]) && !StringUtils.isBlank(fieldTypes[2])) {
                field.setKeyType(fieldTypes[1]);
                field.setValueType(fieldTypes[2]);
                if (!field.isPrimitiveKeyType()) {
                    addValidatedError(getValidatedName("的[") + field.getType() + "]类型" + field.getValidatedName() + "的键类型[" + field.getKeyType() + "]不合法");
                }
                if (!field.isLegalValueType()) {
                    addValidatedError(getValidatedName("的[") + field.getType() + "]类型" + field.getValidatedName() + "的值类型[" + field.getValueType() + "]不合法");
                }
            } else {
                addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型[" + field.getTypeInfo() + "]格式错误，合法格式[" + fieldType + ":键类型:值类型]");
            }
        }
    }

    /**
     * 校验字段数值范围限制
     */
    protected void validateFieldRange(FieldDefinition field) {
        Object min = field.getMin();
        Number minValue = null;

        if (min instanceof String) {
            if (field.isNumberType()) {
                try {
                    minValue = Long.parseLong((String) min);
                    field.setMin(minValue);
                } catch (NumberFormatException e1) {
                    try {
                        minValue = Double.parseDouble((String) min);
                        field.setMin(minValue);
                    } catch (NumberFormatException e2) {
                        addValidatedError(getValidatedName("的") + field.getValidatedName() + "最小值限制[" + min + "]不能为非数字");
                    }
                }
            } else {
                addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型[" + field.getType() + "]不支持最小值限制");
            }
        }

        Object max = field.getMax();
        Number maxValue = null;

        if (max instanceof String) {
            if (field.isNumberType()) {
                try {
                    maxValue = Long.parseLong((String) max);
                    field.setMax(maxValue);
                } catch (NumberFormatException e1) {
                    try {
                        maxValue = Double.parseDouble((String) max);
                        field.setMax(maxValue);
                    } catch (NumberFormatException e2) {
                        addValidatedError(getValidatedName("的") + field.getValidatedName() + "最大值限制[" + min + "]不能为非数字");
                    }
                }
            } else {
                addValidatedError(getValidatedName("的") + field.getValidatedName() + "类型[" + field.getType() + "]不支持最大值限制");
            }
        }

        if (minValue != null && maxValue != null && Double.compare(minValue.doubleValue(), maxValue.doubleValue()) > 0) {
            addValidatedError(getValidatedName("的") + field.getValidatedName() + "最小值限制[" + minValue + "]不能大于最大值限制[" + maxValue + "]");
        }
    }

    /**
     * 校验字段循环依赖，字段类型为bean类型或者集合类型字段的值类型为bean
     */
    protected void validateFieldBeanCycle(FieldDefinition field) {
        Set<BeanDefinition> fieldBeans = new HashSet<>();
        fieldBeans.add(this);
        boolean cycle = validateFieldBeanCycle(field, field, fieldBeans);
        field.setCycle(cycle);
    }

    protected boolean validateFieldBeanCycle(FieldDefinition rootField, FieldDefinition field, Set<BeanDefinition> fieldBeans) {
        BeanDefinition fieldBean = null;
        if (field.isBeanType()) {
            fieldBean = field.getTypeBean();
        } else if (field.isCollectionType()) {
            fieldBean = field.getValueTypeBean();
        }

        if (fieldBean == null) {
            return false;
        }

        if (fieldBeans.contains(fieldBean)) {
            addValidatedError(getValidatedName("的") + rootField.getValidatedName() + "循环依赖类型[" + fieldBean.getName() + "]");
            return true;
        }

        fieldBeans.add(fieldBean);

        for (FieldDefinition fieldBeanField : fieldBean.getFields()) {
            Set<BeanDefinition> fieldBeanFieldBeans = new HashSet<>(fieldBeans);
            if (validateFieldBeanCycle(rootField, fieldBeanField, fieldBeanFieldBeans)) {
                return true;
            }
        }

        return false;
    }

}
