package com.duke.mybatis.page;

public class PageRequest {
    private boolean counting;
    private int page;
    private int size;

    public static PageRequest page(Integer page, Integer size, boolean count) {
        if (page == null || page < 1) {
            page = 1;
        }

        if (size == null || size < 1) {
            size = 10;
        }
        return builder().page(page).size(size).count(count).build();
    }

    public static PageRequest page(Integer page, Integer size) {
        if (page == null || page < 1) {
            page = 1;
        }

        if (size == null || size < 1) {
            size = 10;
        }
        return builder().page(page).size(size).count(true).build();
    }

    private PageRequest() {
    }

    @Override
    public String toString() {
        return "{\npage:" + this.page + ",\n, size:" + this.size + ",\n, counting:" + this.counting + "\n}";
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public void setSize(int size) {
        this.size = size < 1 ? 10 : size;
    }

    public static PageRequestBuilder builder() {
        return new PageRequestBuilder();
    }

    public int getPage() {
        return this.page;
    }

    public int getSize() {
        return this.size;
    }

    public boolean isCounting() {
        return counting;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof PageRequest)) {
            return false;
        } else {
            PageRequest other = (PageRequest) o;
            if (!other.canEqual(this)) {
                return false;
            } else if (this.getPage() != other.getPage()) {
                return false;
            } else if (this.getSize() != other.getSize()) {
                return false;
            } else if (this.isCounting() != other.isCounting()) {
                return false;
            }
            return true;
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof com.duke.mybatis.page.Page;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = result * 59 + this.getPage();
        result = result * 59 + this.getSize();
        result = result * 59 + (this.counting ? 79 : 97);
        return result;
    }

    public static final class PageRequestBuilder {
        private int page;
        private int size;
        private boolean counting;

        private PageRequestBuilder() {
        }

        public PageRequestBuilder page(int page) {
            this.page = page;
            return this;
        }

        public PageRequestBuilder size(int size) {
            this.size = size;
            return this;
        }

        public PageRequestBuilder count(boolean counting) {
            this.counting = counting;
            return this;
        }

        public PageRequest build() {
            PageRequest request = new PageRequest();
            request.page = this.page;
            request.size = this.size;
            request.counting = this.counting;
            return request;
        }
    }
}


