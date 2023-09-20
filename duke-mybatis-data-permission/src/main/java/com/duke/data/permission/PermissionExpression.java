package com.duke.data.permission;

public class PermissionExpression {
    private String tableName;
    private String expression;
    private String label;
    private String group;
    private ParsedExpression parsedExpression;

    public PermissionExpression(String tableName, String expression, String label, String group) {
        this.tableName = tableName;
        this.expression = expression;
        this.label = label;
        this.group = group;
    }

    public PermissionExpression() {
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public ParsedExpression getParsedExpression() {
        return parsedExpression;
    }

    public void setParsedExpression(ParsedExpression parsedExpression) {
        this.parsedExpression = parsedExpression;
    }
}

class ParsedExpression {
    private String prefix;
    private String name;
    private String expression;

    public ParsedExpression(String prefix, String name) {
        this.prefix = prefix;
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}