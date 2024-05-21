package com.yupi.yurpc.fault.tolerant;

import com.yupi.yurpc.model.RpcResponse;

import java.util.Map;

/**
 * @author 杨帆
 *
 * 降级到其他服务 - 容错服务
 */
public class FailBackTolerantStrategy implements TolerantStrategy{

    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        // todo 可自行扩展，获取降级的服务并调用
        return null;
    }
}
