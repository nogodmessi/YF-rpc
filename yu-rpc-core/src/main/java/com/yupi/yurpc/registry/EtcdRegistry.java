package com.yupi.yurpc.registry;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.json.JSONUtil;
import com.yupi.yurpc.config.RegistryConfig;
import com.yupi.yurpc.model.ServiceMetaInfo;
import io.etcd.jetcd.*;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ETCD注册中心
 */
public class EtcdRegistry implements Registry{
    private Client client;

    private KV kvClient;

    /**
     * 本机注册的节点key集合(用于维护续期)
     */
    private final Set<String> localRegisterNodeKeySet = new HashSet<>();

    /**
     * 根节点
     */
    private final static String ETCD_ROOT_PATH = "/rpc/";

    @Override
    public void init(RegistryConfig registryConfig) {
        client = Client.builder().endpoints(registryConfig.getAddress()).connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))
                .build();
        kvClient = client.getKVClient();
        heartBeat();
    }

    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 创建 Lease he KV 客户端
        Lease leaseClient = client.getLeaseClient();

        // 创建一个30秒的租约
        long leaseId = leaseClient.grant(30).get().getID();

        // 设置要存储的键值对
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from(JSONUtil.toJsonStr(serviceMetaInfo), StandardCharsets.UTF_8);

        // 将键值对与租约关联起来， 并设置过期时间
        PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
        kvClient.put(key, value, putOption).get();

        // 添加节点信息到本地缓存
        localRegisterNodeKeySet.add(registerKey);
    }

    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        kvClient.delete(ByteSequence.from( registerKey, StandardCharsets.UTF_8));
        localRegisterNodeKeySet.remove(registerKey);
    }

    /**
     * 服务发现(用于消费者)
     *
     * @param serviceKey
     * @return
     */
    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 前缀搜索，结尾一定要加"/"
        String searchPrefix = ETCD_ROOT_PATH + serviceKey + "/";

        try {
            // 前缀查询
            GetOption getOption = GetOption.builder().isPrefix(true).build();
            List<KeyValue> keyValues = kvClient.get(
                            ByteSequence.from(searchPrefix, StandardCharsets.UTF_8),
                            getOption)
                    .get()
                    .getKvs();
            // 解析服务信息
            return keyValues.stream()
                    .map(keyValue -> {
                       String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                       return JSONUtil.toBean(value, ServiceMetaInfo.class);
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("获取服务列表失败",e);
        }
    }

    /**
     * 注册中心销毁，用于项目关闭后释放资源
     */
    @Override
    public void destroy() {
        System.out.println("当前节点下线");
        // 下线节点
        //遍历本节点所有的key
        for (String key : localRegisterNodeKeySet) {
            try{
               kvClient.delete(ByteSequence.from(key,StandardCharsets.UTF_8)).get();
            }catch (Exception e){
                throw new RuntimeException(key + "节点下线失败");
            }
        }



        // 释放资源
        if(kvClient != null){
            kvClient.close();
        }
        if(client != null){
            client.close();
        }
    }

    /**
     * 使用Hutool工具类的CronUtil实现定时任务，对所有集合中的节点 重新注册操作，
     * 这是一个小trick，相当于续签了。
     */
    @Override
    public void heartBeat() {
        // 10秒续签一次
        CronUtil.schedule("*/10 * * * * * ", new Task() {
            @Override
            public void execute() {
                // 遍历本节点所有的key
                for (String key : localRegisterNodeKeySet) {
                    try {
                        List<KeyValue> keyValues = kvClient.get(ByteSequence.from(key, StandardCharsets.UTF_8))
                                .get()
                                .getKvs();
                        // 节点已过期，需要重启节点才能注册
                        if (CollUtil.isEmpty(keyValues)){
                            continue;
                        }
                        // 节点未过期，重新注册
                        KeyValue keyValue = keyValues.get(0);
                        String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                        ServiceMetaInfo serviceMetaInfo = JSONUtil.toBean(value, ServiceMetaInfo.class);
                        register(serviceMetaInfo);
                    }catch (Exception e){
                        throw  new RuntimeException(key + "续签失败", e);
                    }
                }
            }
        });

        // 支持秒级别定时任务
        CronUtil.setMatchSecond(true);
        CronUtil.start();
    }
}
