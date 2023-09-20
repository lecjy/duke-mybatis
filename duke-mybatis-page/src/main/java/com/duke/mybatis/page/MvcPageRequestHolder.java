package com.duke.mybatis.page;

public class MvcPageRequestHolder implements PageRequestHolder {

    private static final ThreadLocal<Page> localPage = new ThreadLocal<>();

    @Override
    public <E> void page(Page<E> page) {
        localPage.set(page);
    }

    @Override
    public <E> Page<E> page() {
        return localPage.get();
    }

    @Override
    public void remove() {
        localPage.remove();
    }

//    @Override
//    public PageRequest pageRequest() {
//        if (RequestContextHolder.getRequestAttributes() != null && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
//            HttpServletRequest httpRequest = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
//            String page = httpRequest.getParameter("page.page");
//            String size = httpRequest.getParameter("page.size");
//            String counting = httpRequest.getParameter("page.counting");
//            return parse(page, size, counting);
//        }
//        return null;
//    }
}
