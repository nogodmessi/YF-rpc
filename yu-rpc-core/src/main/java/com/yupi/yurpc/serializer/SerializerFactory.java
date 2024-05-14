package com.yupi.yurpc.serializer;

import com.yupi.yurpc.spi.SpiLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * @author 杨帆
 */
public class SerializerFactory {

   static {
       SpiLoader.load(Serializer.class);
   }

    /**
     * 默认序列化器
     *
     */
    private final static Serializer DEFAULT_SERIALIZER = new JdkSerializer();

    /**
     * 获取实例
     *
     * @param key
     * @return
     */
    public static Serializer getInstance(String key){
        return SpiLoader.getInstance(Serializer.class, key);
    }
}
