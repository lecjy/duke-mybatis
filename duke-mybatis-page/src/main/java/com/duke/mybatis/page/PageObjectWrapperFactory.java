package com.duke.mybatis.page;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;

public class PageObjectWrapperFactory extends DefaultObjectWrapperFactory {

    private PageRequestHolder pageRequestHolder;

    public PageObjectWrapperFactory(PageRequestHolder pageRequestHolder) {
        this.pageRequestHolder = pageRequestHolder;
    }

    @Override
    public boolean hasWrapperFor(Object object) {
        if (object instanceof Page) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
        if (object instanceof Page) {
            Page page = pageRequestHolder.page();
            pageRequestHolder.remove();
            return new PageObjectWrapper(page);
        }
        throw new ReflectionException("The DefaultObjectWrapperFactory should never be called to provide an ObjectWrapper.");
    }
}
