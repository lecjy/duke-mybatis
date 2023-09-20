package com.duke.mybatis.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

@MappedJdbcTypes({JdbcType.ARRAY})
public class JSONAndArrayTypeHandler<E> extends BaseTypeHandler<E[]> {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final Class<E[]> type;

    public JSONAndArrayTypeHandler(Class<E[]> type) {
        Objects.requireNonNull(type);
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E[] parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    @Override
    public E[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return this.toObject(rs.getString(columnName), type);
    }

    @Override
    public E[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return this.toObject(rs.getString(columnIndex), type);
    }

    @Override
    public E[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return this.toObject(cs.getString(columnIndex), type);
    }

    private String toJson(E[] params) {
        try {
            return mapper.writeValueAsString(params);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "[]";
    }

    private E[] toObject(String content, Class<E[]> clazz) {
        if (content != null && !content.isEmpty()) {
            try {
                return mapper.readValue(content, clazz);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }
}