package com.duke.mybatis.page;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;

import java.util.List;

public class PageObjectWrapper implements ObjectWrapper {

    @SuppressWarnings("rawtypes")
    private Page page;

    @SuppressWarnings("rawtypes")
    public PageObjectWrapper(Page page) {
        this.page = page;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <E> void addAll(List<E> list) {
        page.setData(list);
    }

    @Override
    public Object get(PropertyTokenizer prop) {
        return null;
    }

    @Override
    public void set(PropertyTokenizer prop, Object value) {
    }

    @Override
    public String findProperty(String name, boolean useCamelCaseMapping) {
        return null;
    }

    @Override
    public String[] getGetterNames() {
        return new String[0];
    }

    @Override
    public String[] getSetterNames() {
        return new String[0];
    }

    @Override
    public Class<?> getSetterType(String name) {
        return null;
    }

    @Override
    public Class<?> getGetterType(String name) {
        return null;
    }

    @Override
    public boolean hasSetter(String name) {
        return false;
    }

    @Override
    public boolean hasGetter(String name) {
        return false;
    }

    @Override
    public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
        return null;
    }

    @Override
    public boolean isCollection() {
        return false;
    }

    @Override
    public void add(Object element) {
    }
}
