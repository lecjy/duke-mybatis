package com.duke.mybatis.page;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;

public class PageObjectFactory extends DefaultObjectFactory {

    private static final long serialVersionUID = 3963031299778136554L;

    private final PageRequestHolder pageRequestHolder;

    public PageObjectFactory(PageRequestHolder pageRequestHolder) {
        this.pageRequestHolder = pageRequestHolder;
    }

    @Override
    public <T> boolean isCollection(Class<T> type) {
        if (type == Page.class) {
            return true;
        }
        return super.isCollection(type);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T create(Class<T> type) {
        if (type == Page.class) {
            return (T) pageRequestHolder.page();
        }
        return create(type, null, null);
    }
}