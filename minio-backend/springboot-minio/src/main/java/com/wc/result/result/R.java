package com.wc.result.result;

/*
* controller的返回结果统一使用该类进行包装
*
* */
public class R {

    private int code;

    private String msg;

    private Object data;

    public R(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static R OK(){
        return new R(200,"成功",null);
    }

    public static R OK(Object data){
        return new R(200,"成功",data);
    }

    public static R FAIL(){
        return new R(500,"失败",null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
