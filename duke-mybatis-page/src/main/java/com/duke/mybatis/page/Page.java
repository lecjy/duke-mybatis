package com.duke.mybatis.page;

import java.util.List;

public class Page<E> {
    private long totalRecord;
    private List<E> data;

    public static <E> PageBuilder<E> builder() {
        return new PageBuilder();
    }

    public long getTotalRecord() {
        return this.totalRecord;
    }

    public List<E> getData() {
        return this.data;
    }

    public void setTotalRecord(long totalRecord) {
        this.totalRecord = totalRecord;
    }

    public void setData(List<E> data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof Page)) {
            return false;
        } else {
            Page<?> other = (Page) o;
            if (!other.canEqual(this)) {
                return false;
            } else if (this.getTotalRecord() != other.getTotalRecord()) {
                return false;
            } else {
                Object this$data = this.getData();
                Object other$data = other.getData();
                if (this$data == null) {
                    if (other$data != null) {
                        return false;
                    }
                } else if (!this$data.equals(other$data)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof Page;
    }

    @Override
    public int hashCode() {
        boolean PRIME = true;
        int result = 1;
        long $totalRecord = this.getTotalRecord();
        result = result * 59 + (int) ($totalRecord >>> 32 ^ $totalRecord);
        Object $data = this.getData();
        result = result * 59 + ($data == null ? 43 : $data.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "Page(totalRecord=" + this.totalRecord + ", data=" + this.data + ")";
    }

    public static final class PageBuilder<E> {
        private long totalRecord;
        private long totalPage;
        private List<E> data;

        private PageBuilder() {
        }

        public PageBuilder<E> totalRecord(long totalRecord) {
            this.totalRecord = totalRecord;
            return this;
        }

        public PageBuilder<E> totalPage(long totalPage) {
            this.totalPage = totalPage;
            return this;
        }

        public PageBuilder<E> data(List<E> data) {
            this.data = data;
            return this;
        }

        public Page<E> build() {
            Page<E> page = new Page();
            page.totalRecord = this.totalRecord;
            page.data = this.data;
            return page;
        }
    }
}
